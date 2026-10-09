package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.resource.CredentialSecretService;
import com.zhiwei.superemployee.app.resource.ManagedCredential;
import com.zhiwei.superemployee.app.resource.ManagedHost;
import com.zhiwei.superemployee.app.resource.ResourceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link HostConnectivityService} 单测：可达性前置条件（凭据、指纹自动拉取/环境回退）与日志白名单解析。
 * @author yangdb
 */
class HostConnectivityServiceTest {

    /**
     * 场景：主机未绑定凭据即要求可达。
     * 预期：requireReachable 抛出 IllegalStateException，消息含「凭据」。
     */
    @Test
    void shouldFailWhenCredentialMissing() {
        ResourceRepository resources = mock(ResourceRepository.class);
        HostConnectivityService service = new HostConnectivityService(
                resources, new CredentialSecretService("test-key-at-least-32-bytes-long!!"),
                5, 5, "");

        ManagedHost withoutCredential = host(null, "SHA256:abc", null);
        assertThatThrownBy(() -> service.requireReachable(withoutCredential))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("凭据");
    }

    /**
     * 场景：已绑定凭据但缺少主机指纹，尝试自动拉取时本机端口不可达。
     * 预期：抛出 IllegalStateException，消息匹配指纹/探测/连接失败语义，而非强制要求手工预置指纹。
     */
    @Test
    void shouldAutoFetchFingerprintWhenMissing() {
        ResourceRepository resources = mock(ResourceRepository.class);
        HostConnectivityService service = new HostConnectivityService(
                resources, new CredentialSecretService("test-key-at-least-32-bytes-long!!"),
                1, 1, "");
        when(resources.findCredential("cred-1")).thenReturn(Optional.of(
                new ManagedCredential("cred-1", "SSH1", "ssh", "SSH", "root", "cipher", null, true, "now", "now")));

        ManagedHost withoutFingerprint = host("cred-1", null, null);
        assertThatThrownBy(() -> service.requireReachable(withoutFingerprint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageMatching(".*(指纹|探测|连接|Connection|connect|refused|timed out|超时).*");
    }

    /**
     * 场景：主机字段无指纹，但环境回退配置提供 host-1 指纹。
     * 预期：resolveFingerprint 返回环境变量中的 SHA256 值。
     */
    @Test
    void shouldResolveFingerprintFromEnvFallback() {
        ResourceRepository resources = mock(ResourceRepository.class);
        HostConnectivityService service = new HostConnectivityService(
                resources, new CredentialSecretService("test-key-at-least-32-bytes-long!!"),
                5, 5, "host-1=SHA256:env-fingerprint");

        ManagedHost host = host("cred-1", null, null);
        assertThat(service.resolveFingerprint(host)).isEqualTo("SHA256:env-fingerprint");
    }

    /**
     * 场景：解析允许日志路径列表时混入路径穿越，或提供合法多路径。
     * 预期：含 {@code ..} 的条目抛 IllegalArgumentException；合法列表按 | 拆分为规范化路径。
     */
    @Test
    void shouldRejectUnsafeAllowedLogPathsBeforeConnect() {
        assertThatThrownBy(() -> SshLogPathPolicy.parseAllowed("/ok.log|/tmp/../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SshLogPathPolicy.parseAllowed("/opt/app/logs/app.log|/var/log/app/error.log"))
                .containsExactly("/opt/app/logs/app.log", "/var/log/app/error.log");
    }

    /**
     * 构造指向本机 22 端口的测试主机。
     *
     * @param credentialId 凭据 ID，允许 {@code null}
     * @param fingerprint  主机密钥指纹，允许 {@code null}
     * @param paths        允许日志路径串，允许 {@code null}
     * @return {@link ManagedHost}
     */
    private ManagedHost host(String credentialId, String fingerprint, String paths) {
        return new ManagedHost("host-1", "HOST_1", "demo", "127.0.0.1", 22, "LINUX", "DEV",
                null, "UNKNOWN", credentialId, null, paths, fingerprint, null, true, "now", "now");
    }
}
