package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.system.CursorEnvironmentService;
import com.zhiwei.superemployee.app.system.WorkspaceSettingsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 在已批准的本地 Maven 工作区内调用 Cursor CLI（{@code agent -p --force}）执行最小修复。
 * <p>长提示写入工作区临时文件；Cursor 的 {@code -} 不是 stdin 标记，而是普通提示词。</p>
 * @author yangdb
 */
@Component
@ConditionalOnProperty(prefix = "super-employee.cursor", name = "enabled", havingValue = "true")
public class CursorCliRepairGateway extends AbstractCliRepairGateway {
    static final String PROMPT_RELATIVE = ".zhiwei/repair-prompt.txt";

    private final String apiKey;

    @Autowired
    public CursorCliRepairGateway(
            @Value("${super-employee.cursor.workspace:${super-employee.codex.workspace:}}") String workspace,
            @Value("${super-employee.cursor.executable:agent}") String executable,
            @Value("${super-employee.cursor.timeout-seconds:300}") long timeoutSeconds,
            @Value("${super-employee.cursor.api-key:}") String apiKey,
            ObjectProvider<WorkspaceSettingsService> workspaceSettings) {
        super(workspace, executable, timeoutSeconds, workspaceSettings);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    CursorCliRepairGateway(String workspace, String executable, long timeoutSeconds, String apiKey) {
        super(workspace, executable, timeoutSeconds);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public String engineId() {
        return RoutingLocalRepairGateway.ENGINE_CURSOR;
    }

    @Override
    public String engineDisplayName() {
        return "Cursor CLI";
    }

    @Override
    protected String defaultExecutableName() {
        return "agent";
    }

    @Override
    protected Path officialInstallExecutable(String configuredName) {
        if (!isWindows()) {
            return null;
        }
        Path dir = CursorEnvironmentService.officialInstallDirectory();
        if (!Files.isDirectory(dir)) {
            return null;
        }
        List<String> names = List.of(
                configuredName == null || configuredName.isBlank() ? "agent" : configuredName,
                "agent",
                "cursor-agent");
        for (String name : names) {
            for (String suffix : List.of(".cmd", ".exe", ".bat", "")) {
                Path candidate = dir.resolve(name + suffix).normalize();
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    @Override
    protected List<String> processArgs(Path workspace) {
        String ws = workspace.toAbsolutePath().normalize().toString();
        return List.of(
                "-p",
                "--force",
                "--trust",
                "--sandbox", "disabled",
                "--workspace", ws,
                "--output-format", "text",
                "请完整阅读并严格执行工作区文件 " + PROMPT_RELATIVE
                        + " 中的全部修复任务；必须直接修改源码完成修复，不得只读写提示文件；"
                        + "不要修改或删除 " + PROMPT_RELATIVE + "；"
                        + "只修改完成修复所必需的源码；禁止 git commit、push 和部署；"
                        + "不要创建无关文件。");
    }

    @Override
    protected List<String> stdinPromptArgs() {
        // 兼容旧单测；真实执行走 processArgs(workspace)。
        return List.of(
                "-p",
                "--force",
                "--trust",
                "--sandbox", "disabled",
                "--output-format", "text",
                "请完整阅读并严格执行工作区文件 " + PROMPT_RELATIVE
                        + " 中的全部修复任务；必须直接修改源码完成修复，不得只读写提示文件；"
                        + "不要修改或删除 " + PROMPT_RELATIVE + "；"
                        + "只修改完成修复所必需的源码；禁止 git commit、push 和部署；"
                        + "不要创建无关文件。");
    }

    @Override
    protected void preparePromptDelivery(Path workspace, String scopedPrompt) throws IOException {
        Path file = workspace.resolve(PROMPT_RELATIVE).normalize();
        if (!file.startsWith(workspace.normalize())) {
            throw new IOException("非法提示文件路径");
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, scopedPrompt, StandardCharsets.UTF_8);
    }

    @Override
    protected void deliverPrompt(Process process, String scopedPrompt) throws IOException {
        // 提示已写入文件；关闭 stdin，避免 CLI 阻塞等待管道输入。
        process.getOutputStream().close();
    }

    @Override
    protected void cleanupPromptDelivery(Path workspace) {
        try {
            Files.deleteIfExists(workspace.resolve(PROMPT_RELATIVE));
        }
        catch (IOException ignored) {
            // 清理失败不影响主流程
        }
    }

    @Override
    protected void configureProcessEnvironment(ProcessBuilder processBuilder) {
        if (apiKey.isBlank()) {
            return;
        }
        Map<String, String> env = processBuilder.environment();
        env.put("CURSOR_API_KEY", apiKey);
    }
}
