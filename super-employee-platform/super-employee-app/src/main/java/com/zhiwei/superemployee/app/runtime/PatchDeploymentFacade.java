package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
import com.zhiwei.superemployee.core.workspace.ProjectBackendKind;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 混合部署门面：按 backendKind 路由后端部署，再按端侧配置执行静态同步或跳过小程序发布。
 * @author yangdb
 */
@Service
public class PatchDeploymentFacade {
    private final JavaPatchDeploymentService javaJarDeployer;
    private final JavaWarDeploymentService javaWarDeployer;
    private final PathReplaceDeployer pathReplaceDeployer;
    private final DockerDeployer dockerDeployer;
    private final ClientStaticDeployer clientStaticDeployer;

    public PatchDeploymentFacade(
            JavaPatchDeploymentService javaJarDeployer,
            JavaWarDeploymentService javaWarDeployer,
            PathReplaceDeployer pathReplaceDeployer,
            DockerDeployer dockerDeployer,
            ClientStaticDeployer clientStaticDeployer) {
        this.javaJarDeployer = javaJarDeployer;
        this.javaWarDeployer = javaWarDeployer;
        this.pathReplaceDeployer = pathReplaceDeployer;
        this.dockerDeployer = dockerDeployer;
        this.clientStaticDeployer = clientStaticDeployer;
    }

    public IncidentToolGateway.ToolResult deploy(IncidentTask task) {
        ProjectBackendKind kind = resolveBackendKind(task);
        IncidentToolGateway.ToolResult backend = switch (kind) {
            case MAVEN, GRADLE -> javaJarDeployer.deploy(task);
            case JAVA_WAR -> javaWarDeployer.deploy(task);
            case NODE, GO, PYTHON -> pathReplaceDeployer.deploy(task, kind);
            case DOCKER -> dockerDeployer.deploy(task);
        };
        if (!backend.isSuccess()) {
            return backend;
        }
        IncidentToolGateway.ToolResult client = clientStaticDeployer.deployIfConfigured(task);
        if (!client.isSuccess()) {
            return new IncidentToolGateway.ToolResult(false,
                    backend.getOutput() + "\n\n—— 端侧部署 ——\n" + client.getOutput());
        }
        return new IncidentToolGateway.ToolResult(true,
                backend.getOutput() + "\n\n—— 端侧部署 ——\n" + client.getOutput());
    }

    private ProjectBackendKind resolveBackendKind(IncidentTask task) {
        Object raw = task.getContext().get("backendKind");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            return ProjectBackendKind.from(String.valueOf(raw));
        }
        Object type = task.getContext().get("applicationType");
        if (type != null) {
            return switch (String.valueOf(type).trim().toUpperCase(Locale.ROOT)) {
                case "NODE" -> ProjectBackendKind.NODE;
                case "GO" -> ProjectBackendKind.GO;
                case "PYTHON" -> ProjectBackendKind.PYTHON;
                case "DOCKER" -> ProjectBackendKind.DOCKER;
                case "JAVA_WAR" -> ProjectBackendKind.JAVA_WAR;
                default -> ProjectBackendKind.MAVEN;
            };
        }
        return ProjectBackendKind.MAVEN;
    }
}
