package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.ops.DeploymentTarget;
import com.zhiwei.superemployee.app.ssh.ManagedSshConnector;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
import com.zhiwei.superemployee.core.workspace.BackendWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.ProjectBackendKind;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.SFTPClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Java WAR 部署器：从 Maven target 选择 WAR，部署到 Tomcat webapps 并触发重载/重启。
 * <p>适合前后端不分离的老项目（JSP / Thymeleaf 等打进同一 WAR）。</p>
 * @author yangdb
 */
@Service
public class JavaWarDeploymentService {
    private static final DateTimeFormatter VERSION =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final ManagedSshConnector sshConnector;
    private final long timeoutSeconds;

    public JavaWarDeploymentService(
            ManagedSshConnector sshConnector,
            @Value("${super-employee.tools.deployment-timeout-seconds:180}") long timeoutSeconds) {
        this.sshConnector = sshConnector;
        this.timeoutSeconds = Math.max(30, timeoutSeconds);
    }

    public IncidentToolGateway.ToolResult deploy(IncidentTask task) {
        try {
            Map<String, Object> context = task.getContext();
            Path module = resolveModule(context);
            String code = DeploySupport.requireCode(context, module);
            boolean autoDeploy = DeploySupport.autoDeploy(context);
            List<DeploymentTarget> targets = DeploySupport.resolveTargets(context);
            if (targets.isEmpty()) {
                String path = DeploySupport.normalizeDeploymentPath(context.get("deploymentPath"));
                String war = DeploySupport.text(context.get("deploymentJar"));
                if (path != null) {
                    targets = List.of(new DeploymentTarget(path, war.isBlank() ? null : war));
                } else {
                    targets = List.of(new DeploymentTarget("/opt/tomcat/webapps", code + ".war"));
                }
            }
            List<Path> artifacts = new ArrayList<>();
            List<Path> scriptDirs = new ArrayList<>();
            List<String> outputs = new ArrayList<>();
            for (int i = 0; i < targets.size(); i++) {
                DeploymentTarget target = targets.get(i);
                String base = target.path() == null ? "/opt/tomcat/webapps" : target.path();
                String relativeWar = relativeWar(code, target.jar());
                String appName = appName(code, relativeWar);
                Path artifact = findWar(module, relativeWar);
                Path scripts = generateScripts(module, task.getId() + "-" + i, appName, base, relativeWar);
                artifacts.add(artifact);
                scriptDirs.add(scripts);
                if (autoDeploy) {
                    String hostId = DeploySupport.text(context.get("preferredHostId"));
                    if (hostId.isBlank()) {
                        return new IncidentToolGateway.ToolResult(false, "自动部署缺少目标主机");
                    }
                    outputs.add(uploadAndDeploy(hostId, base, appName, artifact, scripts));
                }
            }
            context.put("deploymentTargets", targets.stream().map(item -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("path", item.path());
                String artifact = item.jar() == null ? "" : item.jar();
                row.put("jar", artifact);
                row.put("artifact", artifact);
                return row;
            }).toList());
            context.put("deploymentPath", targets.get(0).path());
            context.put("deploymentJar", targets.get(0).jar());
            context.put("deploymentArtifact", artifacts.get(0).toString());
            context.put("deploymentScripts", scriptDirs.get(0).toString());
            if (!autoDeploy) {
                StringBuilder message = new StringBuilder("Java WAR 部署包与一键脚本已生成，自动部署未开启。\n");
                for (int i = 0; i < targets.size(); i++) {
                    message.append("目标 ").append(i + 1).append("：").append(targets.get(i).path())
                            .append(" / ").append(relativeWar(code, targets.get(i).jar())).append('\n')
                            .append("WAR：").append(artifacts.get(i)).append('\n')
                            .append("脚本目录：").append(scriptDirs.get(i)).append('\n');
                }
                message.append("包含：backup.sh、start.sh、deploy.sh（Tomcat webapps）");
                return new IncidentToolGateway.ToolResult(true, message.toString().trim());
            }
            context.put("deployedAt", Instant.now().toString());
            return new IncidentToolGateway.ToolResult(true, String.join("\n\n", outputs));
        }
        catch (Exception ex) {
            return new IncidentToolGateway.ToolResult(false, "Java WAR 部署失败：" + ex.getMessage());
        }
    }

    private Path resolveModule(Map<String, Object> context) {
        String modulePath = DeploySupport.text(context.get("backendModulePath"));
        if (!modulePath.isBlank()) {
            return Path.of(modulePath).toAbsolutePath().normalize();
        }
        return BackendWorkspacePaths.requireBackend(
                ProjectBackendKind.JAVA_WAR, DeploySupport.text(context.get("workspacePath")));
    }

    Path findWar(Path module, String relativeWar) throws IOException {
        List<Path> candidates = new ArrayList<>();
        collectWars(module, candidates, 0);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Maven target 中未找到可部署 WAR，请确认 packaging=war 并完成构建");
        }
        if (relativeWar == null || relativeWar.isBlank()) {
            return candidates.stream().max(Comparator.comparing(this::lastModified))
                    .orElseThrow(() -> new IllegalStateException("未找到 WAR"));
        }
        String want = Path.of(relativeWar).getFileName().toString().toLowerCase(Locale.ROOT);
        String stem = want.endsWith(".war") ? want.substring(0, want.length() - 4) : want;
        return candidates.stream()
                .filter(path -> path.getFileName().toString().equalsIgnoreCase(want)
                        || path.getFileName().toString().toLowerCase(Locale.ROOT).startsWith(stem + "-"))
                .max(Comparator.comparing(this::lastModified))
                .orElseGet(() -> candidates.stream().max(Comparator.comparing(this::lastModified)).orElseThrow());
    }

    private void collectWars(Path root, List<Path> out, int depth) throws IOException {
        if (root == null || depth > 6 || !Files.isDirectory(root)) {
            return;
        }
        Path target = root.resolve("target");
        if (Files.isDirectory(target)) {
            try (var files = Files.list(target)) {
                files.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".war"))
                        .forEach(out::add);
            }
        }
        try (var children = Files.list(root)) {
            for (Path child : children.filter(Files::isDirectory).toList()) {
                String name = child.getFileName().toString();
                if (name.startsWith(".") || "target".equals(name) || "node_modules".equals(name)) {
                    continue;
                }
                if (Files.isRegularFile(child.resolve("pom.xml"))) {
                    collectWars(child, out, depth + 1);
                }
            }
        }
    }

    private Path generateScripts(Path module, String incidentId, String code, String base, String relativeWar)
            throws IOException {
        Path directory = DeploySupport.scriptDir(module, incidentId);
        Files.writeString(directory.resolve("backup.sh"), backupScript(code, base, relativeWar), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("start.sh"), startScript(code, base, relativeWar), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("deploy.sh"), deployScript(code, base, relativeWar), StandardCharsets.UTF_8);
        return directory;
    }

    private String relativeWar(String code, String bound) {
        if (bound != null && !bound.isBlank()) {
            return bound;
        }
        return code + ".war";
    }

    private String appName(String code, String relativeWar) {
        String name = Path.of(relativeWar).getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".war")) {
            name = name.substring(0, name.length() - 4);
        }
        if (name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            return name;
        }
        return code;
    }

    private String uploadAndDeploy(String hostId, String base, String code, Path artifact, Path scripts)
            throws IOException {
        String version = VERSION.format(Instant.now());
        String controlDir = base + "/.zhiwei";
        String incoming = controlDir + "/incoming/" + code + "-" + version + ".war";
        String remoteScripts = controlDir + "/scripts";
        try (SSHClient ssh = sshConnector.open(hostId); SFTPClient sftp = ssh.newSFTPClient()) {
            sftp.mkdirs(controlDir + "/incoming");
            sftp.mkdirs(remoteScripts);
            sftp.mkdirs(base);
            sftp.mkdirs(controlDir + "/backups");
            sftp.put(artifact.toString(), incoming);
            sftp.put(scripts.resolve("backup.sh").toString(), remoteScripts + "/backup.sh");
            sftp.put(scripts.resolve("start.sh").toString(), remoteScripts + "/start.sh");
            sftp.put(scripts.resolve("deploy.sh").toString(), remoteScripts + "/deploy.sh");
            String command = "chmod 750 " + remoteScripts + "/*.sh && sh "
                    + remoteScripts + "/deploy.sh " + incoming;
            try (Session session = ssh.startSession()) {
                Session.Command execution = session.exec(command);
                execution.join(timeoutSeconds, TimeUnit.SECONDS);
                if (execution.getExitStatus() == null) {
                    execution.close();
                    throw new IllegalStateException("远端 WAR 部署执行超时");
                }
                String stdout = new String(execution.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String stderr = new String(execution.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                String output = DeploySupport.bounded((stdout + "\n" + stderr).trim());
                if (execution.getExitStatus() != 0) {
                    throw new IllegalStateException("远端部署脚本退出码 " + execution.getExitStatus() + "：\n" + output);
                }
                return "Java WAR 已自动部署到 Tomcat webapps。\n目标：" + hostId + ":" + base
                        + "\nWAR：" + incoming + (output.isBlank() ? "" : "\n" + output);
            }
        }
    }

    private String backupScript(String code, String base, String relativeWar) {
        return "#!/bin/sh\nset -eu\n"
                + "BASE=\"" + base + "\"\nAPP=\"" + code + "\"\nREL_WAR=\"" + relativeWar + "\"\n"
                + "CURRENT=\"$BASE/$REL_WAR\"\nBACKUPS=\"$BASE/.zhiwei/backups\"\n"
                + "CONTEXT=\"${REL_WAR%.war}\"\nEXPLODED=\"$BASE/$CONTEXT\"\n"
                + "mkdir -p \"$BACKUPS\"\n"
                + "if [ -f \"$CURRENT\" ]; then\n"
                + "  TARGET=\"$BACKUPS/$APP-$(date +%Y%m%d%H%M%S).war\"\n"
                + "  cp -p \"$CURRENT\" \"$TARGET\"\n  echo \"backup=$TARGET\"\n"
                + "elif [ -d \"$EXPLODED\" ]; then\n"
                + "  TARGET=\"$BACKUPS/$APP-$(date +%Y%m%d%H%M%S).tgz\"\n"
                + "  tar -C \"$BASE\" -czf \"$TARGET\" \"$CONTEXT\"\n  echo \"backup=$TARGET\"\n"
                + "else\n  echo \"backup=skipped(first deployment)\"\nfi\n";
    }

    /**
     * 重启策略：优先 TOMCAT_SERVICE（systemd），其次 CATALINA_HOME/BASE 脚本，最后 touch WAR 触发热部署。
     */
    private String startScript(String code, String base, String relativeWar) {
        return "#!/bin/sh\nset -eu\n"
                + "BASE=\"" + base + "\"\nAPP=\"" + code + "\"\nREL_WAR=\"" + relativeWar + "\"\n"
                + "WAR=\"$BASE/$REL_WAR\"\n"
                + "if [ ! -f \"$WAR\" ]; then echo \"missing war: $WAR\" >&2; exit 1; fi\n"
                + "if [ -n \"${TOMCAT_SERVICE:-}\" ]; then\n"
                + "  systemctl restart \"$TOMCAT_SERVICE\"\n"
                + "  echo \"restarted systemd service=$TOMCAT_SERVICE\"\n"
                + "  exit 0\nfi\n"
                + "CATALINA_BASE_DIR=$(CDPATH= cd -- \"$BASE/..\" && pwd)\n"
                + "if [ -x \"$CATALINA_BASE_DIR/bin/shutdown.sh\" ] && [ -x \"$CATALINA_BASE_DIR/bin/startup.sh\" ]; then\n"
                + "  \"$CATALINA_BASE_DIR/bin/shutdown.sh\" || true\n  sleep 3\n"
                + "  \"$CATALINA_BASE_DIR/bin/startup.sh\"\n"
                + "  echo \"restarted via catalina scripts under $CATALINA_BASE_DIR\"\n"
                + "  exit 0\nfi\n"
                + "if [ -n \"${CATALINA_HOME:-}\" ] && [ -x \"$CATALINA_HOME/bin/shutdown.sh\" ]; then\n"
                + "  \"$CATALINA_HOME/bin/shutdown.sh\" || true\n  sleep 3\n"
                + "  \"$CATALINA_HOME/bin/startup.sh\"\n"
                + "  echo \"restarted via CATALINA_HOME=$CATALINA_HOME\"\n"
                + "  exit 0\nfi\n"
                + "touch \"$WAR\"\n"
                + "echo \"touched war for Tomcat autoDeploy: $WAR\"\n";
    }

    private String deployScript(String code, String base, String relativeWar) {
        return "#!/bin/sh\nset -eu\n"
                + "BASE=\"" + base + "\"\nAPP=\"" + code + "\"\nREL_WAR=\"" + relativeWar + "\"\nSOURCE=${1:-}\n"
                + "if [ -z \"$SOURCE\" ] || [ ! -f \"$SOURCE\" ]; then echo \"usage: deploy.sh <war>\" >&2; exit 2; fi\n"
                + "SCRIPT_DIR=$(CDPATH= cd -- \"$(dirname -- \"$0\")\" && pwd)\n"
                + "WAR=\"$BASE/$REL_WAR\"\nCONTEXT=\"${REL_WAR%.war}\"\n"
                + "CONTEXT_NAME=$(basename -- \"$CONTEXT\")\n"
                + "sh \"$SCRIPT_DIR/backup.sh\"\n"
                + "mkdir -p \"$BASE\" \"$BASE/.zhiwei/backups\"\n"
                + "rm -rf \"$BASE/$CONTEXT_NAME\"\n"
                + "cp -p \"$SOURCE\" \"$WAR\"\n"
                + "sh \"$SCRIPT_DIR/start.sh\"\n"
                + "echo \"deployed=$WAR\"\n";
    }

    private FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        }
        catch (IOException ignored) {
            return FileTime.fromMillis(0);
        }
    }
}
