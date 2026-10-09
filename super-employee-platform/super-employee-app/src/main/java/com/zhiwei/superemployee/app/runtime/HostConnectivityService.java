package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.resource.CredentialSecretService;
import com.zhiwei.superemployee.app.resource.ManagedCredential;
import com.zhiwei.superemployee.app.resource.ManagedHost;
import com.zhiwei.superemployee.app.resource.ResourceRepository;
import com.zhiwei.superemployee.app.ssh.SshAuthentication;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.Buffer;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.FingerprintVerifier;
import net.schmizz.sshj.transport.verification.HostKeyVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 配置主机时校验 SSH 连通性及白名单日志路径可读性；支持自动拉取主机密钥指纹。
 * @author yangdb
 */
@Service
public class HostConnectivityService {
    private final ResourceRepository resources;
    private final CredentialSecretService secrets;
    private final int connectTimeoutMs;
    private final int commandTimeoutMs;
    private final Map<String, String> envFingerprints;

    /**
     * 注入资源仓储、凭据解密服务及 SSH 超时与环境指纹配置。
     *
     * @param resources              资源中心仓储（主机/凭据）
     * @param secrets                凭据密文解密服务
     * @param connectTimeoutSeconds  SSH 连接超时秒数，最小 1
     * @param commandTimeoutSeconds  远程命令超时秒数，最小 1
     * @param hostKeyFingerprints    环境级主机指纹映射，格式 {@code hostId=SHA256:...,hostId2=...}
     */
    public HostConnectivityService(ResourceRepository resources,
                                   CredentialSecretService secrets,
                                   @Value("${super-employee.ssh.connect-timeout-seconds:10}") long connectTimeoutSeconds,
                                   @Value("${super-employee.ssh.command-timeout-seconds:60}") long commandTimeoutSeconds,
                                   @Value("${super-employee.ssh.host-key-fingerprints:}") String hostKeyFingerprints) {
        this.resources = resources;
        this.secrets = secrets;
        this.connectTimeoutMs = (int) Math.min(Integer.MAX_VALUE, Math.max(1, connectTimeoutSeconds) * 1000L);
        this.commandTimeoutMs = (int) Math.min(Integer.MAX_VALUE, Math.max(1, commandTimeoutSeconds) * 1000L);
        this.envFingerprints = parseFingerprints(hostKeyFingerprints);
    }

    /**
     * 仅用 IP/端口连接目标 SSH，自动采集主机密钥 SHA256 指纹（无需凭据）。
     *
     * @param ipAddress 主机地址
     * @param sshPort   SSH 端口
     * @return 指纹列表，如 {@code SHA256:...}
     */
    public List<String> fetchFingerprints(String ipAddress, int sshPort) {
        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("请先填写主机 IP / 主机名");
        }
        int port = sshPort <= 0 ? 22 : sshPort;
        Set<String> fingerprints = new LinkedHashSet<>();
        try (SSHClient ssh = new SSHClient()) {
            ssh.setConnectTimeout(connectTimeoutMs);
            ssh.setTimeout(commandTimeoutMs);
            ssh.addHostKeyVerifier(capturingVerifier(fingerprints));
            ssh.connect(ipAddress.trim(), port);
        }
        catch (IOException ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            throw new IllegalStateException("自动获取主机指纹失败：" + message, ex);
        }
        if (fingerprints.isEmpty()) {
            throw new IllegalStateException("未从目标主机获取到任何 SSH 主机密钥指纹");
        }
        return List.copyOf(fingerprints);
    }

    /**
     * 对给定主机对象执行连通性探测（可不依赖已落库记录）；缺指纹时自动拉取。
     *
     * @param host 待探测主机（需含可用 SSH 凭据 ID）
     * @return 成功或失败的探测结果（含指纹）
     */
    public ProbeResult probe(ManagedHost host) {
        try {
            ManagedCredential credential = resources.findCredential(host.getCredentialId())
                    .filter(ManagedCredential::isEnabled)
                    .filter(value -> "SSH".equalsIgnoreCase(value.getType()))
                    .orElseThrow(() -> new IllegalStateException("目标主机未配置可用的 SSH 凭据"));
            List<String> fingerprints = resolveFingerprintList(host);
            if (fingerprints.isEmpty()) {
                fingerprints = fetchFingerprints(host.getIpAddress(), host.getSshPort());
            }
            String secret = secrets.decrypt(credential.getSecretValue());
            try (SSHClient ssh = new SSHClient()) {
                ssh.setConnectTimeout(connectTimeoutMs);
                ssh.setTimeout(commandTimeoutMs);
                for (String fingerprint : fingerprints) {
                    ssh.addHostKeyVerifier(FingerprintVerifier.getInstance(fingerprint));
                }
                ssh.connect(host.getIpAddress(), host.getSshPort());
                SshAuthentication.authenticate(ssh, credential, secret);
                exec(ssh, "true");
                for (String path : SshLogPathPolicy.parseAllowed(host.getAllowedLogPaths())) {
                    exec(ssh, SshLogPathPolicy.buildProbeCommand(path));
                }
                return ProbeResult.ok("SSH 连通性探测成功", fingerprints);
            }
        }
        catch (RuntimeException | IOException ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return ProbeResult.failed("SSH 连通性探测失败：" + message);
        }
    }

    /**
     * 探测已落库主机并写回 runtime_status / last_connectivity_at；成功时同步落库自动获取的指纹。
     *
     * @param hostId 主机主键
     * @return 探测结果
     * @throws IllegalArgumentException 主机不存在
     */
    public ProbeResult probeAndPersist(String hostId) {
        ManagedHost host = resources.findHost(hostId)
                .orElseThrow(() -> new IllegalArgumentException("主机不存在"));
        ProbeResult result = probe(host);
        String now = Instant.now().toString();
        ManagedHost updated = host.withConnectivity(
                result.success() ? "ONLINE" : "OFFLINE", now, host.isEnabled());
        if (result.success() && result.fingerprints() != null && !result.fingerprints().isEmpty()) {
            updated = updated.withHostKeyFingerprints(String.join("|", result.fingerprints()));
        }
        resources.updateHost(updated);
        return result;
    }

    /**
     * 启用前强制探测；失败抛出 IllegalStateException。成功时带回自动指纹。
     *
     * @param host 待启用主机
     * @return 标记 ONLINE 且可能已写入指纹的主机副本
     * @throws IllegalStateException 探测失败
     */
    public ManagedHost requireReachable(ManagedHost host) {
        ProbeResult result = probe(host);
        if (!result.success()) {
            throw new IllegalStateException(result.message());
        }
        ManagedHost online = host.withConnectivity("ONLINE", Instant.now().toString(), host.isEnabled());
        if (result.fingerprints() != null && !result.fingerprints().isEmpty()) {
            return online.withHostKeyFingerprints(String.join("|", result.fingerprints()));
        }
        return online;
    }

    /**
     * 解析主机可用的原始指纹字符串：优先主机落库字段，其次环境配置。
     *
     * @param host 目标主机
     * @return 单个或多个以 {@code |} 分隔的指纹；均未配置时可能为 {@code null}
     */
    public String resolveFingerprint(ManagedHost host) {
        if (host.getHostKeyFingerprint() != null && !host.getHostKeyFingerprint().isBlank()) {
            return host.getHostKeyFingerprint();
        }
        return envFingerprints.get(host.getId());
    }

    /**
     * 将主机指纹原始字符串拆分为去空白的指纹列表。
     *
     * @param host 目标主机
     * @return 指纹列表；未配置时为空列表
     */
    private List<String> resolveFingerprintList(ManagedHost host) {
        String raw = resolveFingerprint(host);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    /**
     * 构造仅采集指纹、一律放行的主机密钥验证器（用于自动拉取指纹）。
     *
     * @param fingerprints 采集结果写入此集合
     * @return SSHJ {@link HostKeyVerifier}
     */
    private HostKeyVerifier capturingVerifier(Set<String> fingerprints) {
        return new HostKeyVerifier() {
            /**
             * 计算并记录公钥 SHA256 指纹后放行连接。
             *
             * @param hostname 主机名
             * @param port     端口
             * @param key      远端公钥
             * @return 始终 {@code true}，以便完成握手并采集指纹
             */
            @Override
            public boolean verify(String hostname, int port, PublicKey key) {
                fingerprints.add(sha256Fingerprint(key));
                return true;
            }

            /**
             * 采集模式下不预置已知算法列表。
             *
             * @param hostname 主机名
             * @param port     端口
             * @return 空列表
             */
            @Override
            public List<String> findExistingAlgorithms(String hostname, int port) {
                return List.of();
            }
        };
    }

    /**
     * 生成 OpenSSH 风格 SHA256 指纹（与 {@code ssh-keygen -E sha256} 一致）。
     *
     * @param key 主机公钥
     * @return {@code SHA256:...} 指纹
     * @throws IllegalStateException 摘要算法不可用或编码失败
     */
    static String sha256Fingerprint(PublicKey key) {
        try {
            Buffer.PlainBuffer buffer = new Buffer.PlainBuffer();
            buffer.putPublicKey(key);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(buffer.getCompactData());
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest);
        }
        catch (Exception ex) {
            throw new IllegalStateException("计算主机密钥指纹失败", ex);
        }
    }

    /**
     * 在已建立的 SSH 会话上执行命令，要求退出码为 0。
     *
     * @param ssh     已认证的 SSH 客户端
     * @param command 远程命令
     * @throws IOException           会话或 IO 失败
     * @throws IllegalStateException 退出码非 0 或超时无状态
     */
    private void exec(SSHClient ssh, String command) throws IOException {
        try (Session session = ssh.startSession()) {
            Session.Command result = session.exec(command);
            result.join(commandTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            Integer status = result.getExitStatus();
            if (status == null || status != 0) {
                throw new IllegalStateException("命令失败(" + status + ")：" + command);
            }
        }
    }

    /**
     * 解析配置串 {@code hostId=fingerprint,...} 为不可变映射；重复键保留后者。
     *
     * @param raw 配置原文，可为空
     * @return 主机 ID → 指纹字符串
     */
    private Map<String, String> parseFingerprints(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(item -> item.contains("="))
                .map(item -> item.split("=", 2))
                .collect(Collectors.toUnmodifiableMap(
                        pair -> pair[0].trim(), pair -> pair[1].trim(), (left, right) -> right));
    }

    /**
     * SSH 连通性探测结果。
     *
     * @param success      是否探测成功
     * @param message      人类可读说明
     * @param fingerprints 探测过程使用或采集到的指纹列表
     */
    public record ProbeResult(boolean success, String message, List<String> fingerprints) {
        /**
         * 构造成功结果。
         *
         * @param message      成功说明
         * @param fingerprints 指纹列表，{@code null} 视为空
         * @return 成功的探测结果
         */
        public static ProbeResult ok(String message, List<String> fingerprints) {
            return new ProbeResult(true, message, fingerprints == null ? List.of() : List.copyOf(fingerprints));
        }

        /**
         * 构造失败结果（无指纹）。
         *
         * @param message 失败说明
         * @return 失败的探测结果
         */
        public static ProbeResult failed(String message) {
            return new ProbeResult(false, message, List.of());
        }
    }
}
