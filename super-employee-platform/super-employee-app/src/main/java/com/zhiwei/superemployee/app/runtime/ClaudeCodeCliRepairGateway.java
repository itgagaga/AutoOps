package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.system.ClaudeEnvironmentService;
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
 * 在已批准的本地 Maven 工作区内调用 Claude Code CLI（{@code claude -p}）执行最小修复。
 * <p>长提示写入工作区临时文件，避免 Windows 命令行长度限制；权限模式默认 {@code acceptEdits}。</p>
 * @author yangdb
 */
@Component
@ConditionalOnProperty(prefix = "super-employee.claude", name = "enabled", havingValue = "true")
public class ClaudeCodeCliRepairGateway extends AbstractCliRepairGateway {
    static final String PROMPT_RELATIVE = ".zhiwei/repair-prompt.txt";

    private final String apiKey;
    private final String permissionMode;

    @Autowired
    public ClaudeCodeCliRepairGateway(
            @Value("${super-employee.claude.workspace:${super-employee.codex.workspace:}}") String workspace,
            @Value("${super-employee.claude.executable:claude}") String executable,
            @Value("${super-employee.claude.timeout-seconds:300}") long timeoutSeconds,
            @Value("${super-employee.claude.api-key:}") String apiKey,
            @Value("${super-employee.claude.permission-mode:acceptEdits}") String permissionMode,
            ObjectProvider<WorkspaceSettingsService> workspaceSettings) {
        super(workspace, executable, timeoutSeconds, workspaceSettings);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.permissionMode = normalizePermissionMode(permissionMode);
    }

    ClaudeCodeCliRepairGateway(String workspace, String executable, long timeoutSeconds,
                               String apiKey, String permissionMode) {
        super(workspace, executable, timeoutSeconds);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.permissionMode = normalizePermissionMode(permissionMode);
    }

    @Override
    public String engineId() {
        return RoutingLocalRepairGateway.ENGINE_CLAUDE;
    }

    @Override
    public String engineDisplayName() {
        return "Claude Code CLI";
    }

    @Override
    protected String defaultExecutableName() {
        return "claude";
    }

    @Override
    protected Path officialInstallExecutable(String configuredName) {
        if (!isWindows()) {
            return null;
        }
        List<String> names = List.of(
                configuredName == null || configuredName.isBlank() ? "claude" : configuredName,
                "claude");
        for (Path dir : ClaudeEnvironmentService.officialInstallDirectories()) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            for (String name : names) {
                for (String suffix : List.of(".exe", ".cmd", ".bat", "")) {
                    Path candidate = dir.resolve(name + suffix).normalize();
                    if (Files.isRegularFile(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    @Override
    protected List<String> stdinPromptArgs() {
        return List.of(
                "-p",
                "--permission-mode", permissionMode,
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
        // 提示已写入文件；关闭 stdin，避免 Claude 阻塞等待输入。
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
        env.put("ANTHROPIC_API_KEY", apiKey);
    }

    private static String normalizePermissionMode(String value) {
        if (value == null || value.isBlank()) {
            return "acceptEdits";
        }
        String mode = value.trim();
        if (mode.equals("acceptEdits") || mode.equals("bypassPermissions")
                || mode.equals("default") || mode.equals("dontAsk") || mode.equals("auto")) {
            return mode;
        }
        return "acceptEdits";
    }
}
