package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.ssh.ManagedSshConnector;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
import com.zhiwei.superemployee.core.workspace.ClientWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.ProjectClientKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Web 静态资源部署：将本地 dist 同步到远端静态目录。
 * @author yangdb
 */
@Service
public class ClientStaticDeployer {
    private static final DateTimeFormatter VERSION =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final ManagedSshConnector sshConnector;
    private final long timeoutSeconds;

    public ClientStaticDeployer(
            ManagedSshConnector sshConnector,
            @Value("${super-employee.tools.deployment-timeout-seconds:180}") long timeoutSeconds) {
        this.sshConnector = sshConnector;
        this.timeoutSeconds = Math.max(30, timeoutSeconds);
    }

    public IncidentToolGateway.ToolResult deployIfConfigured(IncidentTask task) {
        Map<String, Object> context = task.getContext();
        ProjectClientKind clientKind = resolveClientKind(context);
        if (clientKind == ProjectClientKind.UNI_APP || clientKind == ProjectClientKind.MP_WEIXIN_NATIVE) {
            return new IncidentToolGateway.ToolResult(true,
                    "端侧类型 " + clientKind.displayName() + " 仅做验证门禁，不执行自动发布");
        }
        if (clientKind != ProjectClientKind.WEB_NPM) {
            return new IncidentToolGateway.ToolResult(true, "未配置 Web 端侧，跳过静态部署");
        }
        String remote = DeploySupport.text(context.get("clientDeployPath"));
        if (remote.isBlank()) {
            return new IncidentToolGateway.ToolResult(true, "未配置端侧静态部署目录，跳过静态部署");
        }
        if (!DeploySupport.autoDeploy(context)) {
            return new IncidentToolGateway.ToolResult(true, "自动部署未开启，跳过静态部署（本地 dist 已由验证阶段构建）");
        }
        try {
            String hostId = DeploySupport.text(context.get("preferredHostId"));
            if (hostId.isBlank()) {
                return new IncidentToolGateway.ToolResult(false, "静态部署缺少目标主机");
            }
            Path client = ClientWorkspacePaths.requireClient(clientKind,
                    DeploySupport.text(context.get("frontendWorkspacePath")));
            String distDir = DeploySupport.text(context.get("clientDistDir"));
            if (distDir.isBlank()) {
                distDir = "dist";
            }
            Path dist = client.resolve(distDir);
            if (!Files.isDirectory(dist)) {
                return new IncidentToolGateway.ToolResult(false, "端侧产物目录不存在：" + dist);
            }
            String normalized = DeploySupport.normalizeDeploymentPath(remote);
            String backup = normalized + ".bak-" + VERSION.format(Instant.now());
            DeploySupport.execRemote(sshConnector, hostId,
                    "if [ -d '" + normalized + "' ]; then cp -a '" + normalized + "' '" + backup
                            + "'; fi; rm -rf '" + normalized + "'; mkdir -p '" + normalized + "'",
                    timeoutSeconds);
            DeploySupport.uploadDirectory(sshConnector, hostId, dist, normalized);
            context.put("clientDeployedPath", normalized);
            context.put("clientDeployBackup", backup);
            return new IncidentToolGateway.ToolResult(true,
                    "Web 静态资源已同步。\n目标：" + hostId + ":" + normalized + "\n备份：" + backup);
        }
        catch (Exception ex) {
            return new IncidentToolGateway.ToolResult(false, "静态部署失败：" + ex.getMessage());
        }
    }

    private ProjectClientKind resolveClientKind(Map<String, Object> context) {
        Object raw = context.get("clientKind");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            return ProjectClientKind.from(String.valueOf(raw));
        }
        Object path = context.get("frontendWorkspacePath");
        if (path != null && !String.valueOf(path).isBlank()) {
            return ProjectClientKind.WEB_NPM;
        }
        return ProjectClientKind.NONE;
    }
}
