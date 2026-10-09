package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.ops.DeploymentPathDiscoveryService;
import com.zhiwei.superemployee.app.ops.DeploymentTarget;
import com.zhiwei.superemployee.app.ssh.ManagedSshConnector;
import com.zhiwei.superemployee.app.system.WorkspaceSettingsService;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
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
 * Java 补丁部署器：从 Maven target 选择可执行 JAR，生成备份/启动/一键部署脚本；
 * 开启自动部署时通过受管 SSH/SFTP 上传并执行。
 * @author yangdb
 */
@Service
public class JavaPatchDeploymentService {
    private static final DateTimeFormatter VERSION =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final WorkspaceSettingsService workspaces;
    private final ManagedSshConnector sshConnector;
    private final DeploymentPathDiscoveryService deploymentPathDiscovery;
    private final long timeoutSeconds;

    /**
     * 创建 Java 补丁部署器。
     *
     * @param workspaces     用于将任务工作区解析到实际 Maven 模块
     * @param sshConnector   复用资源中心主机、凭据和主机指纹的受管 SSH 连接器
     * @param deploymentPathDiscovery 自动部署前重新识别唯一实际目录
     * @param timeoutSeconds 远程部署脚本最大执行秒数，最低限制为 30 秒
     */
    public JavaPatchDeploymentService(
            WorkspaceSettingsService workspaces,
            ManagedSshConnector sshConnector,
            DeploymentPathDiscoveryService deploymentPathDiscovery,
            @Value("${super-employee.tools.deployment-timeout-seconds:180}") long timeoutSeconds) {
        this.workspaces = workspaces;
        this.sshConnector = sshConnector;
        this.deploymentPathDiscovery = deploymentPathDiscovery;
        this.timeoutSeconds = Math.max(30, timeoutSeconds);
    }

    /**
     * 为已通过 CodeX 审阅和 Maven 构建的故障任务执行部署阶段。
     * <p>
     * 始终定位 JAR 并生成 {@code backup.sh}、{@code start.sh}、{@code deploy.sh}。
     * 未开启自动部署时仅返回本地脚本目录；开启后将制品和脚本上传到受管主机，
     * 再执行包含备份、替换和启动的一键部署脚本。
     * </p>
     *
     * @param task 携带工作区、应用编码、主机和部署策略的故障任务
     * @return 不抛出业务异常的工具结果；失败原因通过 {@code success=false} 返回
     */
    public IncidentToolGateway.ToolResult deploy(IncidentTask task) {
        try {
            Map<String, Object> context = task.getContext();
            String type = text(context.get("applicationType"));
            String backendKind = text(context.get("backendKind")).toUpperCase(Locale.ROOT);
            boolean javaStack = type.isBlank() || "JAVA".equalsIgnoreCase(type)
                    || "MAVEN".equals(backendKind) || "GRADLE".equals(backendKind);
            if (!javaStack) {
                return new IncidentToolGateway.ToolResult(false, "当前补丁部署工具仅支持 Java（Maven/Gradle）应用");
            }
            Path module = resolveJavaModule(context);
            String code = requireCode(context.get("applicationCode"), module);
            boolean autoDeploy = Boolean.parseBoolean(text(context.get("autoDeploy")));
            List<DeploymentTarget> targets = resolveTargets(context);
            if (autoDeploy) {
                String hostId = text(context.get("preferredHostId"));
                if (hostId.isBlank()) {
                    return new IncidentToolGateway.ToolResult(false, "自动部署缺少目标主机");
                }
                if (targets.isEmpty()) {
                    String deploymentPath = normalizeDeploymentPath(context.get("deploymentPath"));
                    String boundJar = text(context.get("deploymentJar"));
                    if (deploymentPath != null && !boundJar.isBlank()) {
                        targets = List.of(new DeploymentTarget(deploymentPath, boundJar));
                        context.put("deploymentPathDetection", "使用任务绑定的部署路径与 JAR："
                                + deploymentPath + " / " + boundJar);
                    } else {
                        DeploymentPathDiscoveryService.DetectionResult detection = deploymentPathDiscovery.detect(
                                hostId, code, deploymentPaths(context, deploymentPath));
                        if (detection.detected()) {
                            deploymentPath = detection.deploymentPath();
                            context.put("deploymentPath", deploymentPath);
                            context.put("deploymentPathDetection", detection.message());
                            targets = List.of(new DeploymentTarget(deploymentPath, null));
                        } else {
                            return new IncidentToolGateway.ToolResult(false,
                                    "部署前路径识别失败：" + detection.message());
                        }
                    }
                } else {
                    context.put("deploymentPathDetection", "使用任务绑定的 " + targets.size() + " 个部署目标");
                }
            } else if (targets.isEmpty()) {
                String deploymentPath = normalizeDeploymentPath(context.get("deploymentPath"));
                if (deploymentPath != null) {
                    targets = List.of(new DeploymentTarget(deploymentPath, text(context.get("deploymentJar"))));
                } else {
                    targets = List.of(new DeploymentTarget("/opt/" + code, null));
                }
            }

            List<Path> artifacts = new ArrayList<>();
            List<Path> scriptDirs = new ArrayList<>();
            List<String> outputs = new ArrayList<>();
            for (int i = 0; i < targets.size(); i++) {
                DeploymentTarget target = targets.get(i);
                String base = target.path() == null ? "/opt/" + code : target.path();
                String relativeJar = relativeJar(code, target.jar());
                String appName = appName(code, relativeJar);
                Path artifact = findArtifact(module, relativeJar);
                Path scripts = generateScripts(module, task.getId() + "-" + i, appName, base, relativeJar);
                artifacts.add(artifact);
                scriptDirs.add(scripts);
                if (autoDeploy) {
                    String hostId = text(context.get("preferredHostId"));
                    outputs.add(uploadAndDeploy(hostId, base, appName, artifact, scripts));
                }
            }

            context.put("deploymentTargets", targets.stream().map(item -> {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("path", item.path());
                row.put("jar", item.jar() == null ? "" : item.jar());
                return row;
            }).toList());
            context.put("deploymentPath", targets.get(0).path());
            context.put("deploymentJar", targets.get(0).jar());
            context.put("deploymentArtifact", artifacts.get(0).toString());
            context.put("deploymentArtifacts", artifacts.stream().map(Path::toString).toList());
            context.put("deploymentScripts", scriptDirs.get(0).toString());
            context.put("deploymentScriptDirs", scriptDirs.stream().map(Path::toString).toList());

            if (!autoDeploy) {
                StringBuilder message = new StringBuilder("Java 部署包与一键脚本已生成，自动部署未开启。\n");
                for (int i = 0; i < targets.size(); i++) {
                    message.append("目标 ").append(i + 1).append("：").append(targets.get(i).path())
                            .append(" / ").append(relativeJar(code, targets.get(i).jar())).append('\n')
                            .append("JAR：").append(artifacts.get(i)).append('\n')
                            .append("脚本目录：").append(scriptDirs.get(i)).append('\n');
                }
                message.append("包含：backup.sh、start.sh、deploy.sh");
                return new IncidentToolGateway.ToolResult(true, message.toString().trim());
            }
            context.put("deployedAt", Instant.now().toString());
            return new IncidentToolGateway.ToolResult(true, String.join("\n\n", outputs));
        }
        catch (IllegalArgumentException | IllegalStateException | IOException ex) {
            return new IncidentToolGateway.ToolResult(false, "Java 补丁部署失败：" + ex.getMessage());
        }
    }

    private Path resolveJavaModule(Map<String, Object> context) {
        String modulePath = text(context.get("backendModulePath"));
        if (!modulePath.isBlank()) {
            return Path.of(modulePath).toAbsolutePath().normalize();
        }
        String workspace = text(context.get("workspacePath"));
        String backendKind = text(context.get("backendKind")).toUpperCase(Locale.ROOT);
        if ("GRADLE".equals(backendKind)) {
            return com.zhiwei.superemployee.core.workspace.BackendWorkspacePaths.requireBackend(
                    com.zhiwei.superemployee.core.workspace.ProjectBackendKind.GRADLE, workspace);
        }
        return workspaces.resolveMavenModule(workspace);
    }

    /**
     * 从故障上下文读取多部署目标，兼容仅保存单 path/jar 的旧故障。
     */
    private List<DeploymentTarget> resolveTargets(Map<String, Object> context) {
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
                    String jar = text(map.get("artifact"));
                    if (jar.isBlank()) {
                        jar = text(map.get("jar"));
                    }
                    if (path != null) {
                        targets.add(new DeploymentTarget(path, jar.isBlank() ? null : jar));
                    }
                }
            }
            if (!targets.isEmpty()) {
                return targets;
            }
        }
        String path = normalizeDeploymentPath(context.get("deploymentPath"));
        String jar = text(context.get("deploymentJar"));
        if (path != null && !jar.isBlank()) {
            return List.of(new DeploymentTarget(path, jar));
        }
        return List.of();
    }

    /**
     * 从故障上下文读取候选目录，并兼容仅保存单 deploymentPath 的旧故障。
     *
     * @param context context
     * @param legacyPath 旧版单路径
     * @return 响应数据 Map
     */
    private List<String> deploymentPaths(Map<String, Object> context, String legacyPath) {
        Object value = context.get("deploymentPaths");
        if (value instanceof List<?> list) {
            List<String> paths = list.stream().map(this::text).filter(path -> !path.isBlank()).toList();
            if (!paths.isEmpty()) {
                return paths;
            }
        }
        return legacyPath == null ? List.of() : List.of(legacyPath);
    }

    /**
     * 从 Maven 模块（含多模块子工程）选择与绑定 JAR 匹配的可部署制品；
     * 找不到匹配项时回退为最新可部署 JAR。
     */
    Path findArtifact(Path module) throws IOException {
        return findArtifact(module, null);
    }

    /**
     * 按绑定 JAR 文件名匹配本地制品；支持多模块工作区打出多个包。
     */
    Path findArtifact(Path module, String relativeJar) throws IOException {
        List<Path> candidates = new ArrayList<>();
        collectDeployableJars(module, candidates, 0);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Maven target 中未找到可部署 JAR，请先完成构建验证");
        }
        if (relativeJar == null || relativeJar.isBlank()) {
            return candidates.stream().max(Comparator.comparing(this::lastModified))
                    .orElseThrow(() -> new IllegalStateException("Maven target 中未找到可部署 JAR"));
        }
        String want = Path.of(relativeJar).getFileName().toString().toLowerCase(Locale.ROOT);
        String stem = want.endsWith(".jar") ? want.substring(0, want.length() - 4) : want;
        Path exact = candidates.stream()
                .filter(path -> path.getFileName().toString().equalsIgnoreCase(want))
                .max(Comparator.comparing(this::lastModified))
                .orElse(null);
        if (exact != null) {
            return exact;
        }
        Path prefixed = candidates.stream()
                .filter(path -> {
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.startsWith(stem + "-") || name.startsWith(stem + ".");
                })
                .max(Comparator.comparing(this::lastModified))
                .orElse(null);
        if (prefixed != null) {
            return prefixed;
        }
        return candidates.stream().max(Comparator.comparing(this::lastModified))
                .orElseThrow(() -> new IllegalStateException("Maven target 中未找到可部署 JAR"));
    }

    /**
     * 递归收集工作区及子模块 target 下的可部署 JAR（深度限制避免越界扫描）。
     */
    private void collectDeployableJars(Path root, List<Path> out, int depth) throws IOException {
        if (root == null || depth > 6 || !Files.isDirectory(root)) {
            return;
        }
        Path target = root.resolve("target");
        if (Files.isDirectory(target)) {
            try (var files = Files.list(target)) {
                files.filter(Files::isRegularFile)
                        .filter(this::isDeployableJar)
                        .forEach(out::add);
            }
        }
        Path libs = root.resolve("build").resolve("libs");
        if (Files.isDirectory(libs)) {
            try (var files = Files.list(libs)) {
                files.filter(Files::isRegularFile)
                        .filter(this::isDeployableJar)
                        .forEach(out::add);
            }
        }
        try (var children = Files.list(root)) {
            for (Path child : children.filter(Files::isDirectory).toList()) {
                String name = child.getFileName().toString();
                if (name.startsWith(".") || "target".equals(name) || "build".equals(name)
                        || "node_modules".equals(name)) {
                    continue;
                }
                if (Files.isRegularFile(child.resolve("pom.xml"))
                        || Files.isRegularFile(child.resolve("build.gradle"))
                        || Files.isRegularFile(child.resolve("build.gradle.kts"))) {
                    collectDeployableJars(child, out, depth + 1);
                }
            }
        }
    }

    private boolean isDeployableJar(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jar")
                && !name.endsWith("-sources.jar")
                && !name.endsWith("-javadoc.jar")
                && !name.startsWith("original-");
    }

    /**
     * 在模块内按故障编号生成隔离的部署脚本目录，避免并发任务相互覆盖。
     */
    Path generateScripts(Path module, String incidentId, String code, String deploymentPath) throws IOException {
        return generateScripts(module, incidentId, code, deploymentPath, relativeJar(code, null));
    }

    /**
     * 按绑定 JAR 相对路径生成备份/启动/部署脚本。
     */
    Path generateScripts(Path module, String incidentId, String code, String deploymentPath, String relativeJar)
            throws IOException {
        Path directory = module.resolve(".zhiwei-deploy").resolve(safeId(incidentId));
        Files.createDirectories(directory);
        String jar = relativeJar(code, relativeJar);
        Files.writeString(directory.resolve("backup.sh"), backupScript(code, deploymentPath, jar), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("start.sh"), startScript(code, deploymentPath, jar), StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("deploy.sh"), deployScript(code, deploymentPath, jar), StandardCharsets.UTF_8);
        return directory;
    }

    private String relativeJar(String code, String boundJar) {
        if (boundJar != null && !boundJar.isBlank()) {
            return boundJar;
        }
        return "current/" + code + ".jar";
    }

    private String appName(String code, String relativeJar) {
        String name = Path.of(relativeJar).getFileName().toString();
        if (name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            name = name.substring(0, name.length() - 4);
        }
        if (name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            return name;
        }
        return code;
    }

    /**
     * 通过 SFTP 上传版本化 JAR 和脚本，并通过 SSH 执行固定部署入口。
     *
     * @param hostId 主机 ID
     * @param base 基础路径
     * @param code 编码
     * @param artifact artifact
     * @param scripts scripts
     * @return 通过 SFTP 上传版本化 JAR 和脚本，并通过 SSH 执行固定部署入口
     */
    private String uploadAndDeploy(String hostId, String base, String code, Path artifact, Path scripts)
            throws IOException {
        String version = VERSION.format(Instant.now());
        String controlDir = base + "/.zhiwei";
        String incoming = controlDir + "/incoming/" + code + "-" + version + ".jar";
        String remoteScripts = controlDir + "/scripts";
        try (SSHClient ssh = sshConnector.open(hostId); SFTPClient sftp = ssh.newSFTPClient()) {
            sftp.mkdirs(controlDir + "/incoming");
            sftp.mkdirs(remoteScripts);
            sftp.mkdirs(base + "/current");
            sftp.mkdirs(base + "/backups");
            sftp.mkdirs(base + "/logs");
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
                    throw new IllegalStateException("远端一键部署执行超时");
                }
                String stdout = new String(execution.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String stderr = new String(execution.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                String output = bounded((stdout + "\n" + stderr).trim());
                if (execution.getExitStatus() != 0) {
                    throw new IllegalStateException("远端部署脚本退出码 " + execution.getExitStatus() + "：\n" + output);
                }
                return "Java 补丁已自动部署。\n目标：" + hostId + ":" + base
                        + "\n备份目录：" + base + "/backups"
                        + "\n部署包：" + incoming + (output.isBlank() ? "" : "\n" + output);
            }
        }
    }

    /**
     * 生成将当前 JAR 复制到时间戳备份目录的幂等脚本。
     */
    private String backupScript(String code, String base, String relativeJar) {
        return "#!/bin/sh\nset -eu\n"
                + "BASE=\"" + base + "\"\nAPP=\"" + code + "\"\nREL_JAR=\"" + relativeJar + "\"\n"
                + "CURRENT=\"$BASE/$REL_JAR\"\nBACKUPS=\"$BASE/backups\"\n"
                + "mkdir -p \"$BACKUPS\"\n"
                + "if [ -f \"$CURRENT\" ]; then\n"
                + "  TARGET=\"$BACKUPS/$APP-$(date +%Y%m%d%H%M%S).jar\"\n"
                + "  cp -p \"$CURRENT\" \"$TARGET\"\n  echo \"backup=$TARGET\"\n"
                + "else\n  echo \"backup=skipped(first deployment)\"\nfi\n";
    }

    /**
     * 生成 Java 启动脚本。脚本仅停止 PID 文件中且命令行匹配当前应用 JAR 的进程。
     */
    private String startScript(String code, String base, String relativeJar) {
        return "#!/bin/sh\nset -eu\n"
                + "BASE=\"" + base + "\"\nAPP=\"" + code + "\"\nREL_JAR=\"" + relativeJar + "\"\n"
                + "JAR=\"$BASE/$REL_JAR\"\nPID_FILE=\"$BASE/current/$APP.pid\"\nLOG=\"$BASE/logs/$APP.log\"\n"
                + "if [ ! -f \"$JAR\" ]; then echo \"missing jar: $JAR\" >&2; exit 1; fi\n"
                + "if [ -f \"$PID_FILE\" ]; then\n"
                + "  OLD_PID=$(cat \"$PID_FILE\" 2>/dev/null || true)\n"
                + "  if [ -n \"$OLD_PID\" ] && kill -0 \"$OLD_PID\" 2>/dev/null; then\n"
                + "    case \"$(ps -p \"$OLD_PID\" -o args= 2>/dev/null || true)\" in\n"
                + "      *\"$REL_JAR\"*|*\"" + Path.of(relativeJar).getFileName() + "\"*) kill \"$OLD_PID\"; sleep 3 ;;\n"
                + "      *) echo \"refuse to stop unrelated pid $OLD_PID\" >&2; exit 1 ;;\n"
                + "    esac\n  fi\nfi\n"
                + "mkdir -p \"$BASE/current\" \"$BASE/logs\"\n"
                + "nohup java ${JAVA_OPTS:-} -jar \"$JAR\" >> \"$LOG\" 2>&1 &\n"
                + "NEW_PID=$!\necho \"$NEW_PID\" > \"$PID_FILE\"\n"
                + "sleep 2\nkill -0 \"$NEW_PID\"\necho \"started pid=$NEW_PID log=$LOG\"\n";
    }

    /**
     * 生成依次调用备份、替换 JAR 和启动流程的一键部署脚本。
     */
    private String deployScript(String code, String base, String relativeJar) {
        return "#!/bin/sh\nset -eu\n"
                + "BASE=\"" + base + "\"\nAPP=\"" + code + "\"\nREL_JAR=\"" + relativeJar + "\"\nSOURCE=${1:-}\n"
                + "if [ -z \"$SOURCE\" ] || [ ! -f \"$SOURCE\" ]; then echo \"usage: deploy.sh <jar>\" >&2; exit 2; fi\n"
                + "SCRIPT_DIR=$(CDPATH= cd -- \"$(dirname -- \"$0\")\" && pwd)\n"
                + "JAR=\"$BASE/$REL_JAR\"\n"
                + "sh \"$SCRIPT_DIR/backup.sh\"\n"
                + "mkdir -p \"$(dirname -- \"$JAR\")\" \"$BASE/current\" \"$BASE/logs\" \"$BASE/backups\"\n"
                + "cp -p \"$SOURCE\" \"$JAR\"\n"
                + "sh \"$SCRIPT_DIR/start.sh\"\n"
                + "echo \"deployed=$JAR\"\n";
    }

    /**
     * 校验应用编码；兼容旧任务时可从 Maven 模块目录名推导。
     *
     * @param value 取值
     * @param module module
     * @return 校验应用编码；兼容旧任务时可从 Maven 模块目录名推导
     */
    private String requireCode(Object value, Path module) {
        String code = text(value);
        if (code.isBlank() && module != null && module.getFileName() != null) {
            code = module.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "-");
        }
        if (!code.matches("[A-Za-z0-9][A-Za-z0-9._-]{1,63}")) {
            throw new IllegalArgumentException("应用编码不符合部署安全规则");
        }
        return code;
    }

    /**
     * 规范化并限制 Linux 部署路径，拒绝根目录、越级路径和 shell 特殊字符。
     *
     * @param value 取值
     * @return 规范化并限制 Linux 部署路径，拒绝根目录、越级路径和 shell 特殊字符
     */
    private String normalizeDeploymentPath(Object value) {
        String path = text(value);
        if (path.isBlank()) return null;
        if ("/".equals(path) || !path.startsWith("/") || path.contains("..") || !path.matches("[A-Za-z0-9/_.-]+")) {
            throw new IllegalArgumentException("部署路径必须是安全的 Linux 绝对路径");
        }
        return path.replaceAll("/+$", "");
    }

    /**
     * 将故障编号转换为仅可作为本地目录名使用的安全值。
     *
     * @param value 取值
     * @return 将故障编号转换为仅可作为本地目录名使用的安全值
     */
    private String safeId(String value) {
        return value == null ? "unknown" : value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * 安全读取文件修改时间；读取失败时回退到最早时间。
     *
     * @param path 路径
     * @return 安全读取文件修改时间；读取失败时回退到最早时间
     */
    private FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        }
        catch (IOException ignored) {
            return FileTime.fromMillis(0);
        }
    }

    /**
     * 限制远程部署输出长度，避免大型日志进入故障上下文。
     *
     * @param value 取值
     * @return 限制远程部署输出长度，避免大型日志进入故障上下文
     */
    private String bounded(String value) {
        return value.length() <= 12_000 ? value : value.substring(0, 12_000) + "\n…输出已截断";
    }

    /**
     * 将可空上下文字段转换为去除首尾空白的文本。
     *
     * @param value 取值
     * @return 将可空上下文字段转换为去除首尾空白的文本
     */
    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
