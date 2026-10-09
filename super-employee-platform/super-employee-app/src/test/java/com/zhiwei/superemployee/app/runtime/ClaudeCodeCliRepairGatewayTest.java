package com.zhiwei.superemployee.app.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ClaudeCodeCliRepairGateway} 单测：引擎标识、命令参数与工作区校验。
 * @author yangdb
 */
class ClaudeCodeCliRepairGatewayTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldExposeClaudeEngineIdentity() throws Exception {
        ClaudeCodeCliRepairGateway gateway = gateway();
        assertThat(gateway.engineId()).isEqualTo("claude");
        assertThat(gateway.engineDisplayName()).isEqualTo("Claude Code CLI");
    }

    @Test
    void shouldBuildPrintModeCommandWithPermissionMode() throws Exception {
        ClaudeCodeCliRepairGateway gateway = gateway();
        List<String> command = gateway.buildCommand("ignored-for-claude");
        assertThat(command).contains("claude", "-p", "--permission-mode", "acceptEdits", "--output-format", "text");
        assertThat(command.stream().anyMatch(arg -> arg.contains(ClaudeCodeCliRepairGateway.PROMPT_RELATIVE))).isTrue();
        assertThat(command).noneMatch(value -> value.equals("-"));
    }

    @Test
    void shouldRejectForbiddenPromptTokens() throws Exception {
        ClaudeCodeCliRepairGateway gateway = gateway();
        assertThatThrownBy(() -> gateway.repair("请 git commit 修复", List.of("src/Main.java")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("禁止操作");
    }

    private ClaudeCodeCliRepairGateway gateway() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        return new ClaudeCodeCliRepairGateway(tempDir.toString(), "claude", 30, "", "acceptEdits");
    }
}
