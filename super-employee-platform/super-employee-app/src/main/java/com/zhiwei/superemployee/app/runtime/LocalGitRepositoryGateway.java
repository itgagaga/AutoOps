package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.system.WorkspaceSettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 本地 Git 查询/轻量操作：只读 STATUS/LOG/DIFF/SHOW/BRANCH，以及受控 PULL(--ff-only)。
 * 使用 {@code git -C <repo>} 指定仓库，避免 Windows 下特殊路径导致 ProcessBuilder.directory 失效。
 * @author yangdb
 */
@Component
public class LocalGitRepositoryGateway {
    private static final Set<String> READ_ACTIONS = Set.of("STATUS", "LOG", "DIFF", "SHOW", "BRANCH");
    private static final Set<String> WRITE_ACTIONS = Set.of("PULL");
    private static final int MAX_OUTPUT_CHARS = 20000;
    private static final Set<String> IGNORABLE_DIR_NAMES = Set.of(
            "target", "logs", "node_modules", "dist", "build", "coverage", "out",
            "vendor", "__pycache__", "venv", ".venv", "unpackage", "miniprogram_npm",
            ".idea", ".vscode", ".agents", ".agentscope", ".specstory", ".claude",
            ".omc", ".zhiwei", ".zhiwei-logs", ".zhiwei-deploy", ".vite", ".cache",
            ".parcel-cache", ".turbo", ".sts4-cache", ".apt_generated", ".settings",
            "nbproject", "storybook-static", ".git"
    );
    private static final Set<String> IGNORABLE_FILE_NAMES = Set.of(
            ".gitignore", ".dockerignore", ".npmignore", ".eslintignore",
            ".prettierignore", ".cursorindexingignore", ".ds_store", "thumbs.db",
            "desktop.ini", ".factorypath", ".springbeans", ".flattened-pom.xml",
            ".classpath", ".project", ".env", ".envrc"
    );
    private static final Set<String> IGNORABLE_SUFFIXES = Set.of(
            ".iml", ".iws", ".ipr", ".log", ".class", ".orig", ".bak", ".tmp",
            ".swp", ".swo", ".jar", ".war", ".ear", ".nar", ".tsbuildinfo",
            ".lock.db", ".mv.db", ".h2.db", ".trace.db"
    );

    private final WorkspaceSettingsService workspaceSettings;
    private final long timeoutSeconds;

    /**
     * 注入工作区设置服务与 Git 命令超时。
     *
     * @param workspaceSettings 工作区/Maven 模块解析服务
     * @param timeoutSeconds    Git 命令超时秒数，最小 5
     */
    public LocalGitRepositoryGateway(WorkspaceSettingsService workspaceSettings,
                                     @Value("${super-employee.git.timeout-seconds:60}") long timeoutSeconds) {
        this.workspaceSettings = workspaceSettings;
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
    }

    /**
     * Agent 用：在任务工作区（须为 Maven 项目或其上级含 pom）上执行只读/受控 Git 动作。
     *
     * @param taskWorkspacePath 任务工作区路径
     * @param action            动作名
     * @param pathOrRef         路径或引用
     * @param maxCommits        LOG 最大提交数
     * @return 执行结果
     */
    public Result inspect(String taskWorkspacePath, String action, String pathOrRef, int maxCommits) {
        Path workspace = workspaceSettings.resolveMavenModule(taskWorkspacePath);
        return execute(workspace, action, pathOrRef, maxCommits, false);
    }

    /**
     * 为故障修复准备稳定的运维分支：先同步远端基线，再创建或复用并校验实际分支。
     *
     * @param taskWorkspacePath 任务工作区路径
     * @param branchName        运维分支名
     * @return 分支准备结果与说明输出
     * @throws IllegalStateException 未找到有效 Git 仓库
     * @throws IllegalArgumentException 分支名非法
     */
    public Result ensureOperationalBranch(String taskWorkspacePath, String branchName) {
        Path workspace = workspaceSettings.resolveMavenModule(taskWorkspacePath);
        Path repoRoot = findGitRoot(workspace);
        if (repoRoot == null) {
            throw new IllegalStateException("代码工作区或其上级未找到有效 Git 仓库：" + workspace);
        }
        String branch = sanitizeBranch(branchName);
        Result current = run(repoRoot, List.of(
                "git", "-C", repoRoot.toString(), "branch", "--show-current"));
        if (!current.success() || current.output().trim().isBlank()) {
            return new Result(false, "无法识别当前 Git 分支：\n" + current.output(), repoRoot.toString());
        }
        String currentBranch = current.output().trim();
        Result status = run(repoRoot, List.of(
                "git", "-C", repoRoot.toString(), "status", "--porcelain"));
        boolean dirtyWorktree = status.success() && !status.output().isBlank();
        if (branch.equals(currentBranch) && dirtyWorktree) {
            return new Result(true,
                    "运维分支已经检出；工作区存在未提交修改，已保留当前内容并跳过基线合并："
                            + branch,
                    repoRoot.toString());
        }

        Result remote = run(repoRoot, List.of(
                "git", "-C", repoRoot.toString(), "remote", "get-url", "origin"));
        String latestRef = null;
        String updateMessage = "仓库未配置 origin，使用本地最新 HEAD";
        if (remote.success()) {
            Result fetched = run(repoRoot, List.of(
                    "git", "-C", repoRoot.toString(), "fetch", "--prune", "origin"));
            if (!fetched.success()) {
                latestRef = resolveLatestRemoteRef(repoRoot, currentBranch, branch);
                updateMessage = latestRef == null
                        ? "远端 fetch 因认证失败，且没有可用的远端缓存；使用本地当前 HEAD。\n" + fetched.output()
                        : "远端 fetch 因认证失败，使用本地缓存基线 " + latestRef + "。\n" + fetched.output();
            }
            else {
                latestRef = resolveLatestRemoteRef(repoRoot, currentBranch, branch);
                if (latestRef == null) {
                    updateMessage = "已 fetch origin，但无法确定远端基线分支；使用本地当前 HEAD";
                }
                else {
                    updateMessage = "已同步远端最新基线：" + latestRef;
                }
            }
        }

        Result exists = run(repoRoot, List.of(
                "git", "-C", repoRoot.toString(), "show-ref", "--verify", "--quiet",
                "refs/heads/" + branch));
        Result switched;
        String action;
        if (exists.success()) {
            switched = branch.equals(currentBranch)
                    ? new Result(true, "运维分支已经检出", repoRoot.toString())
                    : run(repoRoot, List.of("git", "-C", repoRoot.toString(), "switch", branch));
            action = "复用并切换到已有运维分支";
        }
        else {
            // 工作区有本地修改时必须从当前 HEAD 建分支；直接切到远端基线会触发
            // “local changes would be overwritten by checkout” 并阻断 CodeX。
            List<String> createCommand = latestRef == null || dirtyWorktree
                    ? List.of("git", "-C", repoRoot.toString(), "switch", "-c", branch)
                    : List.of("git", "-C", repoRoot.toString(), "switch", "-c", branch,
                            "--no-track", latestRef);
            switched = run(repoRoot, createCommand);
            action = dirtyWorktree
                    ? "保留本地修改并基于当前 HEAD 创建运维分支"
                    : "基于最新代码创建并切换到运维分支";
            if (switched.success() && latestRef != null) {
                run(repoRoot, List.of("git", "-C", repoRoot.toString(), "config",
                        "branch." + branch + ".zhiweiBaseRef", latestRef));
            }
        }
        if (!switched.success()) {
            return new Result(false, action + "失败：" + branch + "\n" + switched.output(),
                    repoRoot.toString());
        }
        // 无远端的本地演示仓也要落盘基线，否则合并时无法确定主分支。
        Result existingBase = run(repoRoot, List.of("git", "-C", repoRoot.toString(),
                "config", "--get", "branch." + branch + ".zhiweiBaseRef"));
        if (!existingBase.success() || existingBase.output().trim().isBlank()) {
            String localBase = latestRef != null ? latestRef
                    : resolveOrCreateLocalBaseBranch(repoRoot, branch);
            if (localBase != null && !localBase.isBlank()) {
                run(repoRoot, List.of("git", "-C", repoRoot.toString(), "config",
                        "branch." + branch + ".zhiweiBaseRef", localBase));
                updateMessage += "\n已记录本地合并基线：" + localBase;
            }
        }
        if (latestRef != null && exists.success()) {
            Result updated = run(repoRoot, List.of(
                    "git", "-C", repoRoot.toString(), "merge", "--ff-only", latestRef));
            if (!updated.success()) {
                /*
                 * 运维分支可能已包含多轮修复提交。这里不能 rebase：当基线曾发生目录迁移、
                 * 大规模删除或重命名时，rebase 会逐个重放历史提交，产生大量 rename/delete
                 * 冲突。合并最新基线既保留运维提交，又能保证最新基线成为当前分支祖先。
                 *
                 * 冲突时优先采用 latestRef（theirs）的内容，避免旧运维提交覆盖最新基线；
                 * 运维分支中不冲突的修复仍会保留。--autostash 用于保护工作区已有修改。
                 */
                Result merged = run(repoRoot, List.of(
                        "git", "-C", repoRoot.toString(), "merge", "--no-edit", "--no-ff",
                        "--autostash", "-X", "theirs", latestRef));
                if (!merged.success()) {
                    Result aborted = run(repoRoot, List.of(
                            "git", "-C", repoRoot.toString(), "merge", "--abort"));
                    return new Result(false,
                            "已有运维分支与最新基线发生分叉，自动合并失败，已尝试恢复原分支状态。"
                                    + "\n快进结果：\n" + updated.output()
                                    + "\n合并结果：\n" + merged.output()
                                    + "\n恢复结果：\n" + aborted.output(),
                            repoRoot.toString());
                }
                updateMessage += "\n运维分支存在独立提交，已合并最新基线；冲突内容以最新基线为准。"
                        + "\n" + merged.output();
            }
            else {
                updateMessage += "\n" + updated.output();
            }
        }
        Result verified = run(repoRoot, List.of(
                "git", "-C", repoRoot.toString(), "branch", "--show-current"));
        if (!verified.success() || !branch.equals(verified.output().trim())) {
            return new Result(false, "运维分支校验失败，当前实际分支为：" + verified.output(),
                    repoRoot.toString());
        }
        return new Result(true, action + "：" + branch + "\n" + updateMessage + "\n" + switched.output(),
                repoRoot.toString());
    }

    /**
     * 将已审阅的修复文件提交到运维分支，并合并回其记录的基线分支。
     * 合并成功后删除本地运维分支；不会自动 push。
     */
    public Result mergeOperationalBranch(String taskWorkspacePath, String branchName,
                                         List<String> changedFiles) {
        Path workspace = workspaceSettings.resolveMavenModule(taskWorkspacePath);
        Path repoRoot = findGitRoot(workspace);
        if (repoRoot == null) {
            return new Result(false, "未找到有效 Git 仓库", workspace.toString());
        }
        String branch = sanitizeBranch(branchName);
        Result current = run(repoRoot, List.of("git", "-C", repoRoot.toString(),
                "branch", "--show-current"));
        if (!current.success() || !branch.equals(current.output().trim())) {
            return new Result(false, "当前未检出待合并运维分支：" + branch, repoRoot.toString());
        }
        if (changedFiles != null) {
            for (String raw : changedFiles) {
                String path = sanitizePath(raw);
                if (path == null) continue;
                Result added = run(repoRoot, List.of("git", "-C", repoRoot.toString(),
                        "add", "--", path));
                if (!added.success()) return added;
            }
        }
        // CLI 幂等/漏报 changedFiles 时，工作区仍可能留有真实修复源码（如 FaultInjectorJob.java）。
        // 用户已确认合并，将这些未审阅源码一并纳入运维修复提交。
        Result pendingDirty = run(repoRoot, List.of("git", "-C", repoRoot.toString(),
                "status", "--porcelain"));
        if (pendingDirty.success()) {
            for (String path : blockingWorktreePaths(pendingDirty.output())) {
                Result added = run(repoRoot, List.of("git", "-C", repoRoot.toString(),
                        "add", "--", path));
                if (!added.success()) return added;
            }
        }
        Result staged = run(repoRoot, List.of("git", "-C", repoRoot.toString(),
                "diff", "--cached", "--quiet"));
        if (!staged.success()) {
            Result committed = run(repoRoot, List.of("git", "-C", repoRoot.toString(),
                    "commit", "-m", "fix(ops): apply reviewed repair"));
            if (!committed.success()) return committed;
        }
        Result dirty = run(repoRoot, List.of("git", "-C", repoRoot.toString(),
                "status", "--porcelain"));
        if (!dirty.success()) {
            return new Result(false, "无法检查工作区状态：\n" + dirty.output(), repoRoot.toString());
        }
        String blocking = blockingWorktreeChanges(dirty.output());
        if (!blocking.isBlank()) {
            return new Result(false, "工作区仍有未审阅修改，已拒绝切换和合并：\n" + blocking,
                    repoRoot.toString());
        }
        Result configured = run(repoRoot, List.of("git", "-C", repoRoot.toString(),
                "config", "--get", "branch." + branch + ".zhiweiBaseRef"));
        String baseRef = configured.success() ? configured.output().trim() : null;
        if (baseRef == null || baseRef.isBlank()) {
            baseRef = resolveLatestRemoteRef(repoRoot, branch, branch);
        }
        if (baseRef == null || baseRef.isBlank()) {
            baseRef = resolveOrCreateLocalBaseBranch(repoRoot, branch);
        }
        if (baseRef == null || baseRef.isBlank()) {
            return new Result(false, "无法确定运维分支对应的主分支（仓库无 main/master，且未能自动创建本地基线）",
                    repoRoot.toString());
        }
        String baseBranch = baseRef.startsWith("origin/") ? baseRef.substring("origin/".length()) : baseRef;
        baseBranch = sanitizeBranch(baseBranch);
        Result localBase = run(repoRoot, List.of("git", "-C", repoRoot.toString(),
                "show-ref", "--verify", "--quiet", "refs/heads/" + baseBranch));
        Result switched;
        if (localBase.success()) {
            switched = run(repoRoot, List.of("git", "-C", repoRoot.toString(), "switch", baseBranch));
        }
        else if (baseRef.startsWith("origin/")) {
            switched = run(repoRoot, List.of("git", "-C", repoRoot.toString(), "switch", "-c", baseBranch,
                    "--track", baseRef));
        }
        else {
            return new Result(false, "合并基线分支不存在：" + baseBranch, repoRoot.toString());
        }
        if (!switched.success()) return switched;
        Result merged = run(repoRoot, List.of("git", "-C", repoRoot.toString(),
                "merge", "--no-ff", "--no-edit", branch));
        if (!merged.success()) {
            run(repoRoot, List.of("git", "-C", repoRoot.toString(), "merge", "--abort"));
            run(repoRoot, List.of("git", "-C", repoRoot.toString(), "switch", branch));
            return new Result(false, "合并主分支失败，已恢复运维分支：\n" + merged.output(), repoRoot.toString());
        }
        Result deleted = run(repoRoot, List.of("git", "-C", repoRoot.toString(),
                "branch", "-d", branch));
        String output = "已合并到 " + baseBranch + "（未 push）\n" + merged.output();
        if (!deleted.success()) output += "\n运维分支回收失败，可稍后手动清理：\n" + deleted.output();
        return new Result(true, output, repoRoot.toString());
    }

    /**
     * 解析远端基线引用：优先运维分支配置的 zhiweiBaseRef，其次当前分支远端跟踪，
     * 再次 origin/HEAD，最后尝试 main/master/dev/develop。
     *
     * @param repoRoot           Git 仓库根
     * @param currentBranch      当前检出分支名
     * @param operationalBranch  目标运维分支名
     * @return 形如 {@code origin/main} 的引用；无法确定时返回 {@code null}
     */
    private String resolveLatestRemoteRef(Path repoRoot, String currentBranch, String operationalBranch) {
        Result configuredBase = run(repoRoot, List.of(
                "git", "-C", repoRoot.toString(), "config", "--get",
                "branch." + operationalBranch + ".zhiweiBaseRef"));
        if (configuredBase.success() && configuredBase.output().trim().startsWith("origin/")) {
            String configuredRef = configuredBase.output().trim();
            Result exists = run(repoRoot, List.of(
                    "git", "-C", repoRoot.toString(), "show-ref", "--verify", "--quiet",
                    "refs/remotes/" + configuredRef));
            if (exists.success()) {
                return configuredRef;
            }
        }
        if (!operationalBranch.equals(currentBranch)) {
            Result currentRemote = run(repoRoot, List.of(
                    "git", "-C", repoRoot.toString(), "show-ref", "--verify", "--quiet",
                    "refs/remotes/origin/" + currentBranch));
            if (currentRemote.success()) {
                return "origin/" + currentBranch;
            }
        }
        Result remoteHead = run(repoRoot, List.of(
                "git", "-C", repoRoot.toString(), "symbolic-ref", "--quiet", "--short",
                "refs/remotes/origin/HEAD"));
        if (remoteHead.success() && remoteHead.output().trim().startsWith("origin/")) {
            return remoteHead.output().trim();
        }
        for (String candidate : List.of("main", "master", "dev", "develop")) {
            Result exists = run(repoRoot, List.of(
                    "git", "-C", repoRoot.toString(), "show-ref", "--verify", "--quiet",
                    "refs/remotes/origin/" + candidate));
            if (exists.success()) {
                return "origin/" + candidate;
            }
        }
        for (String candidate : List.of("main", "master", "dev", "develop")) {
            if (candidate.equals(currentBranch) || candidate.equals(operationalBranch)) {
                continue;
            }
            Result exists = run(repoRoot, List.of(
                    "git", "-C", repoRoot.toString(), "show-ref", "--verify", "--quiet",
                    "refs/heads/" + candidate));
            if (exists.success()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 无远端、也无 main/master 时（常见于本地演示仓），自动创建 {@code main} 指向仓库根提交，
     * 作为运维分支合并目标。
     *
     * @param repoRoot           Git 仓库根
     * @param operationalBranch  当前运维分支（不会用作基线名）
     * @return 本地基线分支名，失败时 {@code null}
     */
    private String resolveOrCreateLocalBaseBranch(Path repoRoot, String operationalBranch) {
        for (String candidate : List.of("main", "master", "develop", "dev")) {
            if (candidate.equals(operationalBranch)) {
                continue;
            }
            Result exists = run(repoRoot, List.of(
                    "git", "-C", repoRoot.toString(), "show-ref", "--verify", "--quiet",
                    "refs/heads/" + candidate));
            if (exists.success()) {
                return candidate;
            }
        }
        Result roots = run(repoRoot, List.of(
                "git", "-C", repoRoot.toString(), "rev-list", "--max-parents=0", "HEAD"));
        if (!roots.success() || roots.output().isBlank()) {
            return null;
        }
        String rootSha = roots.output().trim().lines()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");
        if (rootSha.isBlank()) {
            return null;
        }
        String baseName = "main";
        if (baseName.equals(operationalBranch)) {
            baseName = "master";
        }
        Result created = run(repoRoot, List.of(
                "git", "-C", repoRoot.toString(), "branch", baseName, rootSha));
        if (!created.success()) {
            Result exists = run(repoRoot, List.of(
                    "git", "-C", repoRoot.toString(), "show-ref", "--verify", "--quiet",
                    "refs/heads/" + baseName));
            if (!exists.success()) {
                return null;
            }
        }
        return baseName;
    }

    /**
     * Git 客户端用：对任意存在的目录向上查找仓库根后执行动作。
     *
     * @param directoryPath 起始目录
     * @param action        动作名
     * @param pathOrRef     路径或引用
     * @param maxCommits    LOG 最大提交数
     * @param fullDiff      是否完整 DIFF
     * @return 执行结果
     * @throws IllegalArgumentException 目录为空或不存在
     */
    public Result inspectDirectory(String directoryPath, String action, String pathOrRef,
                                   int maxCommits, boolean fullDiff) {
        if (directoryPath == null || directoryPath.isBlank()) {
            throw new IllegalArgumentException("请指定仓库目录");
        }
        Path path = Path.of(directoryPath.trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("目录不存在：" + path);
        }
        return execute(path, action, pathOrRef, maxCommits, fullDiff);
    }

    /**
     * 在起始目录向上查找 Git 根后，按动作组装并执行受控 Git 命令。
     *
     * @param start       起始目录（可为模块或任意仓库内路径）
     * @param action      动作名：STATUS/LOG/DIFF/SHOW/BRANCH/PULL
     * @param pathOrRef   路径或引用参数（按动作解释）
     * @param maxCommits  LOG 最大提交数
     * @param fullDiff    DIFF 是否输出完整 diff（否则仅 --stat）
     * @return 命令执行结果
     * @throws IllegalArgumentException 不支持的动作或非法参数
     * @throws IllegalStateException    未找到有效 Git 仓库
     */
    public Result execute(Path start, String action, String pathOrRef, int maxCommits, boolean fullDiff) {
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (!READ_ACTIONS.contains(normalized) && !WRITE_ACTIONS.contains(normalized)) {
            throw new IllegalArgumentException("不支持的 Git 动作，仅允许：STATUS、LOG、DIFF、SHOW、BRANCH、PULL");
        }
        Path repoRoot = findGitRoot(start);
        if (repoRoot == null) {
            Path rawGit = start.resolve(".git");
            if (Files.isDirectory(rawGit) && !isValidGitMetadata(rawGit)) {
                throw new IllegalStateException("目录下存在空的或损坏的 .git，不是有效 Git 仓库：" + start
                        + "。请选择真正 clone/init 过的仓库根目录。");
            }
            throw new IllegalStateException("目录或其上级未找到有效 Git 仓库：" + start
                    + "。请选择包含完整 .git（含 HEAD）的仓库根目录。");
        }
        List<String> command = buildCommand(repoRoot, normalized, pathOrRef, maxCommits, fullDiff);
        return run(repoRoot, command);
    }

    /**
     * 按动作组装 {@code git -C <repo> ...} 命令参数列表。
     *
     * @param repoRoot   仓库根
     * @param action     已规范化的大写动作名
     * @param pathOrRef  路径或引用
     * @param maxCommits LOG 条数上限
     * @param fullDiff   是否完整 DIFF
     * @return 可交给 {@link ProcessBuilder} 的参数列表
     * @throws IllegalArgumentException SHOW 缺引用或不支持的动作
     */
    private List<String> buildCommand(Path repoRoot, String action, String pathOrRef,
                                      int maxCommits, boolean fullDiff) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(repoRoot.toAbsolutePath().normalize().toString());
        switch (action) {
            case "STATUS" -> command.addAll(List.of("status", "--short", "--branch"));
            case "LOG" -> {
                int n = Math.max(1, Math.min(100, maxCommits <= 0 ? 30 : maxCommits));
                // --graph --all：提交拓扑树（含分支分叉/合并）；等宽字体下可读
                command.addAll(List.of("log", "-n", String.valueOf(n),
                        "--graph", "--decorate", "--all", "--date=short",
                        "--pretty=format:%h %ad %an %s"));
            }
            case "DIFF" -> {
                command.add("diff");
                if (!fullDiff) {
                    command.add("--stat");
                }
                String path = sanitizePath(pathOrRef);
                if (path != null) {
                    command.add("--");
                    command.add(path);
                }
            }
            case "SHOW" -> {
                String ref = sanitizeRef(pathOrRef);
                if (ref == null) {
                    throw new IllegalArgumentException("SHOW 需要合法的提交/引用（如 HEAD 或 commit SHA）");
                }
                command.addAll(List.of("show", "--stat", "--format=medium", ref));
            }
            case "BRANCH" -> command.addAll(List.of("branch", "-vv", "-a"));
            case "PULL" -> command.addAll(List.of("pull", "--ff-only", "--no-edit"));
            default -> throw new IllegalArgumentException("不支持的 Git 动作");
        }
        return command;
    }

    /**
     * 启动 Git 进程，合并 stderr，限时等待并截断输出。
     *
     * @param repoRoot 仓库根（写入结果供展示）
     * @param command  完整命令参数
     * @return 成功/失败结果与输出文本
     */
    private Result run(Path repoRoot, List<String> command) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            builder.environment().put("GIT_TERMINAL_PROMPT", "0");
            // 不依赖 directory()；仓库路径已通过 git -C 传入
            process = builder.start();
            String output = readBounded(process);
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new Result(false, "Git 命令超时（" + timeoutSeconds + "s）\n" + output, repoRoot.toString());
            }
            String text = output == null || output.isBlank()
                    ? (process.exitValue() == 0 ? "(无输出)" : "(失败且无输出)")
                    : output;
            return new Result(process.exitValue() == 0, text, repoRoot.toString());
        }
        catch (IOException ex) {
            return new Result(false, "Git 命令启动失败：" + ex.getMessage()
                    + "（请确认运行环境已安装 git 并在 PATH 中）", repoRoot.toString());
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(false, "Git 命令被中断", repoRoot.toString());
        }
        finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 按平台默认编码读取进程输出，超过 {@link #MAX_OUTPUT_CHARS} 时截断。
     *
     * @param process 已启动的 Git 进程
     * @return 有界输出文本
     * @throws IOException 读取失败
     */
    private static String readBounded(Process process) throws IOException {
        Charset charset = Charset.defaultCharset();
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            // Windows 下 git 常输出系统默认编码（GBK），优先用默认 charset
            charset = Charset.defaultCharset();
        }
        else {
            charset = StandardCharsets.UTF_8;
        }
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), charset))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (out.length() >= MAX_OUTPUT_CHARS) {
                    out.append("\n…(输出已截断)");
                    break;
                }
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append(line);
            }
        }
        return out.toString();
    }

    /**
     * 自起始路径向上查找有效 Git 仓库根；若误选 {@code .git} 目录则回退到父目录。
     *
     * @param start 起始路径，允许 {@code null}
     * @return 仓库根路径；未找到时返回 {@code null}
     */
    public static Path findGitRoot(Path start) {
        if (start == null) {
            return null;
        }
        Path current = start.toAbsolutePath().normalize();
        // 若误选了 .git 目录本身，回退到父目录
        if (".git".equalsIgnoreCase(String.valueOf(current.getFileName()))) {
            current = current.getParent();
            if (current == null) {
                return null;
            }
        }
        while (current != null) {
            if (isValidGitMetadata(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * 识别有效 Git 元数据：完整目录仓库，或 gitfile（worktree/submodule）。
     *
     * @param gitMeta {@code .git} 文件或目录路径
     * @return 有效元数据时返回 {@code true}
     */
    public static boolean isValidGitMetadata(Path gitMeta) {
        if (gitMeta == null || !Files.exists(gitMeta)) {
            return false;
        }
        try {
            if (Files.isRegularFile(gitMeta)) {
                String content = Files.readString(gitMeta).trim();
                return content.toLowerCase(Locale.ROOT).startsWith("gitdir:");
            }
            if (Files.isDirectory(gitMeta)) {
                return Files.isRegularFile(gitMeta.resolve("HEAD"))
                        || Files.isDirectory(gitMeta.resolve("refs"))
                        || Files.isDirectory(gitMeta.resolve("objects"));
            }
        }
        catch (IOException ignored) {
            return false;
        }
        return false;
    }

    /**
     * 校验并规范化相对路径参数，拒绝 {@code ..}、绝对路径与注入字符。
     *
     * @param raw 原始路径，可空
     * @return 规范化相对路径；空白时返回 {@code null}
     * @throws IllegalArgumentException 路径非法或不安全
     */
    private static String sanitizePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replace('\\', '/');
        if (value.contains("..") || value.startsWith("/") || value.contains(":")
                || value.indexOf(0) >= 0 || looksUnsafe(value)) {
            throw new IllegalArgumentException("非法路径参数");
        }
        return value;
    }

    /**
     * 校验 Git 引用（提交 SHA、HEAD、分支名等），限制字符集与长度。
     *
     * @param raw 原始引用，可空
     * @return 合法引用；空白时返回 {@code null}
     * @throws IllegalArgumentException 引用非法
     */
    private static String sanitizeRef(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (!value.matches("^[A-Za-z0-9._/@~^-]{1,128}$") || value.contains("..")) {
            throw new IllegalArgumentException("非法 Git 引用");
        }
        return value;
    }

    /**
     * 校验运维分支名称，拒绝空值及危险字符序列。
     *
     * @param raw 原始分支名
     * @return 合法分支名
     * @throws IllegalArgumentException 名称为空或非法
     */
    private static String sanitizeBranch(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("运维分支名称不能为空");
        }
        String value = raw.trim();
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9._/-]{0,127}$")
                || value.contains("..") || value.contains("//") || value.endsWith("/")
                || value.endsWith(".") || value.contains("@{")) {
            throw new IllegalArgumentException("非法运维分支名称");
        }
        return value;
    }

    /**
     * 检测字符串是否含 shell 注入相关元字符。
     *
     * @param value 待检测文本
     * @return 含危险字符时返回 {@code true}
     */
    private static boolean looksUnsafe(String value) {
        return value.contains(";") || value.contains("|") || value.contains("&")
                || value.contains("`") || value.contains("$") || value.contains(">")
                || value.contains("<") || value.contains("\n");
    }

    /**
     * 合并前只拦截源码的未提交改动。IDE / 忽略规则 / 运维落盘文件不阻止切分支。
     */
    static String blockingWorktreeChanges(String porcelain) {
        if (porcelain == null || porcelain.isBlank()) {
            return "";
        }
        StringBuilder blocking = new StringBuilder();
        for (String line : porcelain.split("\\R")) {
            if (line.isBlank() || isIgnorableStatusLine(line)) {
                continue;
            }
            if (!blocking.isEmpty()) {
                blocking.append('\n');
            }
            blocking.append(line);
        }
        return blocking.toString();
    }

    /** 解析 porcelain 中仍会拦截合并的路径列表（已去掉可忽略噪声）。 */
    static List<String> blockingWorktreePaths(String porcelain) {
        List<String> paths = new ArrayList<>();
        if (porcelain == null || porcelain.isBlank()) {
            return paths;
        }
        for (String line : porcelain.split("\\R")) {
            if (line.isBlank() || isIgnorableStatusLine(line)) {
                continue;
            }
            String path = statusPath(line);
            if (path != null && !path.isBlank()) {
                paths.add(path);
            }
        }
        return paths;
    }

    static boolean isIgnorableStatusLine(String line) {
        String path = statusPath(line);
        return path != null && isIgnorableWorktreePath(path);
    }

    private static String statusPath(String line) {
        String raw = line.stripTrailing();
        if (raw.length() < 4) {
            return null;
        }
        String path = raw.substring(3).trim().replace('\\', '/');
        if (path.contains(" -> ")) {
            path = path.substring(path.lastIndexOf(" -> ") + 4).trim();
        }
        if (path.startsWith("\"") && path.endsWith("\"") && path.length() >= 2) {
            path = path.substring(1, path.length() - 1);
        }
        return path;
    }

    static boolean isIgnorableWorktreePath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String value = path.replace('\\', '/');
        if (value.startsWith("./")) {
            value = value.substring(2);
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            return false;
        }
        String[] parts = value.split("/");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || ".".equals(part)) {
                continue;
            }
            String lower = part.toLowerCase(Locale.ROOT);
            if (IGNORABLE_DIR_NAMES.contains(lower)) {
                return true;
            }
            if (part.startsWith(".") && !".github".equals(lower) && !".mvn".equals(lower)) {
                return true;
            }
            if (i == parts.length - 1 && isIgnorableFileName(lower, value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIgnorableFileName(String lowerName, String fullPath) {
        if (IGNORABLE_FILE_NAMES.contains(lowerName)
                || lowerName.startsWith("~$")
                || lowerName.endsWith("~")
                || lowerName.startsWith("hs_err_pid")
                || lowerName.startsWith("replay_pid")) {
            return true;
        }
        for (String suffix : IGNORABLE_SUFFIXES) {
            if (!lowerName.endsWith(suffix)) {
                continue;
            }
            if ((".jar".equals(suffix) || ".war".equals(suffix))
                    && fullPath.toLowerCase(Locale.ROOT).contains(".mvn/wrapper/")) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * 本地 Git 操作结果。
     *
     * @param success         进程退出码是否为 0
     * @param output          合并后的命令输出（可能截断）
     * @param repositoryRoot  解析到的仓库根路径字符串
     */
    public record Result(boolean success, String output, String repositoryRoot) {
    }
}
