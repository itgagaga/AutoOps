package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.ops.DeploymentPathDiscoveryService;
import com.zhiwei.superemployee.app.ops.DeploymentTarget;
import com.zhiwei.superemployee.app.ops.OpsTaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运维任务 HTTP 入口。
 * <p>
 * 创建后可启用；启用时派生故障任务并调度 Agent。列表与单条访问在服务层按创建人隔离。
 * 部署目录浏览/识别仅允许对资源中心中已启用的主机操作，避免对不存在或停用主机发起 SSH。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/ops-tasks")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('OPS_DASHBOARD')")
public class OpsTaskController {
    private final OpsTaskService service;
    private final DeploymentPathDiscoveryService deploymentPaths;

    /**
     * @param service         运维任务应用服务
     * @param deploymentPaths 远端部署路径发现服务
     */
    public OpsTaskController(OpsTaskService service, DeploymentPathDiscoveryService deploymentPaths) {
        this.service = service;
        this.deploymentPaths = deploymentPaths;
    }

    /**
     * 创建运维任务，创建人取当前登录用户。
     *
     * @param request 创建请求体
     * @return 201 + 任务视图，参数非法时 400
     */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateOpsTaskRequest request) {
        try {
            Map<String, Object> created = service.create(
                    request.getTitle(), request.getDescription(), request.getPriority(),
                    request.getHostId(), request.getWorkspacePath(), request.getFrontendWorkspacePath(),
                    request.getBackendKind(), request.getClientKind(), request.getClientVerifyCommand(),
                    request.getBackendVerifyCommand(), request.getClientDeployPath(),
                    request.getClientDistDir(), request.getDockerComposeFile(), request.getDockerImageName(),
                    request.getRepairEngine(),
                    request.getApplicationCode(), request.getRepositoryUrl(), request.getOwnerName(),
                    Boolean.TRUE.equals(request.getAutoDeploy()), request.getDeploymentPath(),
                    request.getDeploymentPaths(), request.getDeploymentJar(),
                    toDeploymentTargets(request.getDeploymentTargets()),
                    Boolean.TRUE.equals(request.getScheduleEnabled()),
                    request.getPullIntervalMinutes() == null ? 30 : request.getPullIntervalMinutes(),
                    request.getLogRangeMinutes() == null ? 30 : request.getLogRangeMinutes(),
                    request.getLogTemplate(), request.getLogPath(), request.getLogServiceName(),
                    request.getAutoAnalyze() == null || Boolean.TRUE.equals(request.getAutoAnalyze()));
            return ResponseEntity.created(URI.create("/api/ops-tasks/" + created.get("id"))).body(created);
        }
        catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 启用或停用运维任务；启用时触发故障派生与分析调度。
     *
     * @param id      任务主键
     * @param request 启用状态
     * @return 更新后任务视图；不存在时 404
     */
    @PutMapping("/{id}/enabled")
    public ResponseEntity<?> setEnabled(@PathVariable String id,
                                        @Valid @RequestBody AgentEnabledRequest request) {
        try {
            return ResponseEntity.ok(service.setEnabled(id, Boolean.TRUE.equals(request.getEnabled())));
        }
        catch (IllegalArgumentException ex) {
            if ("运维任务不存在".equals(ex.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
        catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 更新运维任务配置并按需重启分析流程。
     *
     * @param id      任务主键
     * @param request 更新内容
     * @return 更新后任务视图
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable String id,
                                    @Valid @RequestBody CreateOpsTaskRequest request) {
        try {
            return ResponseEntity.ok(service.updateAndRestart(
                    id, request.getTitle(), request.getDescription(), request.getPriority(),
                    request.getHostId(), request.getWorkspacePath(), request.getFrontendWorkspacePath(),
                    request.getBackendKind(), request.getClientKind(), request.getClientVerifyCommand(),
                    request.getBackendVerifyCommand(), request.getClientDeployPath(),
                    request.getClientDistDir(), request.getDockerComposeFile(), request.getDockerImageName(),
                    request.getRepairEngine(),
                    request.getApplicationCode(), request.getRepositoryUrl(), request.getOwnerName(),
                    Boolean.TRUE.equals(request.getAutoDeploy()), request.getDeploymentPath(),
                    request.getDeploymentPaths(), request.getDeploymentJar(),
                    toDeploymentTargets(request.getDeploymentTargets()),
                    Boolean.TRUE.equals(request.getScheduleEnabled()),
                    request.getPullIntervalMinutes() == null ? 30 : request.getPullIntervalMinutes(),
                    request.getLogRangeMinutes() == null ? 30 : request.getLogRangeMinutes(),
                    request.getLogTemplate(), request.getLogPath(), request.getLogServiceName(),
                    request.getAutoAnalyze() == null || Boolean.TRUE.equals(request.getAutoAnalyze())));
        }
        catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 列出当前用户可见的运维任务。
     *
     * @return 任务视图列表
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        return service.list();
    }

    /**
     * 浏览目标主机上的远端目录，仅暴露子目录信息供部署候选选择。
     * <p>主机不存在返回 404；主机未启用或路径非法返回 400。</p>
     *
     * @param hostId 资源中心主机 ID
     * @param path   远端绝对路径，默认 {@code /}
     * @return 目录列表或错误消息
     */
    @GetMapping("/deployment-directories")
    public ResponseEntity<?> deploymentDirectories(
            @RequestParam String hostId,
            @RequestParam(defaultValue = "/") String path) {
        try {
            return ResponseEntity.ok(deploymentPaths.listDirectories(hostId, path));
        }
        catch (IllegalArgumentException ex) {
            if ("主机不存在".equals(ex.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
        catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 根据运行进程、标准目录结构和 JAR 名称，在候选路径中识别唯一实际部署目录。
     * <p>主机不存在返回 404；主机未启用、应用编码或路径非法返回 400。</p>
     *
     * @param request 主机、应用编码与候选路径
     * @return 识别结果或错误消息
     */
    @PostMapping("/deployment-paths/detect")
    public ResponseEntity<?> detectDeploymentPath(
            @Valid @RequestBody DetectDeploymentPathRequest request) {
        try {
            return ResponseEntity.ok(deploymentPaths.detect(
                    request.hostId(), request.applicationCode(), request.deploymentPaths()));
        }
        catch (IllegalArgumentException ex) {
            if ("主机不存在".equals(ex.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
        catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 获取单条运维任务详情；无权或不存在统一 404。
     *
     * @param id 任务主键
     * @return 任务视图
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        try {
            return ResponseEntity.ok(service.get(id));
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
        catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(message(ex.getMessage()));
        }
    }

    /**
     * 对已启用任务重新触发分析调度。
     *
     * @param id 任务主键
     * @return 202 + 任务视图，或 404/400
     */
    @PostMapping("/{id}/restart-analysis")
    public ResponseEntity<?> restartAnalysis(@PathVariable String id) {
        try {
            return ResponseEntity.accepted().body(service.restartAnalysis(id));
        }
        catch (IllegalArgumentException ex) {
            if ("运维任务不存在".equals(ex.getMessage())) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
        catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(message(ex.getMessage()));
        }
    }

    /**
     * 删除运维任务；存在进行中约束时返回 409。
     *
     * @param id 任务主键
     * @return 204 无内容，或不存在 404 / 冲突 409
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        try {
            service.delete(id);
            return ResponseEntity.noContent().build();
        }
        catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }
        catch (IllegalStateException ex) {
            return ResponseEntity.status(409).body(message(ex.getMessage()));
        }
    }

    /**
     * 将请求中的多部署目标转为领域对象列表。
     */
    private List<DeploymentTarget> toDeploymentTargets(
            List<CreateOpsTaskRequest.DeploymentTargetRequest> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<DeploymentTarget> result = new ArrayList<>(items.size());
        for (CreateOpsTaskRequest.DeploymentTargetRequest item : items) {
            if (item == null || item.getPath() == null || item.getPath().isBlank()) {
                continue;
            }
            String artifact = item.getArtifact() != null && !item.getArtifact().isBlank()
                    ? item.getArtifact().trim()
                    : (item.getJar() == null || item.getJar().isBlank() ? null : item.getJar().trim());
            result.add(new DeploymentTarget(item.getPath().trim(), artifact));
        }
        return result;
    }

    /**
     * 构造简单错误消息体。
     *
     * @param value 错误文本
     * @return {@code {"message": "..."}}
     */
    private Map<String, String> message(String value) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", value);
        return body;
    }
}
