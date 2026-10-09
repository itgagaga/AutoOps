package com.zhiwei.superemployee.app.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CursorCliRepairGateway}：命令参数不得把 {@code -} 当成 stdin 标记。
 * @author yangdb
 */
class CursorCliRepairGatewayTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldPassShortPromptArgInsteadOfStdinDash() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        CursorCliRepairGateway gateway = new CursorCliRepairGateway(tempDir.toString(), "agent", 30, "");

        List<String> command = gateway.buildCommand("ignored");

        assertThat(command).contains("agent", "-p", "--force", "--trust", "--sandbox", "disabled", "--output-format", "text");
        assertThat(command).noneMatch("-"::equals);
        assertThat(command.stream().anyMatch(arg -> arg.contains(CursorCliRepairGateway.PROMPT_RELATIVE))).isTrue();
    }

    @Test
    void shouldPassExplicitWorkspaceToCursorAgent() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        CursorCliRepairGateway gateway = new CursorCliRepairGateway(tempDir.toString(), "agent", 30, "");

        List<String> command = gateway.processArgs(tempDir);

        assertThat(command).contains("--workspace", tempDir.toAbsolutePath().normalize().toString());
        assertThat(command).contains("--sandbox", "disabled");
    }

    @Test
    void shouldPreferNestedMavenGitRoot() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        Path nested = tempDir.resolve("服务后端");
        Files.createDirectories(nested);
        Files.writeString(nested.resolve("pom.xml"), "<project/>");
        run(tempDir, "git", "init");
        run(nested, "git", "init");
        Files.writeString(tempDir.resolve("README.md"), "parent");
        CursorCliRepairGateway gateway = new CursorCliRepairGateway(tempDir.toString(), "agent", 30, "");

        List<Path> roots = gateway.resolveRepairGitRoots(tempDir);

        assertThat(roots).containsExactly(nested.toAbsolutePath().normalize());
    }

    private static void run(Path dir, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("command failed: " + String.join(" ", command));
        }
    }

    @Test
    void shouldTreatZhiweiPromptAsInternalDeliveryPath() throws Exception {
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
        CursorCliRepairGateway gateway = new CursorCliRepairGateway(tempDir.toString(), "agent", 30, "");
        assertThat(gateway.isInternalDeliveryPath(".zhiwei/repair-prompt.txt")).isTrue();
        assertThat(gateway.isInternalDeliveryPath(".zhiwei\\repair-prompt.txt")).isTrue();
        assertThat(gateway.isInternalDeliveryPath("src/main/java/Demo.java")).isFalse();
    }
}
