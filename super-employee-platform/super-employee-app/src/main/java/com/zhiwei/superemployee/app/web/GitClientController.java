package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.audit.AuditService;
import com.zhiwei.superemployee.app.runtime.LocalGitRepositoryGateway;
import com.zhiwei.superemployee.app.system.WorkspaceSettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 浏览器侧 Git 客户端：查询本地仓库状态 / 日志 / 差异，以及受控 pull --ff-only。
 * <p>
 * 路径前缀 {@code /api/git}。菜单权限：管理员或 {@code OPS_GIT_CLIENT}。
 * 写操作（如 PULL）会记审计；查询失败以 IllegalStateException 暴露输出。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/git")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('OPS_GIT_CLIENT')")
public class GitClientController {
    private final LocalGitRepositoryGateway git;
    private final WorkspaceSettingsService workspaces;
    private final AuditService audit;

    /**
     * @param git        本地 Git 网关
     * @param workspaces 工作区设置（默认路径）
     * @param audit      审计服务
     */
    public GitClientController(LocalGitRepositoryGateway git,
                               WorkspaceSettingsService workspaces,
                               AuditService audit) {
        this.git = git;
        this.workspaces = workspaces;
        this.audit = audit;
    }

    /**
     * 返回系统默认工作区路径，并探测其是否落在有效 Git 仓库内。
     *
     * @return 工作区设置 + gitRepositoryRoot / gitReady
     */
    @GetMapping("/default-workspace")
    public Map<String, Object> defaultWorkspace() {
        Map<String, Object> current = workspaces.current();
        String path = String.valueOf(current.getOrDefault("workspacePath", ""));
        Map<String, Object> result = new LinkedHashMap<>(current);
        if (path != null && !path.isBlank()) {
            Path start = Path.of(path).toAbsolutePath().normalize();
            Path root = LocalGitRepositoryGateway.findGitRoot(start);
            result.put("gitRepositoryRoot", root == null ? null : root.toString());
            result.put("gitReady", root != null);
        }
        else {
            result.put("gitRepositoryRoot", null);
            result.put("gitReady", false);
        }
        return result;
    }

    /**
     * 探测给定目录是否为（或处于）有效 Git 仓库；目录不存在抛 IllegalArgumentException。
     *
     * @param request 含 workspacePath
     * @return gitReady、仓库根与说明 message
     */
    @PostMapping("/probe")
    public Map<String, Object> probe(@Valid @RequestBody GitProbeRequest request) {
        Path start = Path.of(request.getWorkspacePath().trim()).toAbsolutePath().normalize();
        if (!Files.isDirectory(start)) {
            throw new IllegalArgumentException("目录不存在：" + start);
        }
        Path root = LocalGitRepositoryGateway.findGitRoot(start);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspacePath", root == null ? start.toString() : root.toString());
        result.put("gitRepositoryRoot", root == null ? null : root.toString());
        result.put("gitReady", root != null);
        if (root != null) {
            result.put("message", "已识别 Git 仓库");
        }
        else if (Files.isDirectory(start.resolve(".git"))
                && !LocalGitRepositoryGateway.isValidGitMetadata(start.resolve(".git"))) {
            result.put("message", "存在空的或损坏的 .git，不是有效仓库");
        }
        else {
            result.put("message", "未找到有效 Git 仓库");
        }
        return result;
    }

    /**
     * 对工作区执行受控 Git 动作（STATUS/LOG/DIFF/SHOW/BRANCH/PULL），并写审计。
     * 失败时抛 IllegalStateException（由全局处理器转为 409）。
     *
     * @param request 工作区路径、动作及可选 pathOrRef/maxCommits/fullDiff
     * @return 成功时的输出与耗时等
     */
    @PostMapping("/query")
    public Map<String, Object> query(@Valid @RequestBody GitQueryRequest request) {
        long started = System.currentTimeMillis();
        LocalGitRepositoryGateway.Result result = git.inspectDirectory(
                request.getWorkspacePath(),
                request.getAction(),
                request.getPathOrRef(),
                request.getMaxCommits() == null ? 30 : request.getMaxCommits(),
                Boolean.TRUE.equals(request.getFullDiff()));
        long elapsed = System.currentTimeMillis() - started;
        String root = result.repositoryRoot() == null ? "" : result.repositoryRoot();
        String shortId = root;
        int slash = Math.max(root.lastIndexOf('\\'), root.lastIndexOf('/'));
        if (slash >= 0 && slash < root.length() - 1) {
            shortId = root.substring(slash + 1);
        }
        if (shortId.length() > 64) {
            shortId = shortId.substring(0, 64);
        }
        String action = request.getAction().trim().toUpperCase();
        audit.record("GIT_" + action, "GIT", shortId,
                action + " " + elapsed + "ms · " + root);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", result.success());
        body.put("action", action);
        body.put("workspacePath", request.getWorkspacePath().trim());
        body.put("repositoryRoot", result.repositoryRoot());
        body.put("output", result.output());
        body.put("elapsedMs", elapsed);
        body.put("message", result.success() ? "执行成功" : "执行失败");
        if (!result.success()) {
            throw new IllegalStateException("Git 执行失败：\n" + result.output());
        }
        return body;
    }

    /**
     * Git 仓库探测请求：指定本地工作区路径。
     */
    public static class GitProbeRequest {
        @NotBlank
        @Size(max = 512)
        private String workspacePath;

        /**
         * @return 工作区绝对或相对路径
         */
        public String getWorkspacePath() {
            return workspacePath;
        }

        /**
         * @param workspacePath 工作区路径
         */
        public void setWorkspacePath(String workspacePath) {
            this.workspacePath = workspacePath;
        }
    }

    /**
     * Git 查询/拉取请求：动作限于 STATUS、LOG、DIFF、SHOW、BRANCH、PULL。
     */
    public static class GitQueryRequest {
        @NotBlank
        @Size(max = 512)
        private String workspacePath;

        @NotBlank
        @Pattern(regexp = "(?i)^(STATUS|LOG|DIFF|SHOW|BRANCH|PULL)$")
        private String action;

        @Size(max = 256)
        private String pathOrRef;

        private Integer maxCommits;
        private Boolean fullDiff;

        /**
         * @return 工作区路径
         */
        public String getWorkspacePath() {
            return workspacePath;
        }

        /**
         * @param workspacePath 工作区路径
         */
        public void setWorkspacePath(String workspacePath) {
            this.workspacePath = workspacePath;
        }

        /**
         * @return Git 动作名
         */
        public String getAction() {
            return action;
        }

        /**
         * @param action Git 动作名
         */
        public void setAction(String action) {
            this.action = action;
        }

        /**
         * @return 可选路径或引用（如文件路径、commit）
         */
        public String getPathOrRef() {
            return pathOrRef;
        }

        /**
         * @param pathOrRef 路径或引用
         */
        public void setPathOrRef(String pathOrRef) {
            this.pathOrRef = pathOrRef;
        }

        /**
         * @return LOG 最大提交数，可空
         */
        public Integer getMaxCommits() {
            return maxCommits;
        }

        /**
         * @param maxCommits 最大提交数
         */
        public void setMaxCommits(Integer maxCommits) {
            this.maxCommits = maxCommits;
        }

        /**
         * @return 是否输出完整 diff
         */
        public Boolean getFullDiff() {
            return fullDiff;
        }

        /**
         * @param fullDiff 是否完整 diff
         */
        public void setFullDiff(Boolean fullDiff) {
            this.fullDiff = fullDiff;
        }
    }
}
