package com.zhiwei.superemployee.app.ssh;

import com.zhiwei.superemployee.app.resource.CredentialSecretService;
import com.zhiwei.superemployee.app.resource.ManagedCredential;
import com.zhiwei.superemployee.app.resource.ManagedHost;
import com.zhiwei.superemployee.app.resource.ResourceRepository;
import com.zhiwei.superemployee.app.runtime.HostConnectivityService;
import net.schmizz.sshj.SSHClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;

/**
 * 按资源中心主机/凭据/指纹建立已认证的 {@link SSHClient}，供终端与 SFTP 复用。
 * <p>
 * 打开连接前校验主机已启用；若尚无主机密钥指纹则自动探测并落库，
 * 避免运维手工录入指纹即可安全连接。
 * </p>
 * @author yangdb
 */
@Service
public class ManagedSshConnector {
    private final ResourceRepository resources;
    private final CredentialSecretService secrets;
    private final HostConnectivityService connectivity;
    private final int connectTimeoutMs;

    /**
     * @param resources             资源中心仓储
     * @param secrets               凭据加解密服务
     * @param connectivity          主机连通性与指纹探测服务
     * @param connectTimeoutSeconds SSH 连接超时秒数，最小 1
     */
    public ManagedSshConnector(ResourceRepository resources,
                               CredentialSecretService secrets,
                               HostConnectivityService connectivity,
                               @Value("${super-employee.ssh.connect-timeout-seconds:10}") long connectTimeoutSeconds) {
        this.resources = resources;
        this.secrets = secrets;
        this.connectivity = connectivity;
        this.connectTimeoutMs = (int) Math.min(Integer.MAX_VALUE, Math.max(1, connectTimeoutSeconds) * 1000L);
    }

    /**
     * 校验主机存在且已启用。
     *
     * @param hostId 主机 ID
     * @return 已启用的主机
     * @throws IllegalArgumentException 主机不存在
     * @throws IllegalStateException    主机未启用
     */
    public ManagedHost requireEnabledHost(String hostId) {
        ManagedHost host = resources.findHost(hostId)
                .orElseThrow(() -> new IllegalArgumentException("主机不存在"));
        if (!host.isEnabled()) {
            throw new IllegalStateException("主机未启用");
        }
        return host;
    }

    /**
     * 按主机 ID 打开已认证 SSH 连接；调用方负责 {@code disconnect/close}。
     *
     * @param hostId 主机 ID
     * @return 已认证的 SSH 客户端
     * @throws IOException              网络或认证失败
     * @throws IllegalArgumentException 主机不存在
     * @throws IllegalStateException    主机未启用或凭据不可用
     */
    public SSHClient open(String hostId) throws IOException {
        return open(requireEnabledHost(hostId));
    }

    /**
     * 对已解析主机打开已认证 SSH 连接；调用方负责关闭。
     * <p>无手工指纹时自动拉取并落库；认证失败时确保断开半开连接。</p>
     *
     * @param host 已启用主机
     * @return 已认证的 SSH 客户端
     * @throws IOException           网络或认证失败
     * @throws IllegalStateException 未配置可用 SSH 凭据
     */
    public SSHClient open(ManagedHost host) throws IOException {
        ManagedCredential credential = resources.findCredential(host.getCredentialId())
                .filter(ManagedCredential::isEnabled)
                .filter(value -> "SSH".equalsIgnoreCase(value.getType()))
                .orElseThrow(() -> new IllegalStateException("目标主机未配置可用的 SSH 凭据"));
        String fingerprint = connectivity.resolveFingerprint(host);
        if (fingerprint == null || fingerprint.isBlank()) {
            // 无手工指纹时自动拉取并落库，主机只需绑定 SSH 凭据即可连接。
            var learned = connectivity.fetchFingerprints(host.getIpAddress(), host.getSshPort());
            fingerprint = String.join("|", learned);
            resources.updateHost(host.withHostKeyFingerprints(fingerprint));
        }
        String secret = secrets.decrypt(credential.getSecretValue());
        SSHClient ssh = new SSHClient();
        ssh.setConnectTimeout(connectTimeoutMs);
        ssh.setTimeout(0);
        Arrays.stream(fingerprint.split("\\|")).map(String::trim).filter(v -> !v.isBlank())
                .forEach(fp -> ssh.addHostKeyVerifier(
                        net.schmizz.sshj.transport.verification.FingerprintVerifier.getInstance(fp)));
        ssh.connect(host.getIpAddress(), host.getSshPort());
        try {
            SshAuthentication.authenticate(ssh, credential, secret);
        }
        catch (IOException | RuntimeException ex) {
            try {
                ssh.disconnect();
            }
            catch (Exception ignored) {
            }
            throw ex;
        }
        return ssh;
    }
}
