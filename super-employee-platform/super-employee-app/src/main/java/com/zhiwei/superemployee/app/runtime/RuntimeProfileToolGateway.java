package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.CommandIncidentToolGateway;
import com.zhiwei.superemployee.core.gateway.IncidentModelGateway;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
import com.zhiwei.superemployee.core.workspace.BackendWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.ProjectBackendKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 按工程画像路由验证与部署：后端按 backendKind 执行构建验证，再跑端侧验证，部署走 Facade。
 * @author yangdb
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "super-employee.tools", name = "enabled", havingValue = "true")
public class RuntimeProfileToolGateway implements IncidentToolGateway {
    private final PatchDeploymentFacade deploymentFacade;
    private final FrontendBuildService frontendBuildService;
    private final String mavenVerificationCommand;
    private final String deploymentCommand;
    private final long timeoutSeconds;

    public RuntimeProfileToolGateway(
            PatchDeploymentFacade deploymentFacade,
            FrontendBuildService frontendBuildService,
            @Value("${super-employee.tools.verification-command:}") String mavenVerificationCommand,
            @Value("${super-employee.tools.deployment-command:}") String deploymentCommand,
            @Value("${super-employee.tools.timeout-seconds:300}") long timeoutSeconds) {
        this.deploymentFacade = deploymentFacade;
        this.frontendBuildService = frontendBuildService;
        this.mavenVerificationCommand = mavenVerificationCommand == null ? "" : mavenVerificationCommand.trim();
        this.deploymentCommand = deploymentCommand;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public ToolResult previewPatch(IncidentTask task, IncidentModelGateway.PatchPlan plan) {
        return delegate(task).previewPatch(task, plan);
    }

    @Override
    public ToolResult runVerification(IncidentTask task, IncidentModelGateway.PatchPlan plan) {
        ToolResult backend = delegate(task).runVerification(task, plan);
        if (!backend.isSuccess()) {
            return backend;
        }
        ToolResult frontend = frontendBuildService.verifyIfConfigured(task);
        if (!frontend.isSuccess()) {
            return new ToolResult(false,
                    backend.getOutput() + "\n\n—— 端侧验证 ——\n" + frontend.getOutput());
        }
        if (frontend.getOutput() != null && frontend.getOutput().contains("跳过")) {
            return backend;
        }
        return new ToolResult(true,
                backend.getOutput() + "\n\n—— 端侧验证 ——\n" + frontend.getOutput());
    }

    @Override
    public ToolResult deploy(IncidentTask task, IncidentModelGateway.PatchPlan plan) {
        return deploymentFacade.deploy(task);
    }

    private CommandIncidentToolGateway delegate(IncidentTask task) {
        ProjectBackendKind kind = resolveBackendKind(task);
        Path module = resolveModule(task, kind);
        String verify = resolveVerifyCommand(task, kind);
        List<String> allow = new ArrayList<>(kind.defaultAllowedCommands());
        boolean maven = kind.usesMavenWorkspace();
        return new CommandIncidentToolGateway(
                verify,
                deploymentCommand,
                module,
                allow,
                timeoutSeconds,
                maven);
    }

    private ProjectBackendKind resolveBackendKind(IncidentTask task) {
        Object raw = task.getContext().get("backendKind");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            return ProjectBackendKind.from(String.valueOf(raw));
        }
        Object type = task.getContext().get("applicationType");
        if (type != null) {
            String value = String.valueOf(type).trim().toUpperCase(Locale.ROOT);
            return switch (value) {
                case "NODE" -> ProjectBackendKind.NODE;
                case "GO" -> ProjectBackendKind.GO;
                case "PYTHON" -> ProjectBackendKind.PYTHON;
                case "DOCKER" -> ProjectBackendKind.DOCKER;
                default -> ProjectBackendKind.MAVEN;
            };
        }
        return ProjectBackendKind.MAVEN;
    }

    private Path resolveModule(IncidentTask task, ProjectBackendKind kind) {
        Object modulePath = task.getContext().get("backendModulePath");
        if (modulePath != null && !String.valueOf(modulePath).isBlank()) {
            return Path.of(String.valueOf(modulePath)).toAbsolutePath().normalize();
        }
        Object workspace = task.getContext().get("workspacePath");
        return BackendWorkspacePaths.requireBackend(kind, workspace == null ? null : String.valueOf(workspace));
    }

    private String resolveVerifyCommand(IncidentTask task, ProjectBackendKind kind) {
        Object override = task.getContext().get("backendVerifyCommand");
        if (override != null && !String.valueOf(override).isBlank()) {
            return String.valueOf(override).trim();
        }
        if (kind == ProjectBackendKind.MAVEN && !mavenVerificationCommand.isBlank()) {
            return mavenVerificationCommand;
        }
        if (kind == ProjectBackendKind.DOCKER) {
            Object compose = task.getContext().get("dockerComposeFile");
            String file = compose == null || String.valueOf(compose).isBlank()
                    ? "docker-compose.yml" : String.valueOf(compose).trim();
            Object image = task.getContext().get("dockerImageName");
            if (image != null && !String.valueOf(image).isBlank()) {
                return "docker compose -f " + file + " build";
            }
            return "docker compose -f " + file + " build";
        }
        if (kind == ProjectBackendKind.GRADLE) {
            // 优先 gradlew（若存在由用户覆盖命令）；默认 gradle test
            return "gradle test";
        }
        return kind.defaultVerifyCommand();
    }
}
