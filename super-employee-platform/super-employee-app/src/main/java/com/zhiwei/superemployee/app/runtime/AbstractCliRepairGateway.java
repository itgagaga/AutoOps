package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.system.WorkspaceSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 本地 CLI 修复网关抽象基类：Git 快照、范围校验与进程编排共用；
 * 子类负责可执行文件解析与命令行参数。
 * @author yangdb
 */
public abstract class AbstractCliRepairGateway implements LocalRepairGateway {
    private static final Logger log = LoggerFactory.getLogger(AbstractCliRepairGateway.class);
    private final Supplier<Path> workspaceSupplier;
    private final String executable;
    private final long timeoutSeconds;

    /**
     * @param workspace         YAML 配置的默认工作区路径，可为空
     * @param executable        CLI 可执行名或绝对路径
     * @param timeoutSeconds    单次修复超时秒数，最小 1
     * @param workspaceSettings 可选的界面工作区设置服务
     */
    protected AbstractCliRepairGateway(String workspace,
                                       String executable,
                                       long timeoutSeconds,
                                       ObjectProvider<WorkspaceSettingsService> workspaceSettings) {
        String yamlWorkspace = workspace == null ? "" : workspace.trim();
        WorkspaceSettingsService settings = workspaceSettings == null ? null : workspaceSettings.getIfAvailable();
        this.workspaceSupplier = () -> settings == null
                ? WorkspaceSettingsService.requireMavenProject(yamlWorkspace)
                : settings.requireWorkspace();
        this.executable = executable;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    /**
     * 单测构造：固定工作区路径，不依赖 Spring 设置服务。
     */
    protected AbstractCliRepairGateway(String workspace, String executable, long timeoutSeconds) {
        Path fixed = WorkspaceSettingsService.requireMavenProject(workspace);
        this.workspaceSupplier = () -> fixed;
        this.executable = executable;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
    }

    protected String executable() {
        return executable;
    }

    @Override
    public abstract String engineId();

    @Override
    public abstract String engineDisplayName();

    /**
     * 执行已经由 AgentScope 权限门禁批准的本地修复（使用默认工作区）。
     */
    public Result repair(String approvedPrompt, List<String> allowedFiles) {
        return repair(approvedPrompt, allowedFiles, null);
    }

    @Override
    public Result repair(String approvedPrompt, List<String> allowedFiles, String workspaceOverride) {
        if (approvedPrompt == null || approvedPrompt.isBlank() || containsForbidden(approvedPrompt)) {
            throw new IllegalArgumentException(engineDisplayName() + " 修复请求包含空内容或禁止操作");
        }
        Path workspace = (workspaceOverride != null && !workspaceOverride.isBlank())
                ? WorkspaceSettingsService.requireMavenProject(workspaceOverride)
                : workspaceSupplier.get();
        Set<String> allowed = validateAllowedFiles(workspace, allowedFiles);
        boolean workspaceScoped = allowed.isEmpty();
        RepairSnapshot snapshot;
        long started = System.nanoTime();
        try {
            snapshot = createSnapshot(workspace);
            log.info("{} 修复开始 repairId={} workspace={} scope={}",
                    engineDisplayName(), snapshot.id(), workspace, workspaceScoped ? "WORKSPACE" : "FILES:" + allowed.size());
            String scopedPrompt = approvedPrompt
                    + (workspaceScoped
                    ? "\n允许修改当前任务配置的代码工作区内文件，但不得越出该工作区。"
                    : "\n只允许修改以下相对路径文件：" + String.join(", ", allowed))
                    + "\n允许按需联网查询资料或下载构建依赖；禁止 Git 提交、推送和部署。"
                    + workspaceFileIndex(workspace);
            Path resolvedExecutable = resolveExecutable();
            preparePromptDelivery(workspace, scopedPrompt);
            List<String> command = buildProcessCommand(resolvedExecutable, workspace);
            log.info("{} 已解析 executable={} workspace={}", engineDisplayName(), resolvedExecutable, workspace);
            Path processOutput = Files.createTempFile("zhiwei-repair-", ".log");
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(workspace.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(processOutput.toFile());
            configureProcessEnvironment(processBuilder);
            Process process = processBuilder.start();
            IOException stdinFailure = null;
            try {
                deliverPrompt(process, scopedPrompt);
            }
            catch (IOException ex) {
                stdinFailure = ex;
            }
            try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                waitForProcessExit(process, 5);
                String timeoutOutput = readProcessOutputQuietly(processOutput);
                deleteQuietly(processOutput);
                log.error("{} 修复超时 repairId={} timeoutSeconds={} durationMs={}",
                        engineDisplayName(), snapshot.id(), timeoutSeconds, elapsedMillis(started));
                String timeoutMessage = engineDisplayName() + " 执行超时";
                if (!timeoutOutput.isBlank()) {
                    timeoutMessage = timeoutMessage + "\n" + truncateOutput(timeoutOutput);
                }
                return new Result(false, timeoutMessage, "", List.of(), true, snapshot.id());
            }
            String message = readProcessOutputQuietly(processOutput);
            deleteQuietly(processOutput);
            message = truncateOutput(message);
            if (stdinFailure != null) {
                message = engineDisplayName() + " 在接收任务前提前退出（exitCode=" + process.exitValue() + "）："
                        + stdinFailure.getMessage() + "\n" + message;
            }
            // 先清理临时提示文件，再对比快照，避免把投递文件误计为代码修复。
            cleanupPromptDelivery(workspace);
            RepairChanges changes = finishSnapshot(snapshot);
            String diff = changes.comparison();
            Set<String> changedFiles = changes.files();
            boolean withinScope = workspaceScoped || allowed.containsAll(changedFiles);
            if (!withinScope) {
                message = message + "\n检测到超出批准范围的文件：" + difference(changedFiles, allowed);
            }
            boolean hasCodeChanges = !changedFiles.isEmpty();
            // 嵌套 Git/已修复场景：CLI 成功且无新增差异时按幂等成功，避免误报失败。
            boolean idempotentOk = process.exitValue() == 0 && withinScope && !hasCodeChanges && stdinFailure == null;
            if (idempotentOk) {
                message = (message == null || message.isBlank() ? "" : message + "\n")
                        + engineDisplayName() + " 退出成功且无新增源码差异（可能目标代码已是修复后状态）";
            }
            boolean success = process.exitValue() == 0 && withinScope && stdinFailure == null
                    && (hasCodeChanges || idempotentOk);
            if (success) {
                log.info("{} 修复完成 repairId={} exitCode={} changedFiles={} idempotent={} durationMs={}",
                        engineDisplayName(), snapshot.id(), process.exitValue(), changedFiles.size(),
                        idempotentOk, elapsedMillis(started));
            }
            else {
                log.error("{} 修复失败 repairId={} exitCode={} withinScope={} changedFiles={} durationMs={}",
                        engineDisplayName(), snapshot.id(), process.exitValue(), withinScope, changedFiles.size(),
                        elapsedMillis(started));
            }
            return new Result(success, message, diff,
                    List.copyOf(changedFiles), withinScope, snapshot.id());
            } finally {
                cleanupPromptDelivery(workspace);
                deleteQuietly(processOutput);
            }
        } catch (IOException ex) {
            log.error("{} 修复启动或快照失败 workspace={} durationMs={}",
                    engineDisplayName(), workspace, elapsedMillis(started), ex);
            return new Result(false, engineDisplayName() + " 无法启动：" + ex.getMessage(), "", List.of(), true, null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("{} 修复被中断 workspace={} durationMs={}", engineDisplayName(), workspace, elapsedMillis(started), ex);
            return new Result(false, engineDisplayName() + " 执行被中断", "", List.of(), true, null);
        }
    }

    private static String truncateOutput(String message) {
        if (message == null) {
            return "";
        }
        if (message.length() <= 128_000) {
            return message;
        }
        return message.substring(0, 128_000) + "\n…输出已截断";
    }

    private static String readProcessOutputQuietly(Path processOutput) {
        try {
            if (processOutput != null && Files.exists(processOutput)) {
                return Files.readString(processOutput, StandardCharsets.UTF_8);
            }
        }
        catch (IOException ignored) {
            // Windows 下进程仍占用日志文件时，忽略读取失败
        }
        return "";
    }

    private static void waitForProcessExit(Process process, int seconds) {
        try {
            process.waitFor(Math.max(1, seconds), TimeUnit.SECONDS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 删除临时文件；Windows 上子进程句柄未释放时可能失败，重试后仍失败则忽略。
     */
    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                if (!Files.exists(path) || Files.deleteIfExists(path)) {
                    return;
                }
            }
            catch (IOException ignored) {
                // 文件被占用时稍后重试
            }
            try {
                Thread.sleep(100L * (attempt + 1));
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * 计算自给定纳秒起点至今的耗时毫秒数。
     *
     * @param started {@link System#nanoTime()} 起点
     * @return 已流逝毫秒
     */
    private long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    /**
     * 审阅并接受或撤销一次 CodeX 修复快照。
     *
     * @param workspacePath 任务工作区路径
     * @param repairId      修复快照 UUID
     * @param accept        {@code true} 接受保留变更；{@code false} 回滚到修复前
     * @return 审阅结果（是否成功、决策状态、说明）
     * @throws IllegalArgumentException 快照不存在或参数非法
     * @throws IllegalStateException    回滚过程中路径越界或 IO 失败
     */
    @Override
    public ReviewResult review(String workspacePath, String repairId, boolean accept) throws IOException {
        Path workspace = WorkspaceSettingsService.requireMavenProject(workspacePath);
        if (repairId == null || !repairId.matches("[a-f0-9-]{36}")) {
            throw new IllegalArgumentException("修复快照不存在或参数无效");
        }
        List<Path> gitRoots = resolveRepairGitRoots(workspace);
        List<Path> snapshotDirs = new ArrayList<>();
        for (Path gitRoot : gitRoots) {
            Path snapshot = gitRoot.resolve(".git").resolve("zhiwei-repairs").resolve(repairId).normalize();
            Path gitMeta = gitRoot.resolve(".git").normalize();
            if (snapshot.startsWith(gitMeta) && Files.isDirectory(snapshot)) {
                snapshotDirs.add(snapshot);
            }
        }
        if (snapshotDirs.isEmpty()) {
            throw new IllegalArgumentException("修复快照不存在");
        }
        try {
            String decision = "PENDING";
            for (Path snapshot : snapshotDirs) {
                Properties metadata = loadProperties(snapshot.resolve("manifest.properties"));
                decision = metadata.getProperty("decision", "PENDING");
                if (!"PENDING".equals(decision)) {
                    return new ReviewResult(false, decision, "该修复结果已经处理，不能重复操作");
                }
            }
            if (accept) {
                for (Path snapshot : snapshotDirs) {
                    Properties metadata = loadProperties(snapshot.resolve("manifest.properties"));
                    metadata.setProperty("decision", "ACCEPTED");
                    storeProperties(snapshot.resolve("manifest.properties"), metadata);
                }
                return new ReviewResult(true, "ACCEPTED", "已接受修复，代码变更保留在当前运维分支");
            }
            for (Path snapshot : snapshotDirs) {
                revertSnapshot(snapshot);
            }
            return new ReviewResult(true, "REVERTED", "已撤销本次本地修复，并恢复执行前的工作区内容");
        }
        catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("处理修复结果失败：" + ex.getMessage(), ex);
        }
    }

    private void revertSnapshot(Path snapshot) throws IOException, InterruptedException {
        Properties metadata = loadProperties(snapshot.resolve("manifest.properties"));
        Path repo = Path.of(metadata.getProperty("gitRoot", snapshot.getParent().getParent().getParent().toString()));
        String headSha = metadata.getProperty("headSha", "");
        for (String encoded : metadata.stringPropertyNames().stream()
                .filter(key -> key.startsWith("affected."))
                .map(key -> key.substring("affected.".length())).toList()) {
            String storedPath = metadata.getProperty("path." + encoded);
            String relative = storedPath == null ? decode(encoded) : decode(storedPath);
            Path target = repo.resolve(relative).normalize();
            if (!target.startsWith(repo)) {
                throw new IllegalStateException("快照包含越界路径");
            }
            String previous = metadata.getProperty("pre." + encoded, "CLEAN");
            Path backup = snapshot.resolve("before").resolve(encoded);
            if (Files.isRegularFile(backup)) {
                Files.createDirectories(target.getParent());
                Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
            }
            else if ("MISSING".equals(previous)) {
                Files.deleteIfExists(target);
            }
            else if ("TRACKED".equals(metadata.getProperty("affected." + encoded))) {
                if (headSha != null && !headSha.isBlank()) {
                    runGit(repo, "checkout", headSha, "--", relative);
                }
                else {
                    runGit(repo, "restore", "--worktree", "--", relative);
                }
            }
            else {
                Files.deleteIfExists(target);
            }
        }
        metadata.setProperty("decision", "REVERTED");
        storeProperties(snapshot.resolve("manifest.properties"), metadata);
    }

    /**
     * 组装将提示作为命令行参数传入的命令（单测/兼容用）；默认按子类 stdin 参数拼装。
     */
    List<String> buildCommand(String scopedPrompt) {
        List<String> command = new java.util.ArrayList<>();
        command.add(executable());
        List<String> args = stdinPromptArgs();
        for (String arg : args) {
            if ("-".equals(arg)) {
                command.add(scopedPrompt);
            } else {
                command.add(arg);
            }
        }
        return command;
    }

    /**
     * 解析 CLI 可执行文件。Windows 的 ProcessBuilder 不会自动按 PATHEXT 展开，因此显式扫描 PATH。
     */
    protected Path resolveExecutable() throws IOException {
        String configured = executable() == null ? "" : executable().trim();
        if (configured.isBlank()) {
            configured = defaultExecutableName();
        }
        if (configured.contains("/") || configured.contains("\\") || Path.of(configured).isAbsolute()) {
            Path path = Path.of(configured).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) return path;
            throw new IOException("配置的 " + engineDisplayName() + " 不存在：" + path);
        }
        String pathValue = System.getenv("PATH");
        if (pathValue != null) {
            List<String> suffixes = isWindows()
                    ? List.of(".exe", ".cmd", ".bat", "")
                    : List.of("");
            for (String suffix : suffixes) {
                for (String directory : pathValue.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                    if (directory == null || directory.isBlank()) continue;
                    Path candidate = Path.of(directory.trim()).resolve(configured + suffix).normalize();
                    if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
                }
            }
        }
        Path official = officialInstallExecutable(configured);
        if (official != null && Files.isRegularFile(official)) {
            return official.toAbsolutePath();
        }
        throw new IOException("未找到 " + engineDisplayName() + " 可执行文件。请配置对应 executable，"
                + "或在环境向导中完成安装，或将可执行文件所在目录加入应用进程 PATH");
    }

    /** 默认可执行名，如 {@code codex} / {@code agent}。 */
    protected abstract String defaultExecutableName();

    /**
     * 官方安装目录回退查找；找不到时返回 {@code null}。
     */
    protected Path officialInstallExecutable(String configuredName) {
        return null;
    }

    /**
     * 使用 stdin 传递 UTF-8 提示，避免 Windows 命令行转义和长度限制。
     */
    protected List<String> buildProcessCommand(Path resolvedExecutable, Path workspace) {
        List<String> args = processArgs(workspace);
        String lower = resolvedExecutable.getFileName().toString().toLowerCase();
        List<String> command = new java.util.ArrayList<>();
        if (isWindows() && (lower.endsWith(".cmd") || lower.endsWith(".bat"))) {
            command.add(System.getenv().getOrDefault("ComSpec", "C:\\Windows\\System32\\cmd.exe"));
            command.add("/d");
            command.add("/s");
            command.add("/c");
            StringBuilder joined = new StringBuilder("call \"").append(resolvedExecutable).append('"');
            for (String arg : args) {
                joined.append(' ').append(quoteProcessArg(arg));
            }
            command.add(joined.toString());
            return command;
        }
        command.add(resolvedExecutable.toString());
        command.addAll(args);
        return command;
    }

    private static String quoteProcessArg(String arg) {
        if (arg == null) {
            return "\"\"";
        }
        if (arg.isEmpty() || arg.chars().anyMatch(ch -> Character.isWhitespace(ch) || ch == '"')) {
            return '"' + arg.replace("\"", "\\\"") + '"';
        }
        return arg;
    }

    /** CLI 参数（以 {@code -} 结尾表示从 stdin 读提示）。 */
    protected abstract List<String> stdinPromptArgs();

    /**
     * 组装实际进程参数；子类可注入 {@code --workspace} 等依赖工作区的选项。
     */
    protected List<String> processArgs(Path workspace) {
        return stdinPromptArgs();
    }

    /** 子类可注入 API Key 等环境变量。 */
    protected void configureProcessEnvironment(ProcessBuilder processBuilder) {
        // 默认不改动环境
    }

    /**
     * 进程启动前准备提示投递（例如写入临时任务文件）。默认无操作。
     */
    protected void preparePromptDelivery(Path workspace, String scopedPrompt) throws IOException {
        // 默认通过 stdin 投递
    }

    /**
     * 向已启动进程投递提示；默认写入 stdin。
     */
    protected void deliverPrompt(Process process, String scopedPrompt) throws IOException {
        try (java.io.OutputStream input = process.getOutputStream()) {
            input.write(scopedPrompt.getBytes(StandardCharsets.UTF_8));
            input.flush();
        }
    }

    /**
     * 进程结束后清理临时提示文件等。
     */
    protected void cleanupPromptDelivery(Path workspace) {
        // 默认无清理
    }

    /**
     * 是否为修复投递用的内部临时路径（不应计入代码变更）。
     */
    protected boolean isInternalDeliveryPath(String relative) {
        if (relative == null || relative.isBlank()) {
            return false;
        }
        String normalized = relative.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        return normalized.equals(".zhiwei/repair-prompt.txt")
                || normalized.startsWith(".zhiwei/");
    }

    /**
     * 生成工作区真实源码索引，供 CodeX 按类名/文件名定位文件。
     * 包含 Git 已跟踪及未跟踪文件，排除构建产物，并限制长度避免提示膨胀。
     *
     * @param workspace 工作区路径
     * @return 追加到提示末尾的索引文本
     * @throws IOException          Git 列举失败
     * @throws InterruptedException 等待被中断
     */
    private String workspaceFileIndex(Path workspace) throws IOException, InterruptedException {
        String output = runGit(workspace, "-c", "core.quotePath=false",
                "ls-files", "-co", "--exclude-standard", "-z");
        StringBuilder index = new StringBuilder(
                "\n\n以下是从工作区递归扫描得到的真实文件索引。补丁中的路径仅供参考；"
                        + "必须先按文件名、类名在索引中定位实际相对路径，不得假设文件位于 src/main/java 根目录：\n");
        int count = 0;
        for (String path : output.split("\u0000", -1)) {
            String normalized = path.replace('\\', '/');
            if (normalized.isBlank() || !isRelevantSourcePath(normalized)) continue;
            index.append("- ").append(normalized).append('\n');
            count++;
            if (count >= 2000 || index.length() >= 60_000) break;
        }
        return index.append("共列出 ").append(count).append(" 个相关文件。\n").toString();
    }

    /**
     * 判断相对路径是否为应纳入源码索引的相关文件（源码/配置/文档等）。
     *
     * @param path 仓库内相对路径（正斜杠）
     * @return 匹配扩展名或关键文件名时返回 {@code true}
     */
    private boolean isRelevantSourcePath(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".xml")
                || lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".properties")
                || lower.endsWith(".vue") || lower.endsWith(".js") || lower.endsWith(".ts")
                || lower.endsWith(".sql") || lower.endsWith(".md")
                || lower.endsWith("pom.xml") || lower.endsWith("package.json");
    }

    /**
     * 判断当前进程是否运行在 Windows 上。
     *
     * @return 操作系统名包含 {@code win} 时返回 {@code true}
     */
    protected static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    /**
     * 检测提示文本是否包含禁止的提交/推送/部署类操作关键词。
     *
     * @param value 已批准的修复提示
     * @return 命中禁止词时返回 {@code true}
     */
    private boolean containsForbidden(String value) {
        String lower = value.toLowerCase();
        return lower.contains("git commit") || lower.contains("git push") || lower.contains("deploy")
                || lower.contains("部署到生产");
    }

    /**
     * 校验允许修改的相对路径均落在工作区内；空列表表示整个工作区范围。
     *
     * @param workspace Maven 工作区根路径
     * @param values    允许修改的相对路径列表，可为 {@code null}
     * @return 规范化后的相对路径集合；空表示工作区范围
     * @throws IllegalArgumentException 路径越界或为绝对路径时
     */
    private Set<String> validateAllowedFiles(Path workspace, List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        Set<String> result = new HashSet<>();
        for (String value : values) {
            Path relative = Path.of(value).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                throw new IllegalArgumentException(engineDisplayName() + " 允许文件不能超出工作区");
            }
            Path resolved = workspace.resolve(relative).normalize();
            if (!resolved.startsWith(workspace)) {
                throw new IllegalArgumentException(engineDisplayName() + " 允许文件不能超出工作区");
            }
            result.add(relative.toString().replace('\\', '/'));
        }
        return result;
    }

    /**
     * 在仓库 {@code .git/zhiwei-repairs} 下创建修复前快照（脏文件备份与清单）。
     * <p>前后端同仓时，父目录可能自有 Git，而真实 Maven 源码在嵌套子仓（如 {@code 服务后端}）。
     * 此时对所有嵌套代码仓分别建快照，避免父仓 status 看不到子仓改动。</p>
     */
    private RepairSnapshot createSnapshot(Path workspace) throws IOException, InterruptedException {
        List<Path> gitRoots = resolveRepairGitRoots(workspace);
        if (gitRoots.isEmpty()) {
            throw new IllegalStateException(engineDisplayName() + " 工作区未找到 Git 仓库");
        }
        String id = UUID.randomUUID().toString();
        List<RepoSnapshot> repos = new ArrayList<>();
        for (Path repo : gitRoots) {
            Path root = repo.resolve(".git").resolve("zhiwei-repairs").resolve(id);
            Files.createDirectories(root.resolve("before"));
            // 尚未首次提交时 HEAD 不存在（unborn）；快照仍可靠工作区文件备份完成回滚。
            String headSha = resolveHeadSha(repo);
            Properties metadata = new Properties();
            metadata.setProperty("decision", "PENDING");
            metadata.setProperty("headSha", headSha);
            metadata.setProperty("gitRoot", repo.toAbsolutePath().normalize().toString());
            metadata.setProperty("workspaceRoot", workspace.toAbsolutePath().normalize().toString());
            Map<String, String> dirty = status(repo);
            for (String relative : dirty.keySet()) {
                Path file = repo.resolve(relative).normalize();
                String encoded = snapshotKey(relative);
                metadata.setProperty("path." + encoded, encode(relative));
                if (Files.isRegularFile(file)) {
                    Files.copy(file, root.resolve("before").resolve(encoded));
                    metadata.setProperty("pre." + encoded, "EXISTS");
                }
                else {
                    metadata.setProperty("pre." + encoded, "MISSING");
                }
            }
            storeProperties(root.resolve("manifest.properties"), metadata);
            repos.add(new RepoSnapshot(repo, root, dirty, headSha));
            log.info("{} 快照已创建 repairId={} gitRoot={} dirtyFiles={}",
                    engineDisplayName(), id, repo, dirty.size());
        }
        return new RepairSnapshot(id, workspace, List.copyOf(repos));
    }

    /**
     * 解析修复应跟踪的 Git 根：优先工作区内含 pom 的嵌套仓库，避免父仓掩盖子仓变更。
     */
    List<Path> resolveRepairGitRoots(Path workspace) throws IOException {
        Path normalized = workspace.toAbsolutePath().normalize();
        LinkedHashSet<Path> nested = new LinkedHashSet<>();
        ArrayList<Path> frontier = new ArrayList<>();
        frontier.add(normalized);
        for (int depth = 0; depth < 4 && !frontier.isEmpty(); depth++) {
            ArrayList<Path> next = new ArrayList<>();
            for (Path dir : frontier) {
                try (var stream = Files.newDirectoryStream(dir)) {
                    for (Path child : stream) {
                        if (!Files.isDirectory(child) || Files.isSymbolicLink(child)) {
                            continue;
                        }
                        String name = child.getFileName() == null ? "" : child.getFileName().toString();
                        if (name.startsWith(".") || name.equalsIgnoreCase("node_modules")
                                || name.equalsIgnoreCase("target") || name.equalsIgnoreCase("dist")
                                || name.equalsIgnoreCase("build") || name.equalsIgnoreCase("out")) {
                            continue;
                        }
                        if (LocalGitRepositoryGateway.isValidGitMetadata(child.resolve(".git"))
                                && com.zhiwei.superemployee.core.workspace.MavenWorkspacePaths.containsMaven(child)) {
                            nested.add(child.toAbsolutePath().normalize());
                        }
                        next.add(child);
                    }
                }
                catch (IOException ignored) {
                    // 跳过不可读目录
                }
            }
            frontier = next;
        }
        if (!nested.isEmpty()) {
            return List.copyOf(nested);
        }
        Path outer = LocalGitRepositoryGateway.findGitRoot(normalized);
        return outer == null ? List.of() : List.of(outer);
    }

    /**
     * 对比修复前后工作区，更新快照清单并生成变更对比文本。
     * <p>除 porcelain 脏文件外，还会对比修复前 HEAD，避免 CLI 中途 {@code git commit}
     * 后工作区变干净导致漏检真实源码变更。</p>
     */
    private RepairChanges finishSnapshot(RepairSnapshot snapshot) throws IOException, InterruptedException {
        Set<String> affected = new LinkedHashSet<>();
        StringBuilder comparison = new StringBuilder();
        for (RepoSnapshot repo : snapshot.repos()) {
            RepairChanges part = finishRepoSnapshot(snapshot.workspace(), repo);
            affected.addAll(part.files());
            if (!part.comparison().isBlank()) {
                if (!comparison.isEmpty()) {
                    comparison.append('\n');
                }
                comparison.append(part.comparison());
            }
        }
        return new RepairChanges(affected, comparison.toString());
    }

    private RepairChanges finishRepoSnapshot(Path workspace, RepoSnapshot repo)
            throws IOException, InterruptedException {
        Properties metadata = loadProperties(repo.root().resolve("manifest.properties"));
        Map<String, String> after = status(repo.gitRoot());
        Set<String> candidates = new HashSet<>(repo.before().keySet());
        candidates.addAll(after.keySet());
        candidates.addAll(pathsChangedSince(repo.gitRoot(), repo.headSha()));
        Set<String> affected = new LinkedHashSet<>();
        StringBuilder comparison = new StringBuilder();
        String prefix = relativizePrefix(workspace, repo.gitRoot());
        for (String relative : candidates) {
            if (isInternalDeliveryPath(relative)) {
                continue;
            }
            String encoded = snapshotKey(relative);
            metadata.setProperty("path." + encoded, encode(relative));
            Path before = repo.root().resolve("before").resolve(encoded);
            Path current = repo.gitRoot().resolve(relative).normalize();
            if (!Files.exists(before)) {
                if (materializeRevisionVersion(repo.gitRoot(), repo.headSha(), relative, before)) {
                    metadata.setProperty("pre." + encoded, "EXISTS");
                }
                else if (!repo.before().containsKey(relative) && after.containsKey(relative)) {
                    metadata.setProperty("pre." + encoded, "MISSING");
                }
            }
            boolean changed;
            if (Files.isRegularFile(before) && Files.isRegularFile(current)) {
                changed = Files.mismatch(before, current) != -1;
            }
            else if (repo.before().containsKey(relative)
                    && !Files.isRegularFile(before)) {
                changed = !java.util.Objects.equals(repo.before().get(relative), after.get(relative));
            }
            else {
                changed = Files.exists(before) != Files.exists(current)
                        || (!repo.before().containsKey(relative) && after.containsKey(relative))
                        || revisionPathChanged(repo.gitRoot(), repo.headSha(), relative);
            }
            if (!changed) continue;
            String displayPath = prefix.isEmpty() ? relative : prefix + "/" + relative;
            affected.add(displayPath);
            boolean tracked = isTracked(repo.gitRoot(), relative);
            metadata.setProperty("affected." + encoded, tracked ? "TRACKED" : "UNTRACKED");
            appendComparison(comparison, displayPath, before, current);
        }
        storeProperties(repo.root().resolve("manifest.properties"), metadata);
        return new RepairChanges(affected, comparison.toString());
    }

    private static String relativizePrefix(Path workspace, Path gitRoot) {
        try {
            Path relative = workspace.toAbsolutePath().normalize()
                    .relativize(gitRoot.toAbsolutePath().normalize());
            if (relative.getNameCount() == 0 || relative.toString().isBlank() || relative.startsWith("..")) {
                return "";
            }
            return relative.toString().replace('\\', '/');
        }
        catch (IllegalArgumentException ex) {
            return "";
        }
    }

    /**
     * 列出相对修复前 HEAD 已发生变化的路径（含工作区未提交，以及修复过程中已提交的文件）。
     */
    private Set<String> pathsChangedSince(Path workspace, String headSha)
            throws IOException, InterruptedException {
        if (headSha == null || headSha.isBlank()) {
            return Set.of();
        }
        Set<String> paths = new LinkedHashSet<>();
        paths.addAll(parseNameOnly(runGit(workspace, "-c", "core.quotePath=false",
                "diff", "--name-only", "-z", headSha)));
        paths.addAll(parseNameOnly(runGit(workspace, "-c", "core.quotePath=false",
                "diff", "--name-only", "-z", headSha, "HEAD")));
        return paths;
    }

    /**
     * 解析 {@code git diff --name-only -z} 输出。
     * <p>忽略混入的 warning/error 文本（例如 Windows 上 LF→CRLF 提示），避免被当成路径。</p>
     */
    static Set<String> parseNameOnly(String output) {
        Set<String> paths = new LinkedHashSet<>();
        if (output == null || output.isBlank()) {
            return paths;
        }
        for (String path : output.split("\u0000", -1)) {
            String normalized = path.replace('\\', '/').trim();
            if (isPlausibleRepoRelativePath(normalized)) {
                paths.add(normalized);
            }
        }
        return paths;
    }

    /**
     * Git 相对路径的基本合法性：排除 warning 文本与 Windows 非法字符。
     */
    static boolean isPlausibleRepoRelativePath(String path) {
        if (path == null || path.isBlank() || path.length() > 512) {
            return false;
        }
        if (path.indexOf('\n') >= 0 || path.indexOf('\r') >= 0) {
            return false;
        }
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("warning:") || lower.startsWith("error:") || lower.startsWith("fatal:")
                || lower.startsWith("hint:")) {
            return false;
        }
        // 仓库内相对路径不应含盘符冒号；LF/CRLF warning 文本含冒号。
        if (path.indexOf(':') >= 0) {
            return false;
        }
        return true;
    }

    /**
     * 判断路径在修复前 HEAD 与当前 HEAD 之间是否有提交级变更（工作区已干净时的兜底）。
     */
    private boolean revisionPathChanged(Path workspace, String headSha, String relative)
            throws IOException, InterruptedException {
        if (headSha == null || headSha.isBlank() || relative == null || relative.isBlank()) {
            return false;
        }
        String output = runGit(workspace, "-c", "core.quotePath=false",
                "diff", "--name-only", "-z", headSha, "HEAD", "--", relative);
        return !parseNameOnly(output).isEmpty();
    }

    /**
     * 将指定修订中已跟踪文件内容物化为快照备份，供审阅页展示修复前版本。
     *
     * @param workspace   Git 工作区根
     * @param revision    修复前 HEAD 等修订
     * @param relative    相对仓库根的路径
     * @param destination 备份目标文件
     * @return {@code true} 表示成功写出修订内容
     * @throws IOException          进程或文件 IO 失败
     * @throws InterruptedException 等待进程被中断
     */
    private boolean materializeRevisionVersion(Path workspace, String revision, String relative, Path destination)
            throws IOException, InterruptedException {
        if (revision == null || revision.isBlank()) {
            return false;
        }
        Files.createDirectories(destination.getParent());
        Process process = new ProcessBuilder("git", "show", revision + ":" + relative)
                .directory(workspace.toFile())
                .redirectErrorStream(true)
                .redirectOutput(destination.toFile())
                .start();
        boolean completed = process.waitFor(20, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            Files.deleteIfExists(destination);
            return false;
        }
        if (process.exitValue() != 0) {
            Files.deleteIfExists(destination);
            return false;
        }
        return true;
    }

    /**
     * 读取仓库 porcelain 状态（含未跟踪），映射为相对路径 → 状态码。
     *
     * @param workspace Git 工作区根
     * @return 有序的脏路径映射
     * @throws IOException          Git 命令失败
     * @throws InterruptedException 等待被中断
     */
    private Map<String, String> status(Path workspace) throws IOException, InterruptedException {
        String output = runGit(workspace, "-c", "core.quotePath=false",
                "status", "--porcelain=v1", "-z", "--untracked-files=all");
        return parsePorcelainStatus(output);
    }

    /**
     * 解析 Git porcelain v1 的 NUL 分隔输出。使用 -z 后中文、空格及引号均为原始路径，
     * 不会再把 Git 的双引号/八进制转义误交给 java.nio.file.Path。
     *
     * @param output {@code git status --porcelain=v1 -z} 的原始输出
     * @return 相对路径 → 两位状态码的有序映射
     */
    static Map<String, String> parsePorcelainStatus(String output) {
        Map<String, String> result = new LinkedHashMap<>();
        String[] records = output == null ? new String[0] : output.split("\u0000", -1);
        for (int i = 0; i < records.length; i++) {
            String line = records[i];
            if (line.length() < 4) {
                continue;
            }
            String status = line.substring(0, 2);
            String path = line.substring(3).replace('\\', '/');
            result.put(path, line.substring(0, 2));
            if ((status.indexOf('R') >= 0 || status.indexOf('C') >= 0) && i + 1 < records.length) {
                i++; // -z 重命名/复制记录的下一字段是旧路径；快照只关心新路径。
            }
        }
        return result;
    }

    /**
     * 判断相对路径是否为 Git 已跟踪文件。
     *
     * @param workspace Git 工作区根
     * @param relative  相对路径
     * @return 已跟踪时返回 {@code true}
     * @throws IOException          进程 IO 失败
     * @throws InterruptedException 等待被中断
     */
    private boolean isTracked(Path workspace, String relative) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "ls-files", "--error-unmatch", "--", relative)
                .directory(workspace.toFile()).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        return process.waitFor(20, TimeUnit.SECONDS) && process.exitValue() == 0;
    }

    /**
     * 解析当前 HEAD；仓库尚无提交时返回空串（允许仅靠工作区文件备份做快照）。
     */
    private String resolveHeadSha(Path repo) throws IOException, InterruptedException {
        List<String> command = List.of("git", "rev-parse", "HEAD");
        Process process = new ProcessBuilder(command)
                .directory(repo.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        byte[] stdout;
        try (InputStream input = process.getInputStream()) {
            stdout = input.readNBytes(128_000);
        }
        boolean finished = process.waitFor(20, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Git 快照命令超时：git rev-parse HEAD");
        }
        if (process.exitValue() == 0) {
            return new String(stdout, StandardCharsets.UTF_8).trim();
        }
        log.warn("{} 仓库尚无可用 HEAD（可能未首次提交），快照改用工作区文件备份 gitRoot={}",
                engineDisplayName(), repo);
        return "";
    }

    /**
     * 在指定工作区执行 Git 子命令并返回合并后的标准输出（限长）。
     *
     * @param workspace Git 工作区根（作为进程工作目录）
     * @param args      传给 {@code git} 的参数（不含 {@code git} 本身）
     * @return 命令标准输出文本
     * @throws IOException           启动或读取失败
     * @throws InterruptedException  等待被中断
     * @throws IllegalStateException 超时或非零退出
     */
    private String runGit(Path workspace, String... args) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        // stderr 不并入 stdout：Windows 上 autocrlf 的 LF/CRLF warning 会污染路径解析。
        Process process = new ProcessBuilder(command)
                .directory(workspace.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        byte[] stdout;
        try (InputStream input = process.getInputStream()) {
            stdout = input.readNBytes(128_000);
        }
        if (!process.waitFor(20, TimeUnit.SECONDS) || process.exitValue() != 0) {
            String detail = new String(stdout, StandardCharsets.UTF_8).trim();
            throw new IllegalStateException("Git 快照命令失败：git " + String.join(" ", args)
                    + (detail.isBlank() ? "" : " → " + detail));
        }
        return new String(stdout, StandardCharsets.UTF_8);
    }

    /**
     * 将单个文件的修复前/后文本预览追加到对比缓冲区（总长上限约 120KB）。
     *
     * @param out    对比文本缓冲
     * @param path   相对路径（展示用）
     * @param before 修复前文件（可能不存在）
     * @param after  修复后文件（可能不存在）
     * @throws IOException 读取文件失败
     */
    private void appendComparison(StringBuilder out, String path, Path before, Path after) throws IOException {
        if (out.length() >= 120_000) return;
        out.append("\n===== ").append(path).append(" =====\n--- 修复前\n")
                .append(readPreview(before)).append("\n+++ 修复后\n").append(readPreview(after)).append('\n');
    }

    /**
     * 读取文件内容预览，最长约 30KB，超长截断并标注。
     *
     * @param file 目标文件路径
     * @return UTF-8 文本预览；非普通文件时返回占位说明
     * @throws IOException 读取失败
     */
    private String readPreview(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return "(文件不存在)";
        byte[] value = Files.readAllBytes(file);
        int length = Math.min(value.length, 30_000);
        String text = new String(value, 0, length, StandardCharsets.UTF_8);
        return value.length > length ? text + "\n…内容已截断" : text;
    }

    /**
     * 将相对路径编码为 URL-safe Base64，用于快照元数据键值。
     *
     * @param value 原始相对路径
     * @return Base64URL 无填充编码
     */
    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将相对路径哈希为固定长度十六进制键，用作快照备份文件名。
     * <p>避免中文长路径经 Base64 膨胀后超过 Windows 255 字符限制。</p>
     *
     * @param value 相对路径原文
     * @return SHA-256 十六进制摘要
     * @throws IllegalStateException 运行时不可用 SHA-256
     */
    static String snapshotKey(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    /**
     * 解码快照元数据中的 Base64URL 路径。
     *
     * @param value 编码串
     * @return UTF-8 相对路径
     */
    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    /**
     * 从文件加载 Properties 清单。
     *
     * @param path 清单文件路径
     * @return 已加载的属性表
     * @throws IOException 读取失败
     */
    private Properties loadProperties(Path path) throws IOException {
        Properties value = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            value.load(input);
        }
        return value;
    }

    /**
     * 将 Properties 清单写回文件。
     *
     * @param path  目标文件
     * @param value 属性内容
     * @throws IOException 写入失败
     */
    private void storeProperties(Path path, Properties value) throws IOException {
        try (java.io.OutputStream output = Files.newOutputStream(path)) {
            value.store(output, "CodeX repair snapshot");
        }
    }

    /**
     * 计算实际变更相对允许集合的差集（越界文件）。
     *
     * @param actual  实际变更路径
     * @param allowed 批准允许路径
     * @return 超出范围的路径集合
     */
    private Set<String> difference(Set<String> actual, Set<String> allowed) {
        Set<String> result = new HashSet<>(actual);
        result.removeAll(allowed);
        return result;
    }

    /**
     * 修复前快照元数据。
     *
     * @param id        快照 UUID
     * @param workspace 运维任务配置的工作区根
     * @param repos     实际跟踪的代码 Git 仓快照（可含嵌套仓）
     */
    private record RepairSnapshot(String id, Path workspace, List<RepoSnapshot> repos) { }

    /**
     * 单个 Git 仓的修复前快照。
     */
    private record RepoSnapshot(Path gitRoot, Path root, Map<String, String> before, String headSha) { }

    /**
     * 修复后变更汇总。
     *
     * @param files      实际受影响的相对路径
     * @param comparison 文本对比预览
     */
    private record RepairChanges(Set<String> files, String comparison) { }
}
