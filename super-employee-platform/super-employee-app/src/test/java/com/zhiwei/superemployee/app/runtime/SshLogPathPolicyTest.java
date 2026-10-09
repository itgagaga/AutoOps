package com.zhiwei.superemployee.app.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SshLogPathPolicy} 单测：目录判定、白名单校验，以及探测/读取命令生成。
 * @author yangdb
 */
class SshLogPathPolicyTest {

    /**
     * 场景：输入无扩展名目录路径与带扩展名日志文件路径。
     * 预期：无扩展名视为目录；含 {@code .log} 等扩展名视为文件。
     */
    @Test
    void shouldTreatExtensionlessPathAsDirectory() {
        assertThat(SshLogPathPolicy.looksLikeDirectory("/data/rcpx/insiis-rcpx-hlw/logs")).isTrue();
        assertThat(SshLogPathPolicy.looksLikeDirectory("/opt/app/logs/app.log")).isFalse();
        assertThat(SshLogPathPolicy.looksLikeDirectory("/opt/app/logs/app_log.2026-07-28.log")).isFalse();
    }

    /**
     * 场景：白名单含目录与精确文件两类条目。
     * 预期：允许目录本身、目录下子文件，以及精确匹配的文件路径。
     */
    @Test
    void shouldAllowExactOrChildUnderDirectoryWhitelist() {
        String allowed = "/data/rcpx/insiis-rcpx-hlw/logs|/opt/app/logs/app.log";
        assertThat(SshLogPathPolicy.requireAllowed("/data/rcpx/insiis-rcpx-hlw/logs", allowed))
                .isEqualTo("/data/rcpx/insiis-rcpx-hlw/logs");
        assertThat(SshLogPathPolicy.requireAllowed(
                "/data/rcpx/insiis-rcpx-hlw/logs/insii-rcpx-hlw_log.2026-07-28.log", allowed))
                .endsWith("insii-rcpx-hlw_log.2026-07-28.log");
        assertThat(SshLogPathPolicy.requireAllowed("/opt/app/logs/app.log", allowed))
                .isEqualTo("/opt/app/logs/app.log");
    }

    /**
     * 场景：请求路径不在白名单内。
     * 预期：抛出 {@link IllegalArgumentException}，消息包含「白名单」。
     */
    @Test
    void shouldRejectPathOutsideWhitelist() {
        assertThatThrownBy(() -> SshLogPathPolicy.requireAllowed("/etc/passwd", "/opt/app/logs"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("白名单");
    }

    /**
     * 场景：分别为目录与文件生成探测/读取命令。
     * 预期：目录探测用 {@code test -d/-r}；文件探测用 {@code test -r}；读取命令含时间窗口与 tail。
     */
    @Test
    void shouldBuildDirectoryAwareProbeAndReadCommands() {
        assertThat(SshLogPathPolicy.buildProbeCommand("/data/app/logs"))
                .isEqualTo("test -d /data/app/logs && test -r /data/app/logs");
        assertThat(SshLogPathPolicy.buildProbeCommand("/data/app/logs/app.log"))
                .isEqualTo("test -r /data/app/logs/app.log");
        assertThat(SshLogPathPolicy.buildReadCommand("/data/app/logs", "/data/app/logs", 10, 40))
                .contains("today=$(date +%F)")
                .contains("date -d")
                .contains("awk -v start=")
                .contains("tail -n 2000 -- \"$f\"")
                .contains("| tail -n 40");
        assertThat(SshLogPathPolicy.buildReadCommand(
                "/data/app/logs/a.log", "/data/app/logs", 15, 20))
                .contains("date -d")
                .contains("-15 minutes")
                .contains("awk -v start=")
                .contains("tail -n 2000 -- \"$f\"")
                .contains("| tail -n 20");
        // 必须先 tail 探针窗口再 grep/awk，禁止对整文件直接扫描。
        String cmd = SshLogPathPolicy.buildReadCommand(
                "/data/app/logs/a.log", "/data/app/logs", 30, 100);
        assertThat(cmd).contains("tail -n 5000 -- \"$f\" | grep -Eqm1");
        assertThat(cmd).contains("tail -n 5000 -- \"$f\" | awk -v start=");
        assertThat(cmd).doesNotContain("grep -Eqm1 '^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}:[0-9]{2}' \"$f\"");
    }
}
