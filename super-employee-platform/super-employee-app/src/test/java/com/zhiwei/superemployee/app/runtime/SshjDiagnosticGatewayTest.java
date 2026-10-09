package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.resource.CredentialSecretService;
import com.zhiwei.superemployee.app.resource.ManagedHost;
import com.zhiwei.superemployee.app.resource.ResourceRepository;
import com.zhiwei.superemployee.core.gateway.SshDiagnosticRequest;
import com.zhiwei.superemployee.core.gateway.SshDiagnosticTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * {@link SshjDiagnosticGateway} 单测：只读诊断模板渲染、日志白名单、注入拒绝与主机指纹解析。
 * @author yangdb
 */
class SshjDiagnosticGatewayTest {

    private final SshjDiagnosticGateway gateway = new SshjDiagnosticGateway(
            mock(ResourceRepository.class), new CredentialSecretService("test-key"),
            mock(HostConnectivityService.class),
            10, 20, 12000, 200, 1440, "host-1=SHA256:example");

    /**
     * 场景：对各预定义诊断模板生成命令。
     * 预期：仅输出固定只读命令片段（uptime/df、systemctl、journalctl 等），不引入任意 shell。
     */
    @Test
    void shouldOnlyRenderFixedReadOnlyTemplates() {
        assertThat(gateway.command(new SshDiagnosticRequest("host-1", SshDiagnosticTemplate.HOST_HEALTH,
                null, null, 0, 0))).contains("uptime").contains("df -hP");
        assertThat(gateway.command(new SshDiagnosticRequest("host-1", SshDiagnosticTemplate.SERVICE_STATUS,
                "nginx.service", null, 0, 20))).contains("systemctl").contains("nginx.service");
        assertThat(gateway.command(new SshDiagnosticRequest("host-1", SshDiagnosticTemplate.JOURNAL_LOGS,
                null, null, 60, 50))).contains("journalctl").contains("-n 50");
        assertThat(gateway.command(new SshDiagnosticRequest("host-1", SshDiagnosticTemplate.JAVA_APPLICATION_LOGS,
                "demo.service", null, 30, 20))).contains("journalctl").contains("-u demo.service");
    }

    /**
     * 场景：APP_LOG_FILE 指向主机白名单内的精确日志文件。
     * 预期：生成含时间窗口（date -d / awk）与 tail 的只读读取命令。
     */
    @Test
    void shouldRenderTailForWhitelistedAppLogFile() {
        ManagedHost host = new ManagedHost("host-1", "HOST_1", "demo", "10.0.0.1", 22, "LINUX", "DEV",
                null, "ONLINE", "cred-1", null, "/opt/app/logs/app.log|/var/log/app/error.log",
                "SHA256:abc", null, true, "now", "now");

        assertThat(gateway.command(new SshDiagnosticRequest("host-1", SshDiagnosticTemplate.APP_LOG_FILE,
                null, "/opt/app/logs/app.log", 10, 80), host))
                .contains("date -d")
                .contains("-10 minutes")
                .contains("awk -v start=")
                .contains("tail -n 80 -- \"$f\"");
    }

    /**
     * 场景：白名单条目为日志目录，分别读取目录本身与目录下分日文件。
     * 预期：目录模式含 cd/today；子文件模式同样带时间窗口与 tail。
     */
    @Test
    void shouldAllowDailyLogDirectoryAndChildFile() {
        ManagedHost host = new ManagedHost("host-1", "HOST_1", "demo", "10.0.0.1", 22, "LINUX", "DEV",
                null, "ONLINE", "cred-1", null, "/data/rcpx/insiis-rcpx-hlw/logs",
                "SHA256:abc", null, true, "now", "now");

        assertThat(gateway.command(new SshDiagnosticRequest("host-1", SshDiagnosticTemplate.APP_LOG_FILE,
                null, "/data/rcpx/insiis-rcpx-hlw/logs", 10, 50), host))
                .contains("cd -- \"$d\"")
                .contains("today=$(date +%F)")
                .contains("-10 minutes")
                .contains("awk -v start=")
                .contains("tail -n 50 -- \"$f\"");

        assertThat(gateway.command(new SshDiagnosticRequest("host-1", SshDiagnosticTemplate.APP_LOG_FILE,
                null, "/data/rcpx/insiis-rcpx-hlw/logs/insii-rcpx-hlw_log.2026-07-28.log", 20, 30), host))
                .contains("-20 minutes")
                .contains("awk -v start=")
                .contains("tail -n 30 -- \"$f\"");
    }

    /**
     * 场景：日志路径含命令注入、未列入白名单或相对穿越。
     * 预期：均抛出 IllegalArgumentException；未命中白名单时消息含「白名单」。
     */
    @Test
    void shouldRejectUnsafeOrUnlistedLogPaths() {
        ManagedHost host = new ManagedHost("host-1", "HOST_1", "demo", "10.0.0.1", 22, "LINUX", "DEV",
                null, "ONLINE", "cred-1", null, "/opt/app/logs/app.log",
                "SHA256:abc", null, true, "now", "now");

        assertThatThrownBy(() -> gateway.command(new SshDiagnosticRequest("host-1",
                SshDiagnosticTemplate.APP_LOG_FILE, null, "/opt/app/logs/app.log;rm -rf /", 0, 20), host))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.command(new SshDiagnosticRequest("host-1",
                SshDiagnosticTemplate.APP_LOG_FILE, null, "/etc/passwd", 0, 20), host))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("白名单");
        assertThatThrownBy(() -> gateway.command(new SshDiagnosticRequest("host-1",
                SshDiagnosticTemplate.APP_LOG_FILE, null, "../secrets", 0, 20), host))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 场景：服务名含 shell 元字符，或 journal 查询窗口超过上限。
     * 预期：command 构造阶段即抛出 IllegalArgumentException。
     */
    @Test
    void shouldRejectInjectedServiceNameAndOversizedRanges() {
        assertThatThrownBy(() -> gateway.command(new SshDiagnosticRequest("host-1",
                SshDiagnosticTemplate.SERVICE_STATUS, "nginx; rm -rf /", null, 0, 20)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gateway.command(new SshDiagnosticRequest("host-1",
                SshDiagnosticTemplate.JOURNAL_LOGS, null, null, 1441, 20)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 场景：主机记录已存储多指纹，同时环境变量也有回退指纹。
     * 预期：优先使用主机字段；hostFingerprints 按 | 拆分为独立指纹列表。
     */
    @Test
    void shouldPreferHostStoredFingerprint() {
        ManagedHost host = new ManagedHost("host-1", "HOST_1", "demo", "10.0.0.1", 22, "LINUX", "DEV",
                null, "ONLINE", "cred-1", null, null, "SHA256:from-host|SHA256:second",
                null, true, "now", "now");

        assertThat(gateway.resolveFingerprint(host)).isEqualTo("SHA256:from-host|SHA256:second");
        assertThat(gateway.hostFingerprints(gateway.resolveFingerprint(host)))
                .containsExactly("SHA256:from-host", "SHA256:second");
    }

    /**
     * 场景：指纹字符串含多个以 | 分隔且带空格的 SHA256 值。
     * 预期：拆分并 trim 后得到有序的指纹列表。
     */
    @Test
    void shouldSplitMultipleVerifiedHostFingerprints() {
        assertThat(gateway.hostFingerprints("SHA256:rsa| SHA256:ecdsa |SHA256:ed25519"))
                .containsExactly("SHA256:rsa", "SHA256:ecdsa", "SHA256:ed25519");
    }
}
