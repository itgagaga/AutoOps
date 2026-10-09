package com.zhiwei.superemployee.app.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CodexCliRepairGateway} 单测：提示词禁令、工作区边界、沙箱命令、Git 快照解析与失败可读性。
 * @author yangdb
 */
class CodexCliRepairGatewayTest {

    /** 临时 Maven 工作区，多数用例在根目录写入 pom.xml。 */
    @TempDir
    Path tempDir;

    /**
     * 场景：修复提示中包含 git commit / deploy 等禁止操作词。
     * 预期：repair 抛出 IllegalArgumentException，消息含「禁止操作」。
     */
    @Test
    void shouldRejectForbiddenPromptTokens() throws Exception {
        CodexCliRepairGateway gateway = gateway();

        assertThatThrownBy(() -> gateway.repair("请 git commit 修复", List.of("src/Main.java")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("禁止操作");
        assertThatThrownBy(() -> gateway.repair("请 deploy 到生产", List.of("src/Main.java")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("禁止操作");
    }

    /**
     * 场景：允许修改文件列表尝试跳出工作区（相对上级或绝对路径）。
     * 预期：repair 拒绝并提示「不能超出工作区」。
     */
    @Test
    void shouldRejectWorkspaceEscapeInAllowedFiles() throws Exception {
        CodexCliRepairGateway gateway = gateway();

        assertThatThrownBy(() -> gateway.repair("修复空指针", List.of("../secrets.env")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超出工作区");
        assertThatThrownBy(() -> gateway.repair("修复空指针", List.of(tempDir.resolve("pom.xml").toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超出工作区");
    }

    /**
     * 场景：构造送入 CodeX CLI 的执行命令。
     * 预期：固定 workspace-write 沙箱并开启网络配置；命令参数不含 git/deploy。
     */
    @Test
    void shouldBuildSandboxedWorkspaceOnlyCommand() throws Exception {
        CodexCliRepairGateway gateway = gateway();

        List<String> command = gateway.buildCommand("修复空指针");

        assertThat(command).containsExactly(
                "codex", "exec", "--sandbox", "workspace-write",
                "--config", "sandbox_workspace_write.network_access=true",
                "修复空指针");
        assertThat(command).noneMatch(value -> value.toLowerCase().contains("git")
                || value.toLowerCase().contains("deploy"));
    }

    /**
     * 场景：工作区根及子目录均无 pom.xml。
     * 预期：构造 Gateway 即失败，消息提示缺少 pom.xml。
     */
    @Test
    void shouldRejectNonMavenWorkspace() {
        assertThatThrownBy(() -> new CodexCliRepairGateway(tempDir.toString(), "codex", 30))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pom.xml");
    }

    /**
     * 场景：父目录作为工作区，子目录含 pom.xml。
     * 预期：可成功构造 Gateway，并能生成含 codex 的命令。
     */
    @Test
    void shouldAcceptParentWorkspaceWithNestedPom() throws Exception {
        Path backend = Files.createDirectories(tempDir.resolve("backend"));
        Files.writeString(backend.resolve("pom.xml"), "<project/>");

        CodexCliRepairGateway gateway = new CodexCliRepairGateway(tempDir.toString(), "codex", 30);
        assertThat(gateway.buildCommand("修复空指针")).contains("codex");
    }

    /**
     * 场景：Git porcelain 空字符分隔输出含中文、空格与重命名路径。
     * 预期：解析出正确相对路径与状态码，不保留旧路径键，也不残留引号转义。
     */
    @Test
    void shouldParseNullDelimitedGitStatusWithUnicodePaths() {
        String output = " M docs/运维说明.md\u0000"
                + "?? 模块 A/新文件.java\u0000"
                + "R  新目录/新名称.java\u0000旧目录/旧名称.java\u0000";

        Map<String, String> status = AbstractCliRepairGateway.parsePorcelainStatus(output);

        assertThat(status).containsEntry("docs/运维说明.md", " M")
                .containsEntry("模块 A/新文件.java", "??")
                .containsEntry("新目录/新名称.java", "R ");
        assertThat(status).doesNotContainKey("旧目录/旧名称.java");
    }

    @Test
    void shouldParseNullDelimitedDiffNameOnly() {
        String output = "insiis-rcpx-common/src/PortalSecurityContextSync.java\u0000"
                + "insiis-rcpx-common/src/PortalTokenService.java\u0000";

        assertThat(AbstractCliRepairGateway.parseNameOnly(output))
                .containsExactly(
                        "insiis-rcpx-common/src/PortalSecurityContextSync.java",
                        "insiis-rcpx-common/src/PortalTokenService.java");
        assertThat(AbstractCliRepairGateway.parseNameOnly("")).isEmpty();
    }

    @Test
    void shouldIgnoreGitCrlfWarningsInNameOnlyOutput() {
        String polluted = "warning: in the working copy of 'insiis-rcpx-common/a.java', LF will be replaced by CRLF the next time Git touches it\u0000"
                + "insiis-rcpx-common/src/Main.java\u0000";

        assertThat(AbstractCliRepairGateway.parseNameOnly(polluted))
                .containsExactly("insiis-rcpx-common/src/Main.java");
        assertThat(AbstractCliRepairGateway.isPlausibleRepoRelativePath(
                "warning: in the working copy of 'a.java', LF will be replaced by CRLF")).isFalse();
    }

    /**
     * 场景：超长中文相对路径生成快照键。
     * 预期：键长固定 64，且仅为小写十六进制，满足 Windows 文件名约束。
     */
    @Test
    void shouldUseFixedLengthSnapshotKeyForLongUnicodePath() {
        String path = "定级备案/" + "主管部门审核批准网络安全等级保护对象安全保护等级的意见".repeat(12) + ".docx";

        String key = AbstractCliRepairGateway.snapshotKey(path);

        assertThat(key).hasSize(64).matches("[a-f0-9]{64}");
    }

    /**
     * 场景：在 Windows 上通过 cmd 包装启动 codex.cmd。
     * 预期：命令含 exec/workspace-write，并以 stdin 标记 {@code -} 结尾，避免中文提示参与命令行转义。
     */
    @Test
    void shouldBuildWindowsCmdLaunchWithStdin() {
        Path executable = Path.of("C:\\Program Files\\nodejs\\codex.cmd");

        List<String> command = CodexCliRepairGateway.buildCodexProcessCommand(executable);

        assertThat(String.join(" ", command))
                .contains("exec", "workspace-write")
                .endsWith("-");
    }

    /**
     * 场景：CLI 因额度耗尽失败。
     * 预期：failureMessage 给出可操作的中文说明，并保留 usage_limit_exceeded，而非笼统「未生成差异」。
     */
    @Test
    void shouldExplainUsageLimitFailure() {
        LocalRepairGateway.Result result = new LocalRepairGateway.Result(
                false,
                "You've hit your usage limit. try again later. usage_limit_exceeded",
                "",
                List.of(),
                true,
                "repair-id");

        assertThat(result.failureMessage("CodeX CLI"))
                .contains("使用额度已耗尽")
                .contains("usage_limit_exceeded");
    }

    /**
     * 在临时目录写入最小 pom.xml 后构造 Gateway。
     *
     * @return 绑定到 {@link #tempDir} 的 {@link CodexCliRepairGateway}
     * @throws Exception 写 pom 失败时抛出
     */
    private CodexCliRepairGateway gateway() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        return new CodexCliRepairGateway(tempDir.toString(), "codex", 30);
    }
}
