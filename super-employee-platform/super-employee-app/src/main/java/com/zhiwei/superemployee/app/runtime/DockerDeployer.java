package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.ops.DeploymentTarget;
import com.zhiwei.superemployee.app.ssh.ManagedSshConnector;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Docker 部署：本地 compose/build → docker save → SFTP → 远端 load + compose up。
 * @author yangdb
 */
@Service
public class DockerDeployer {
    private final ManagedSshConnector sshConnector;
    private final long timeoutSeconds;

    public DockerDeployer(
            ManagedSshConnector sshConnector,
            @Value("${super-employee.tools.deployment-timeout-seconds:300}") long timeoutSeconds) {
        this.sshConnector = sshConnector;
        this.timeoutSeconds = Math.max(60, timeoutSeconds);
    }

    public IncidentToolGateway.ToolResult deploy(IncidentTask task) {
        try {
            Map<String, Object> context = task.getContext();
            Path module = DeploySupport.moduleFrom(task);
            String code = DeploySupport.requireCode(context, module);
            String composeFile = DeploySupport.text(context.get("dockerComposeFile"));
            if (composeFile.isBlank()) {
                composeFile = "docker-compose.yml";
            }
            String imageName = DeploySupport.text(context.get("dockerImageName"));
            if (imageName.isBlank()) {
                imageName = code;
            }
            Path imageTar = buildAndSave(module, composeFile, imageName, task.getId());
            List<DeploymentTarget> targets = DeploySupport.resolveTargets(context);
            if (targets.isEmpty()) {
                targets = List.of(new DeploymentTarget("/opt/" + code, null));
            }
            boolean autoDeploy = DeploySupport.autoDeploy(context);
            if (!autoDeploy) {
                context.put("deploymentArtifact", imageTar.toString());
                return new IncidentToolGateway.ToolResult(true,
                        "Docker 镜像包已生成，自动部署未开启。\n镜像包：" + imageTar
                                + "\n远端将执行 docker load + compose up -d");
            }
            String hostId = DeploySupport.text(context.get("preferredHostId"));
            if (hostId.isBlank()) {
                return new IncidentToolGateway.ToolResult(false, "自动部署缺少目标主机");
            }
            StringBuilder message = new StringBuilder();
            for (DeploymentTarget target : targets) {
                String base = target.path() == null ? "/opt/" + code : target.path();
                message.append(uploadAndUp(hostId, base, imageTar, composeFile, module)).append("\n\n");
            }
            context.put("deploymentArtifact", imageTar.toString());
            context.put("deployedAt", Instant.now().toString());
            return new IncidentToolGateway.ToolResult(true, message.toString().trim());
        }
        catch (Exception ex) {
            return new IncidentToolGateway.ToolResult(false, "Docker 部署失败：" + ex.getMessage());
        }
    }

    private Path buildAndSave(Path module, String composeFile, String imageName, String incidentId)
            throws Exception {
        Path dir = DeploySupport.scriptDir(module, incidentId + "-docker");
        Path compose = module.resolve(composeFile);
        ProcessBuilder build;
        if (Files.isRegularFile(compose)) {
            build = new ProcessBuilder("docker", "compose", "-f", composeFile, "build")
                    .directory(module.toFile()).redirectErrorStream(true);
        } else {
            build = new ProcessBuilder("docker", "build", "-t", imageName, ".")
                    .directory(module.toFile()).redirectErrorStream(true);
        }
        run(build, "docker build");
        Path tar = dir.resolve("image.tar");
        String tag = imageName.contains(":") ? imageName : imageName + ":latest";
        run(new ProcessBuilder("docker", "save", "-o", tar.toString(), tag)
                .directory(module.toFile()).redirectErrorStream(true), "docker save");
        if (!Files.isRegularFile(tar)) {
            // compose 构建的服务镜像名不确定时，尝试 save 应用名
            run(new ProcessBuilder("docker", "save", "-o", tar.toString(), imageName)
                    .directory(module.toFile()).redirectErrorStream(true), "docker save");
        }
        if (!Files.isRegularFile(tar)) {
            throw new IllegalStateException("docker save 未生成 image.tar，请确认镜像标签为 " + tag);
        }
        return tar;
    }

    private void run(ProcessBuilder builder, String label) throws Exception {
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(label + " 超时\n" + DeploySupport.bounded(output));
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(label + " 失败\n" + DeploySupport.bounded(output));
        }
    }

    private String uploadAndUp(String hostId, String base, Path imageTar, String composeFile, Path module)
            throws Exception {
        String remoteTar = base + "/.zhiwei/incoming/image.tar";
        DeploySupport.uploadFile(sshConnector, hostId, imageTar, remoteTar);
        Path localCompose = module.resolve(composeFile);
        if (Files.isRegularFile(localCompose)) {
            DeploySupport.uploadFile(sshConnector, hostId, localCompose, base + "/" + composeFile);
        }
        String command = "mkdir -p " + base + " && docker load -i " + remoteTar
                + " && cd " + base + " && docker compose -f " + composeFile + " up -d";
        String output = DeploySupport.execRemote(sshConnector, hostId, command, timeoutSeconds);
        return "Docker 已部署。\n目标：" + hostId + ":" + base + (output.isBlank() ? "" : "\n" + output);
    }
}
