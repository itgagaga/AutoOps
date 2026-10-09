package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.system.CodexEnvironmentService;
import com.zhiwei.superemployee.app.system.WorkspaceSettingsService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 在已批准的本地 Maven 工作区内调用 CodeX CLI 执行最小修复。
 * @author yangdb
 */
@Component
@ConditionalOnProperty(prefix = "super-employee.codex", name = "enabled", havingValue = "true")
public class CodexCliRepairGateway extends AbstractCliRepairGateway {

    @Autowired
    public CodexCliRepairGateway(@Value("${super-employee.codex.workspace:}") String workspace,
                                 @Value("${super-employee.codex.executable:codex}") String executable,
                                 @Value("${super-employee.codex.timeout-seconds:300}") long timeoutSeconds,
                                 ObjectProvider<WorkspaceSettingsService> workspaceSettings) {
        super(workspace, executable, timeoutSeconds, workspaceSettings);
    }

    CodexCliRepairGateway(String workspace, String executable, long timeoutSeconds) {
        super(workspace, executable, timeoutSeconds);
    }

    @Override
    public String engineId() {
        return RoutingLocalRepairGateway.ENGINE_CODEX;
    }

    @Override
    public String engineDisplayName() {
        return "CodeX CLI";
    }

    @Override
    protected String defaultExecutableName() {
        return "codex";
    }

    @Override
    protected Path officialInstallExecutable(String configuredName) {
        if (isWindows() && "codex".equalsIgnoreCase(configuredName)) {
            Path official = CodexEnvironmentService.officialInstallDirectory().resolve("codex.exe");
            return Files.isRegularFile(official) ? official : null;
        }
        return null;
    }

    @Override
    protected List<String> stdinPromptArgs() {
        return List.of("exec", "--sandbox", "workspace-write",
                "--config", "sandbox_workspace_write.network_access=true", "-");
    }

    /**
     * 兼容旧单测：组装 CodeX stdin 命令。
     */
    static List<String> buildCodexProcessCommand(Path resolvedExecutable) {
        List<String> args = List.of("exec", "--sandbox", "workspace-write",
                "--config", "sandbox_workspace_write.network_access=true", "-");
        String lower = resolvedExecutable.getFileName().toString().toLowerCase();
        List<String> command = new ArrayList<>();
        if (isWindows() && (lower.endsWith(".cmd") || lower.endsWith(".bat"))) {
            command.add(System.getenv().getOrDefault("ComSpec", "C:\\Windows\\System32\\cmd.exe"));
            command.add("/d");
            command.add("/s");
            command.add("/c");
            command.add("call \"" + resolvedExecutable + "\" exec --sandbox workspace-write "
                    + "--config sandbox_workspace_write.network_access=true -");
            return command;
        }
        command.add(resolvedExecutable.toString());
        command.addAll(args);
        return command;
    }
}
