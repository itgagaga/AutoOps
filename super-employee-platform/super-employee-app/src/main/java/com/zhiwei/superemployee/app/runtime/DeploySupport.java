package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.ops.DeploymentTarget;
import com.zhiwei.superemployee.app.ssh.ManagedSshConnector;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 部署公共工具：上下文读取、SSH 执行、路径规范化。 
 * @author yangdb
 */
final class DeploySupport {
    private DeploySupport() {
    }

    static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    static String requireCode(Map<String, Object> context, Path module) {
        String code = text(context.get("applicationCode"));
        if (code.isBlank() && module != null && module.getFileName() != null) {
            code = module.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "-");
        }
        if (!code.matches("[A-Za-z0-9][A-Za-z0-9._-]{1,63}")) {
            throw new IllegalArgumentException("应用编码不符合部署安全规则");
        }
        return code;
    }

    static String normalizeDeploymentPath(Object value) {
        String path = text(value);
        if (path.isBlank()) {
            return null;
        }
        if ("/".equals(path) || !path.startsWith("/") || path.contains("..") || !path.matches("[A-Za-z0-9/_.-]+")) {
            throw new IllegalArgumentException("部署路径必须是安全的 Linux 绝对路径");
        }
        return path.replaceAll("/+$", "");
    }

    static String safeId(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    static String bounded(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 12_000 ? value : value.substring(0, 12_000) + "\n…输出已截断";
    }

    static List<DeploymentTarget> resolveTargets(Map<String, Object> context) {
        Object value = context.get("deploymentTargets");
        if (value instanceof List<?> list && !list.isEmpty()) {
            List<DeploymentTarget> targets = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof DeploymentTarget target) {
                    if (target.path() != null && !target.path().isBlank()) {
                        targets.add(target);
                    }
                    continue;
                }
                if (item instanceof Map<?, ?> map) {
                    String path = normalizeDeploymentPath(map.get("path"));
                    String artifact = text(map.get("artifact"));
                    if (artifact.isBlank()) {
                        artifact = text(map.get("jar"));
                    }
                    if (path != null) {
                        targets.add(new DeploymentTarget(path, artifact.isBlank() ? null : artifact));
                    }
                }
            }
            if (!targets.isEmpty()) {
                return targets;
            }
        }
        String path = normalizeDeploymentPath(context.get("deploymentPath"));
        String jar = text(context.get("deploymentJar"));
        if (path != null) {
            return List.of(new DeploymentTarget(path, jar.isBlank() ? null : jar));
        }
        return List.of();
    }

    static boolean autoDeploy(Map<String, Object> context) {
        return Boolean.parseBoolean(text(context.get("autoDeploy")));
    }

    static String execRemote(ManagedSshConnector sshConnector, String hostId, String command, long timeoutSeconds)
            throws IOException {
        try (SSHClient ssh = sshConnector.open(hostId); Session session = ssh.startSession()) {
            Session.Command execution = session.exec(command);
            execution.join(timeoutSeconds, TimeUnit.SECONDS);
            if (execution.getExitStatus() == null) {
                execution.close();
                throw new IllegalStateException("远端命令执行超时");
            }
            String stdout = new String(execution.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(execution.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            String output = bounded((stdout + "\n" + stderr).trim());
            if (execution.getExitStatus() != 0) {
                throw new IllegalStateException("远端命令退出码 " + execution.getExitStatus() + "：\n" + output);
            }
            return output;
        }
    }

    static void uploadFile(ManagedSshConnector sshConnector, String hostId, Path local, String remote)
            throws IOException {
        try (SSHClient ssh = sshConnector.open(hostId); SFTPClient sftp = ssh.newSFTPClient()) {
            Path parent = Path.of(remote).getParent();
            if (parent != null) {
                sftp.mkdirs(parent.toString().replace('\\', '/'));
            }
            sftp.put(local.toString(), remote);
        }
    }

    static void uploadDirectory(ManagedSshConnector sshConnector, String hostId, Path localDir, String remoteDir)
            throws IOException {
        try (SSHClient ssh = sshConnector.open(hostId); SFTPClient sftp = ssh.newSFTPClient()) {
            uploadDirRecursive(sftp, localDir, remoteDir);
        }
    }

    private static void uploadDirRecursive(SFTPClient sftp, Path localDir, String remoteDir) throws IOException {
        sftp.mkdirs(remoteDir);
        try (var stream = Files.list(localDir)) {
            for (Path child : stream.toList()) {
                String remote = remoteDir + "/" + child.getFileName();
                if (Files.isDirectory(child)) {
                    uploadDirRecursive(sftp, child, remote);
                } else if (Files.isRegularFile(child)) {
                    sftp.put(child.toString(), remote);
                }
            }
        }
    }

    static Path scriptDir(Path module, String incidentId) throws IOException {
        Path directory = module.resolve(".zhiwei-deploy").resolve(safeId(incidentId));
        Files.createDirectories(directory);
        return directory;
    }

    static Path moduleFrom(IncidentTask task) {
        Map<String, Object> context = task.getContext();
        String modulePath = text(context.get("backendModulePath"));
        if (!modulePath.isBlank()) {
            return Path.of(modulePath).toAbsolutePath().normalize();
        }
        return Path.of(text(context.get("workspacePath"))).toAbsolutePath().normalize();
    }
}
