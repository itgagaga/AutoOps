package com.zhiwei.superemployee.app.ssh;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiwei.superemployee.app.audit.AuditService;
import com.zhiwei.superemployee.app.resource.ManagedHost;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 浏览器 WebSocket 与目标主机 SSH PTY 的交互式终端桥接。
 * <p>
 * 连接建立后分配伪终端并泵送远端输出；客户端通过 JSON 消息发送输入、调整窗口或心跳。
 * </p>
 * @author yangdb
 */
@Component
public class SshTerminalWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(SshTerminalWebSocketHandler.class);

    private final ManagedSshConnector connector;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final ExecutorService pumpPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ssh-terminal-pump");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Bridge> bridges = new ConcurrentHashMap<>();

    /**
     * @param connector    受管 SSH 连接器
     * @param audit        审计服务
     * @param objectMapper JSON 解析/序列化
     */
    public SshTerminalWebSocketHandler(ManagedSshConnector connector,
                                       AuditService audit,
                                       ObjectMapper objectMapper) {
        this.connector = connector;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    /**
     * WebSocket 建立后按查询参数打开 SSH PTY，并启动输出泵送线程。
     *
     * @param session 浏览器 WebSocket 会话（查询串含 hostId/cols/rows）
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String hostId = query(session, "hostId");
        Object attrUser = session.getAttributes().get("username");
        String username = attrUser == null
                ? (session.getPrincipal() == null ? null : session.getPrincipal().getName())
                : String.valueOf(attrUser);
        if (hostId == null || hostId.isBlank()) {
            closeQuietly(session, "缺少 hostId");
            return;
        }
        int cols = parseInt(query(session, "cols"), 120);
        int rows = parseInt(query(session, "rows"), 36);
        try {
            Bridge bridge = openBridge(session, hostId, cols, rows);
            bridges.put(session.getId(), bridge);
            audit.record("SSH_OPEN", "HOST", hostId, "user=" + username + ", cols=" + cols + ", rows=" + rows);
            sendJson(session, Map.of("type", "status", "message", "已连接到 " + bridge.hostLabel));
            pumpPool.execute(() -> pumpOutput(session, bridge));
        }
        catch (Exception ex) {
            log.warn("SSH terminal open failed hostId={} user={}: {}", hostId, username, ex.getMessage());
            closeQuietly(session, ex.getMessage() == null ? "SSH 连接失败" : ex.getMessage());
        }
    }

    /**
     * 处理客户端 JSON 消息：终端输入、窗口尺寸变更或 ping。
     *
     * @param session WebSocket 会话
     * @param message 文本消息载荷
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Bridge bridge = bridges.get(session.getId());
        if (bridge == null || bridge.closed.get()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = text(node, "type");
            if ("input".equals(type)) {
                String data = text(node, "data");
                if (data != null && !data.isEmpty()) {
                    bridge.stdin.write(data.getBytes(StandardCharsets.UTF_8));
                    bridge.stdin.flush();
                }
            } else if ("resize".equals(type)) {
                int cols = node.path("cols").asInt(0);
                int rows = node.path("rows").asInt(0);
                if (cols > 0 && rows > 0 && bridge.session instanceof net.schmizz.sshj.connection.channel.direct.SessionChannel channel) {
                    channel.changeWindowDimensions(cols, rows, 0, 0);
                }
            } else if ("ping".equals(type)) {
                sendJson(session, Map.of("type", "pong"));
            }
        }
        catch (Exception ex) {
            log.debug("SSH terminal message error: {}", ex.getMessage());
            closeQuietly(session, "终端通信异常：" + ex.getMessage());
        }
    }

    /**
     * WebSocket 正常关闭时释放 SSH 桥接资源。
     *
     * @param session WebSocket 会话
     * @param status  关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeBridge(session.getId(), "连接关闭");
    }

    /**
     * 传输层错误时关闭桥接并结束 WebSocket。
     *
     * @param session   WebSocket 会话
     * @param exception 传输异常
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        closeBridge(session.getId(), exception.getMessage());
        try {
            session.close(CloseStatus.SERVER_ERROR);
        }
        catch (IOException ignored) {
        }
    }

    /**
     * 打开目标主机 SSH 会话并分配 PTY Shell。
     *
     * @param ws     WebSocket 会话（当前未直接使用，保留对称签名）
     * @param hostId 主机 ID
     * @param cols   终端列数
     * @param rows   终端行数
     * @return 桥接对象
     * @throws IOException SSH 或 PTY 分配失败
     */
    private Bridge openBridge(WebSocketSession ws, String hostId, int cols, int rows) throws IOException {
        ManagedHost host = connector.requireEnabledHost(hostId);
        SSHClient ssh = connector.open(host);
        Session session = ssh.startSession();
        session.allocatePTY("xterm-256color", Math.max(40, cols), Math.max(10, rows), 0, 0, java.util.Collections.emptyMap());
        Session.Shell shell = session.startShell();
        String label = host.getName() + " (" + host.getIpAddress() + ":" + host.getSshPort() + ")";
        return new Bridge(ssh, session, shell.getInputStream(), shell.getOutputStream(), label, host.getId());
    }

    /**
     * 持续读取 SSH 标准输出并推送到浏览器。
     *
     * @param ws     WebSocket 会话
     * @param bridge SSH 桥接
     */
    private void pumpOutput(WebSocketSession ws, Bridge bridge) {
        byte[] buffer = new byte[8192];
        try {
            while (!bridge.closed.get() && ws.isOpen()) {
                int read = bridge.stdout.read(buffer);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                String chunk = new String(buffer, 0, read, StandardCharsets.UTF_8);
                sendJson(ws, Map.of("type", "output", "data", chunk));
            }
        }
        catch (Exception ex) {
            if (!bridge.closed.get()) {
                log.debug("SSH pump ended: {}", ex.getMessage());
            }
        }
        finally {
            closeQuietly(ws, "SSH 会话已结束");
        }
    }

    /**
     * 关闭并移除指定 WebSocket 对应的 SSH 桥接，写入审计。
     *
     * @param sessionId WebSocket 会话 ID
     * @param reason    关闭原因
     */
    private void closeBridge(String sessionId, String reason) {
        Bridge bridge = bridges.remove(sessionId);
        if (bridge == null) {
            return;
        }
        bridge.closed.set(true);
        try {
            bridge.stdin.close();
        }
        catch (Exception ignored) {
        }
        try {
            bridge.session.close();
        }
        catch (Exception ignored) {
        }
        try {
            bridge.ssh.disconnect();
        }
        catch (Exception ignored) {
        }
        audit.record("SSH_CLOSE", "HOST", bridge.hostId, reason == null ? "" : reason);
    }

    /**
     * 向客户端发送错误提示后关闭 WebSocket，并释放桥接。
     *
     * @param session WebSocket 会话
     * @param message 错误或关闭说明
     */
    private void closeQuietly(WebSocketSession session, String message) {
        try {
            if (session.isOpen()) {
                sendJson(session, Map.of("type", "error", "message", message == null ? "连接关闭" : message));
                session.close(CloseStatus.NORMAL);
            }
        }
        catch (Exception ignored) {
        }
        closeBridge(session.getId(), message);
    }

    /**
     * 线程安全地向 WebSocket 发送 JSON 文本消息。
     *
     * @param session WebSocket 会话
     * @param payload 消息体
     * @throws IOException 发送失败
     */
    private void sendJson(WebSocketSession session, Map<String, ?> payload) throws IOException {
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        }
    }

    /**
     * 从 WebSocket URI 查询串读取指定参数。
     *
     * @param session WebSocket 会话
     * @param key     参数名
     * @return 解码后的参数值；不存在则为 {@code null}
     */
    private static String query(WebSocketSession session, String key) {
        if (session.getUri() == null || session.getUri().getQuery() == null) {
            return null;
        }
        for (String part : session.getUri().getQuery().split("&")) {
            int idx = part.indexOf('=');
            if (idx > 0 && part.substring(0, idx).equals(key)) {
                return java.net.URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * 解析整数查询参数，失败时返回默认值。
     *
     * @param value    原始字符串
     * @param fallback 默认值
     * @return 解析结果或默认值
     */
    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        }
        catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /**
     * 读取 JSON 对象的文本字段。
     *
     * @param node  JSON 节点
     * @param field 字段名
     * @return 文本；缺失或为 null 时返回 {@code null}
     */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * 单个 WebSocket 会话对应的 SSH PTY 桥接资源。
     */
    private static final class Bridge {
        private final SSHClient ssh;
        private final Session session;
        private final InputStream stdout;
        private final OutputStream stdin;
        private final String hostLabel;
        private final String hostId;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        /**
         * @param ssh       SSH 客户端
         * @param session   SSH 会话
         * @param stdout    Shell 标准输出
         * @param stdin     Shell 标准输入
         * @param hostLabel 主机展示标签
         * @param hostId    主机 ID
         */
        private Bridge(SSHClient ssh, Session session, InputStream stdout, OutputStream stdin,
                       String hostLabel, String hostId) {
            this.ssh = ssh;
            this.session = session;
            this.stdout = stdout;
            this.stdin = stdin;
            this.hostLabel = hostLabel;
            this.hostId = hostId;
        }
    }
}
