package com.zhiwei.superemployee.app.runtime;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import com.zhiwei.superemployee.app.resource.CredentialSecretService;
import com.zhiwei.superemployee.app.resource.ManagedCredential;
import com.zhiwei.superemployee.app.resource.ManagedHost;
import com.zhiwei.superemployee.app.resource.ResourceRepository;
import com.zhiwei.superemployee.app.ssh.SshAuthentication;
import com.zhiwei.superemployee.core.gateway.SshDiagnosticGateway;
import com.zhiwei.superemployee.core.gateway.SshDiagnosticRequest;
import com.zhiwei.superemployee.core.gateway.SshDiagnosticResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 仅执行预定义只读命令的 SSH 适配器。缺指纹时通过连通性服务自动拉取。
 * @author yangdb
 */
@Component
@ConditionalOnProperty(prefix = "super-employee.ssh", name = "enabled", havingValue = "true")
public class SshjDiagnosticGateway implements SshDiagnosticGateway {
    private final ResourceRepository resources;
    private final CredentialSecretService secrets;
    private final HostConnectivityService connectivity;
    private final int connectTimeoutMs;
    private final int commandTimeoutMs;
    private final int maxOutputChars;
    private final int maxLines;
    private final int maxSinceMinutes;
    private final Map<String, String> envFingerprints;

    /**
     * 注入资源、凭据、连通性服务及 SSH 超时与输出上限配置。
     *
     * @param resources              资源中心仓储
     * @param secrets                凭据解密服务
     * @param connectivity           主机连通性/指纹采集服务
     * @param connectTimeoutSeconds  连接超时秒数
     * @param commandTimeoutSeconds  命令超时秒数
     * @param maxOutputChars         合并输出最大字符数，至少 1024
     * @param maxLines               日志行数上限，至少 1
     * @param maxSinceMinutes        时间窗口分钟上限，至少 1
     * @param hostKeyFingerprints    环境级主机指纹映射配置
     */
    public SshjDiagnosticGateway(
            ResourceRepository resources,
            CredentialSecretService secrets,
            HostConnectivityService connectivity,
            @Value("${super-employee.ssh.connect-timeout-seconds:10}") long connectTimeoutSeconds,
            @Value("${super-employee.ssh.command-timeout-seconds:60}") long commandTimeoutSeconds,
            @Value("${super-employee.ssh.max-output-chars:128000}") int maxOutputChars,
            @Value("${super-employee.ssh.max-log-lines:200}") int maxLines,
            @Value("${super-employee.ssh.max-since-minutes:1440}") int maxSinceMinutes,
            @Value("${super-employee.ssh.host-key-fingerprints:}") String hostKeyFingerprints) {
        this.resources = resources;
        this.secrets = secrets;
        this.connectivity = connectivity;
        this.connectTimeoutMs = secondsToMillis(connectTimeoutSeconds);
        this.commandTimeoutMs = secondsToMillis(commandTimeoutSeconds);
        this.maxOutputChars = Math.max(1024, maxOutputChars);
        this.maxLines = Math.max(1, maxLines);
        this.maxSinceMinutes = Math.max(1, maxSinceMinutes);
        this.envFingerprints = parseFingerprints(hostKeyFingerprints);
    }

    /**
     * 校验目标和凭据后执行受限只读诊断命令。
     *
     * @param request 请求
     * @return 校验目标和凭据后执行受限只读诊断命令
     */
    @Override
    public SshDiagnosticResult execute(SshDiagnosticRequest request) {
        ManagedHost host = resources.findHost(request.hostId())
                .filter(ManagedHost::isEnabled)
                .orElseThrow(() -> new IllegalStateException("目标主机不存在或已停用"));
        ManagedCredential credential = resources.findCredential(host.getCredentialId())
                .filter(ManagedCredential::isEnabled)
                .filter(value -> "SSH".equalsIgnoreCase(value.getType()))
                .orElseThrow(() -> new IllegalStateException("目标主机未配置可用的 SSH 凭据"));
        String fingerprint = resolveFingerprint(host);
        if (fingerprint == null || fingerprint.isBlank()) {
            var learned = connectivity.fetchFingerprints(host.getIpAddress(), host.getSshPort());
            fingerprint = String.join("|", learned);
            resources.updateHost(host.withHostKeyFingerprints(fingerprint));
        }
        String command = command(request, host);
        String secret = secrets.decrypt(credential.getSecretValue());
        try (SSHClient ssh = new SSHClient()) {
            ssh.setConnectTimeout(connectTimeoutMs);
            ssh.setTimeout(commandTimeoutMs);
            hostFingerprints(fingerprint).forEach(ssh::addHostKeyVerifier);
            ssh.connect(host.getIpAddress(), host.getSshPort());
            SshAuthentication.authenticate(ssh, credential, secret);
            try (Session session = ssh.startSession()) {
                Session.Command result = session.exec(command);
                ExecutorService readers = Executors.newFixedThreadPool(2);
                Future<String> stdout = readers.submit(
                        () -> readBounded(result.getInputStream(), maxOutputChars));
                Future<String> stderr = readers.submit(
                        () -> readBounded(result.getErrorStream(), Math.max(1024, maxOutputChars / 4)));
                String output;
                String error;
                try {
                    result.join(commandTimeoutMs, TimeUnit.MILLISECONDS);
                    if (result.getExitStatus() == null) {
                        result.close();
                        throw new SocketTimeoutException(
                                "SSH 只读诊断命令超过 " + commandTimeoutMs + "ms");
                    }
                    output = completedOutput(stdout);
                    error = completedOutput(stderr);
                }
                finally {
                    stdout.cancel(true);
                    stderr.cancel(true);
                    readers.shutdownNow();
                }
                String merged = error.isBlank() ? output : output + System.lineSeparator() + error;
                boolean truncated = merged.length() >= maxOutputChars;
                String safeOutput = merged.length() > maxOutputChars ? merged.substring(0, maxOutputChars) : merged;
                boolean success = result.getExitStatus() != null && result.getExitStatus() == 0;
                return new SshDiagnosticResult(success,
                        success ? "SSH 只读诊断执行完成" : "SSH 只读诊断执行失败，退出码：" + result.getExitStatus(),
                        safeOutput, truncated);
            }
            finally {
                ssh.disconnect();
            }
        }
        catch (IOException ex) {
            String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (detail.contains("Timeout expired") || detail.toLowerCase().contains("timed out")) {
                throw new IllegalStateException(
                        "SSH 只读诊断超时（命令时限 " + commandTimeoutMs + "ms）：主机可连，但远程读日志未在时限内完成。"
                                + "可增大 super-employee.ssh.command-timeout-seconds，或缩小运维任务的日志时间窗口/行数。原始错误："
                                + detail,
                        ex);
            }
            throw new IllegalStateException("SSH 只读诊断连接或执行失败：" + detail, ex);
        }
    }

    /**
     * 命令结束后读取并发收集的输出，避免 stdout/stderr 相互阻塞。
     *
     * @param output 输出文本
     * @return 命令结束后读取并发收集的输出，避免 stdout/stderr 相互阻塞
     */
    private String completedOutput(Future<String> output) throws IOException {
        try {
            return output.get(1, TimeUnit.SECONDS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException("读取 SSH 输出被中断");
            interrupted.initCause(ex);
            throw interrupted;
        }
        catch (TimeoutException ex) {
            throw new SocketTimeoutException("SSH 命令已结束，但输出流未及时关闭");
        }
        catch (ExecutionException ex) {
            if (ex.getCause() instanceof IOException io) {
                throw io;
            }
            throw new IOException("读取 SSH 输出失败", ex.getCause());
        }
    }

    /** 将只读诊断请求转换为受控命令（无主机上下文；APP_LOG_FILE 不可用）。 */
    String command(SshDiagnosticRequest request) {
        return command(request, null);
    }

    /**
     * 按诊断模板生成受限只读 shell 命令；行数与时间窗口受配置上限约束。
     *
     * @param request 诊断请求
     * @param host    主机对象；{@code APP_LOG_FILE} 时必须非空以校验白名单
     * @return 可经 SSH 执行的命令字符串
     * @throws IllegalStateException    APP_LOG_FILE 缺少主机上下文
     * @throws IllegalArgumentException 服务名或参数越界
     */
    String command(SshDiagnosticRequest request, ManagedHost host) {
        int lines = bounded(request.maxLines(), maxLines);
        int since = bounded(request.sinceMinutes(), maxSinceMinutes);
        return switch (request.template()) {
            case HOST_HEALTH -> "uptime; printf '\\n--- memory ---\\n'; free -m; printf '\\n--- filesystem ---\\n'; df -hP";
            case DISK_USAGE -> "df -hP";
            case SERVICE_STATUS -> "systemctl is-active -- " + serviceName(request.serviceName())
                    + "; systemctl status --no-pager --lines=" + lines + " -- " + serviceName(request.serviceName());
            case JOURNAL_LOGS -> "journalctl --no-pager --since '" + since + " minutes ago' -n " + lines;
            case JAVA_APPLICATION_LOGS -> "journalctl --no-pager --since '" + since + " minutes ago' -n "
                    + lines + " -u " + serviceName(request.serviceName());
            case APP_LOG_FILE -> {
                if (host == null) {
                    throw new IllegalStateException("APP_LOG_FILE 需要主机白名单上下文");
                }
                yield SshLogPathPolicy.buildReadCommand(
                        request.logPath(), host.getAllowedLogPaths(), since, lines);
            }
        };
    }

    /**
     * 解析主机指纹：优先落库字段，其次环境配置映射。
     *
     * @param host 目标主机
     * @return 指纹字符串（可含 {@code |} 分隔的多个）；未配置时可能为 {@code null}
     */
    String resolveFingerprint(ManagedHost host) {
        if (host.getHostKeyFingerprint() != null && !host.getHostKeyFingerprint().isBlank()) {
            return host.getHostKeyFingerprint();
        }
        return envFingerprints.get(host.getId());
    }

    /**
     * 校验服务名称以防止命令注入。
     *
     * @param value 取值
     * @return 校验服务名称以防止命令注入
     */
    private String serviceName(String value) {
        if (value == null || !value.matches("[A-Za-z0-9@_.-]{1,128}")) {
            throw new IllegalArgumentException("服务名称不符合只读诊断模板要求");
        }
        return value;
    }

    /**
     * 应用诊断参数的默认值和上限。
     *
     * @param value 取值
     * @param upperBound upperBound
     * @return 应用诊断参数的默认值和上限
     */
    private int bounded(int value, int upperBound) {
        if (value <= 0) {
            return Math.min(100, upperBound);
        }
        if (value > upperBound) {
            throw new IllegalArgumentException("诊断参数超过允许范围");
        }
        return value;
    }

    /**
     * 读取流内容并限制最大字符数。
     *
     * @param stream stream
     * @param limit limit
     * @return 读取流内容并限制最大字符数
     */
    private String readBounded(InputStream stream, int limit) throws IOException {
        byte[] buffer = new byte[4096];
        StringBuilder value = new StringBuilder();
        int read;
        while ((read = stream.read(buffer)) >= 0 && value.length() < limit) {
            value.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
        }
        return value.length() > limit ? value.substring(0, limit) : value.toString().trim();
    }

    /**
     * 将正秒数安全转换为毫秒。
     *
     * @param seconds seconds
     * @return 将正秒数安全转换为毫秒
     */
    private int secondsToMillis(long seconds) {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE / 1000L, Math.max(1, seconds)) * 1000L);
    }

    /** 将同一主机的多个已核验指纹拆分为 SSHJ 验证器输入。 */
    List<String> hostFingerprints(String value) {
        return java.util.Arrays.stream(value.split("\\|"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    /**
     * 解析主机编号到密钥指纹的配置映射；同一主机的多个指纹以竖线分隔。
     *
     * @param raw 原始字符串
     * @return 响应数据 Map
     */
    private Map<String, String> parseFingerprints(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(item -> item.contains("="))
                .map(item -> item.split("=", 2))
                .collect(Collectors.toUnmodifiableMap(
                        pair -> pair[0].trim(), pair -> pair[1].trim(), (left, right) -> right));
    }
}
