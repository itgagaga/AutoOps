package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.ops.DeploymentTarget;
import com.zhiwei.superemployee.app.ssh.ManagedSshConnector;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
import com.zhiwei.superemployee.core.workspace.ProjectBackendKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Node / Go / Python 目录替换部署：打包制品、生成脚本、可选 SSH 上传执行。
 * @author yangdb
 */
@Service
public class PathReplaceDeployer {
    private static final DateTimeFormatter VERSION =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final ManagedSshConnector sshConnector;
    private final long timeoutSeconds;

    public PathReplaceDeployer(
            ManagedSshConnector sshConnector,
            @Value("${super-employee.tools.deployment-timeout-seconds:180}") long timeoutSeconds) {
        this.sshConnector = sshConnector;
        this.timeoutSeconds = Math.max(30, timeoutSeconds);
    }

    public IncidentToolGateway.ToolResult deploy(IncidentTask task, ProjectBackendKind kind) {
        try {
            Map<String, Object> context = task.getContext();
            Path module = DeploySupport.moduleFrom(task);
            String code = DeploySupport.requireCode(context, module);
            boolean autoDeploy = DeploySupport.autoDeploy(context);
            List<DeploymentTarget> targets = DeploySupport.resolveTargets(context);
            if (targets.isEmpty()) {
                targets = List.of(new DeploymentTarget("/opt/" + code, defaultArtifact(kind, code)));
            }
            Path artifact = prepareArtifact(module, kind, code, task.getId());
            List<String> outputs = new ArrayList<>();
            for (int i = 0; i < targets.size(); i++) {
                DeploymentTarget target = targets.get(i);
                String base = target.path() == null ? "/opt/" + code : target.path();
                Path scripts = generateScripts(module, task.getId() + "-" + i, kind, code, base, artifact.getFileName().toString());
                if (autoDeploy) {
                    String hostId = DeploySupport.text(context.get("preferredHostId"));
                    if (hostId.isBlank()) {
                        return new IncidentToolGateway.ToolResult(false, "自动部署缺少目标主机");
                    }
                    outputs.add(uploadAndDeploy(hostId, base, code, artifact, scripts));
                } else {
                    outputs.add("目标：" + base + "\n制品：" + artifact + "\n脚本：" + scripts);
                }
            }
            context.put("deploymentArtifact", artifact.toString());
            if (!autoDeploy) {
                return new IncidentToolGateway.ToolResult(true,
                        kind.displayName() + " 部署包与脚本已生成，自动部署未开启。\n" + String.join("\n", outputs));
            }
            context.put("deployedAt", Instant.now().toString());
            return new IncidentToolGateway.ToolResult(true, String.join("\n\n", outputs));
        }
        catch (Exception ex) {
            return new IncidentToolGateway.ToolResult(false,
                    kind.displayName() + " 部署失败：" + ex.getMessage());
        }
    }

    private String defaultArtifact(ProjectBackendKind kind, String code) {
        return switch (kind) {
            case GO -> code;
            case NODE, PYTHON -> "release.zip";
            default -> code;
        };
    }

    private Path prepareArtifact(Path module, ProjectBackendKind kind, String code, String incidentId)
            throws IOException, InterruptedException {
        Path dir = DeploySupport.scriptDir(module, incidentId + "-artifact");
        return switch (kind) {
            case GO -> buildGoBinary(module, dir, code);
            case NODE, PYTHON -> zipRelease(module, dir.resolve("release.zip"), kind);
            default -> throw new IllegalStateException("PathReplace 不支持 " + kind);
        };
    }

    private Path buildGoBinary(Path module, Path dir, String code) throws IOException, InterruptedException {
        Path out = dir.resolve(code);
        List<String> command = List.of("go", "build", "-o", out.toString(), ".");
        Process process = new ProcessBuilder(command).directory(module.toFile()).redirectErrorStream(true).start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("go build 超时");
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(out)) {
            throw new IllegalStateException("go build 失败，exit=" + process.exitValue());
        }
        return out;
    }

    private Path zipRelease(Path module, Path zipFile, ProjectBackendKind kind) throws IOException {
        Files.createDirectories(zipFile.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile));
             Stream<Path> walk = Files.walk(module)) {
            List<Path> files = walk.filter(Files::isRegularFile)
                    .filter(path -> !shouldSkip(module, path))
                    .limit(5_000)
                    .toList();
            for (Path file : files) {
                String entry = module.relativize(file).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(entry));
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
        if (kind == ProjectBackendKind.NODE && !Files.isRegularFile(module.resolve("package.json"))) {
            throw new IllegalStateException("Node 工程缺少 package.json");
        }
        return zipFile;
    }

    private boolean shouldSkip(Path module, Path file) {
        Path rel = module.relativize(file);
        for (Path part : rel) {
            String name = part.toString().toLowerCase();
            if (name.equals("node_modules") || name.equals(".git") || name.equals("target")
                    || name.equals(".zhiwei-deploy") || name.equals(".zhiwei-logs")
                    || name.equals("venv") || name.equals(".venv") || name.equals("__pycache__")) {
                return true;
            }
        }
        return false;
    }

    private Path generateScripts(Path module, String id, ProjectBackendKind kind, String code, String base, String artifactName)
            throws IOException {
        Path directory = DeploySupport.scriptDir(module, id);
        Files.writeString(directory.resolve("backup.sh"), backupScript(base, code), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("start.sh"), startScript(kind, base, code, artifactName), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("deploy.sh"), deployScript(kind, base, code, artifactName), StandardCharsets.UTF_8);
        return directory;
    }

    private String backupScript(String base, String code) {
        return "#!/bin/sh\nset -eu\nBASE=\"" + base + "\"\nAPP=\"" + code + "\"\n"
                + "mkdir -p \"$BASE/backups\"\n"
                + "if [ -d \"$BASE/current\" ]; then\n"
                + "  TARGET=\"$BASE/backups/$APP-$(date +%Y%m%d%H%M%S).tgz\"\n"
                + "  tar -C \"$BASE\" -czf \"$TARGET\" current || true\n"
                + "  echo \"backup=$TARGET\"\nelse\n  echo \"backup=skipped\"\nfi\n";
    }

    private String startScript(ProjectBackendKind kind, String base, String code, String artifactName) {
        String common = "#!/bin/sh\nset -eu\nBASE=\"" + base + "\"\nAPP=\"" + code + "\"\n"
                + "PID_FILE=\"$BASE/current/$APP.pid\"\nLOG=\"$BASE/logs/$APP.log\"\n"
                + "mkdir -p \"$BASE/current\" \"$BASE/logs\"\n"
                + "if [ -f \"$PID_FILE\" ]; then OLD=$(cat \"$PID_FILE\" 2>/dev/null || true); "
                + "if [ -n \"$OLD\" ] && kill -0 \"$OLD\" 2>/dev/null; then kill \"$OLD\"; sleep 2; fi; fi\n";
        return switch (kind) {
            case GO -> common
                    + "BIN=\"$BASE/current/" + artifactName + "\"\n"
                    + "chmod 750 \"$BIN\"\n"
                    + "nohup \"$BIN\" >> \"$LOG\" 2>&1 &\n"
                    + "echo $! > \"$PID_FILE\"\nsleep 1\nkill -0 \"$(cat \"$PID_FILE\")\"\necho started\n";
            case NODE -> common
                    + "cd \"$BASE/current\"\n"
                    + "if [ -f package-lock.json ] || [ -f package.json ]; then npm ci --omit=dev || npm install --omit=dev; fi\n"
                    + "nohup npm start >> \"$LOG\" 2>&1 &\n"
                    + "echo $! > \"$PID_FILE\"\nsleep 2\necho started\n";
            case PYTHON -> common
                    + "cd \"$BASE/current\"\n"
                    + "python3 -m venv .venv 2>/dev/null || true\n"
                    + ". .venv/bin/activate\n"
                    + "if [ -f requirements.txt ]; then pip install -r requirements.txt; fi\n"
                    + "ENTRY=${PYTHON_ENTRY:-main.py}\n"
                    + "nohup python \"$ENTRY\" >> \"$LOG\" 2>&1 &\n"
                    + "echo $! > \"$PID_FILE\"\nsleep 2\necho started\n";
            default -> throw new IllegalStateException("unsupported");
        };
    }

    private String deployScript(ProjectBackendKind kind, String base, String code, String artifactName) {
        String extract = kind == ProjectBackendKind.GO
                ? "cp -p \"$SOURCE\" \"$BASE/current/" + artifactName + "\"\n"
                : "rm -rf \"$BASE/current\"/*\nunzip -qo \"$SOURCE\" -d \"$BASE/current\"\n";
        return "#!/bin/sh\nset -eu\nBASE=\"" + base + "\"\nSOURCE=${1:-}\n"
                + "if [ -z \"$SOURCE\" ] || [ ! -f \"$SOURCE\" ]; then echo \"usage: deploy.sh <artifact>\" >&2; exit 2; fi\n"
                + "SCRIPT_DIR=$(CDPATH= cd -- \"$(dirname -- \"$0\")\" && pwd)\n"
                + "sh \"$SCRIPT_DIR/backup.sh\"\n"
                + "mkdir -p \"$BASE/current\" \"$BASE/logs\" \"$BASE/backups\"\n"
                + extract
                + "sh \"$SCRIPT_DIR/start.sh\"\n"
                + "echo deployed\n";
    }

    private String uploadAndDeploy(String hostId, String base, String code, Path artifact, Path scripts)
            throws IOException {
        String version = VERSION.format(Instant.now());
        String controlDir = base + "/.zhiwei";
        String incoming = controlDir + "/incoming/" + code + "-" + version + "-" + artifact.getFileName();
        String remoteScripts = controlDir + "/scripts";
        DeploySupport.uploadFile(sshConnector, hostId, artifact, incoming);
        DeploySupport.uploadFile(sshConnector, hostId, scripts.resolve("backup.sh"), remoteScripts + "/backup.sh");
        DeploySupport.uploadFile(sshConnector, hostId, scripts.resolve("start.sh"), remoteScripts + "/start.sh");
        DeploySupport.uploadFile(sshConnector, hostId, scripts.resolve("deploy.sh"), remoteScripts + "/deploy.sh");
        String output = DeploySupport.execRemote(sshConnector, hostId,
                "chmod 750 " + remoteScripts + "/*.sh && sh " + remoteScripts + "/deploy.sh " + incoming,
                timeoutSeconds);
        return "已自动部署（目录替换）。\n目标：" + hostId + ":" + base
                + "\n制品：" + incoming + (output.isBlank() ? "" : "\n" + output);
    }
}
