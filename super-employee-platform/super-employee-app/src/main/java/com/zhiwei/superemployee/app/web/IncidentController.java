package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.runtime.LocalRepairGateway;
import com.zhiwei.superemployee.app.runtime.ReactAgentRuntimeService;
import com.zhiwei.superemployee.app.runtime.RepairArtifactService;
import com.zhiwei.superemployee.app.security.OpsDataPermissionService;
import com.zhiwei.superemployee.core.domain.AgentStepRecord;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.repository.IncidentRepository;
import com.zhiwei.superemployee.core.workflow.IncidentWorkflow;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

/**
 * 故障任务 HTTP 入口：创建、流转、审批、CodeX 修复评审与产物下载。
 * <p>
 * 路径前缀 {@code /api/incidents}。菜单权限：管理员或 {@code OPS_DASHBOARD}。
 * 读写均经 {@link OpsDataPermissionService} 隔离；创建人记为当前登录用户。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/incidents")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('OPS_DASHBOARD')")
public class IncidentController {

    private final IncidentWorkflow workflow;
    private final IncidentRepository incidents;
    private final LocalRepairGateway localRepair;
    private final ReactAgentRuntimeService runtime;
    private final RepairArtifactService artifacts;
    private final OpsDataPermissionService dataPermission;

    /**
     * @param workflow       故障工作流
     * @param incidents      故障仓储
     * @param localRepair    本地修复网关（CodeX / Cursor）
     * @param runtime        ReAct 运行时（修复评审后续续跑）
     * @param artifacts      修复产物/部署脚本下载
     * @param dataPermission 运维数据权限
     */
    public IncidentController(IncidentWorkflow workflow, IncidentRepository incidents,
                              LocalRepairGateway localRepair, ReactAgentRuntimeService runtime,
                              RepairArtifactService artifacts, OpsDataPermissionService dataPermission) {
        this.workflow = workflow;
        this.incidents = incidents;
        this.localRepair = localRepair;
        this.runtime = runtime;
        this.artifacts = artifacts;
        this.dataPermission = dataPermission;
    }

    /**
     * 创建故障任务，创建人取当前登录用户。
     *
     * @param request 标题、描述、优先级
     * @return 持久化后的故障任务
     */
    @PostMapping
    public IncidentTask create(@Valid @RequestBody IncidentRequest request) {
        IncidentTask task = workflow.create(request.getTitle(), request.getDescription(), request.getPriority());
        task.setCreatedBy(dataPermission.currentUsername());
        return incidents.save(task);
    }

    /**
     * 触发故障工作流运行（分析/处置流水线）。
     *
     * @param id 故障主键
     * @return 运行后的任务；不存在时 404
     */
    @PostMapping("/{id}/run")
    public ResponseEntity<IncidentTask> run(@PathVariable String id) {
        dataPermission.requireIncident(id);
        IncidentTask task = workflow.run(id);
        return task == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(task);
    }

    /**
     * 按主键查询故障详情（含上下文与完整领域对象）。
     *
     * @param id 故障主键
     * @return 故障任务；无权限或不存在时由权限服务抛错
     */
    @GetMapping("/{id}")
    public ResponseEntity<IncidentTask> get(@PathVariable String id) {
        return ResponseEntity.ok(dataPermission.requireIncident(id));
    }

    /**
     * 在工作流层批准故障（与审批中心降级路径语义一致）。
     *
     * @param id      故障主键
     * @param request 审批意见
     * @return 更新后的任务；不存在时 404
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<IncidentTask> approve(@PathVariable String id, @Valid @RequestBody DecisionRequest request) {
        dataPermission.requireIncident(id);
        IncidentTask task = workflow.approve(id, request.getComment());
        return task == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(task);
    }

    /**
     * 在工作流层驳回故障。
     *
     * @param id      故障主键
     * @param request 驳回原因
     * @return 更新后的任务；不存在时 404
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<IncidentTask> reject(@PathVariable String id, @Valid @RequestBody RejectRequest request) {
        dataPermission.requireIncident(id);
        IncidentTask task = workflow.reject(id, request.getReason());
        return task == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(task);
    }

    /**
     * 列出当前用户可见的故障；可按运维任务过滤，或返回摘要字段以减轻列表载荷。
     *
     * @param opsTaskId 可选，按关联运维任务过滤（会先校验该运维任务访问权）
     * @param summary   true 时仅返回摘要 Map，否则返回完整 IncidentTask
     * @return 故障列表或摘要列表
     */
    @GetMapping
    public List<?> list(@RequestParam(required = false) String opsTaskId,
                        @RequestParam(defaultValue = "false") boolean summary) {
        if (opsTaskId != null && !opsTaskId.isBlank()) {
            dataPermission.requireOpsTask(opsTaskId);
        }
        var tasks = dataPermission.visibleIncidents().stream()
                .filter(task -> opsTaskId == null || opsTaskId.isBlank()
                        || opsTaskId.equals(String.valueOf(task.getContext().get("opsTaskId"))));
        return summary ? tasks.map(this::summary).toList() : tasks.toList();
    }

    /**
     * 将故障折叠为列表摘要（含精简 context，仅保留 CodeX 评审状态等关键字段）。
     *
     * @param task 故障任务
     * @return 摘要 Map
     */
    private Map<String, Object> summary(IncidentTask task) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", task.getId());
        result.put("createdAt", task.getCreatedAt());
        result.put("title", task.getTitle());
        result.put("description", task.getDescription());
        result.put("priority", task.getPriority());
        result.put("status", task.getStatus());
        result.put("latestSummary", task.getLatestSummary());
        result.put("opsTaskId", task.getContext().get("opsTaskId"));
        Map<String, Object> context = new LinkedHashMap<>();
        if (task.getContext().containsKey("codexReviewStatus")) {
            context.put("codexReviewStatus", task.getContext().get("codexReviewStatus"));
        }
        result.put("context", context);
        return result;
    }

    /**
     * 返回故障 Agent 时间线步骤列表。
     *
     * @param id 故障主键
     * @return 时间线记录列表
     */
    @GetMapping("/{id}/timeline")
    public ResponseEntity<?> timeline(@PathVariable String id) {
        return ResponseEntity.ok(dataPermission.requireIncident(id).getTimeline());
    }

    /**
     * 接受 CodeX 生成的修复方案，并在成功后继续 ReAct 会话。
     *
     * @param id 故障主键
     * @return 含 success/status/message/incident 的结果体
     */
    @PostMapping("/{id}/repair/accept")
    public ResponseEntity<?> acceptRepair(@PathVariable String id) throws IOException {
        return reviewRepair(id, true);
    }

    /**
     * 撤销 CodeX 修复方案，并在成功后通知运行时继续。
     *
     * @param id 故障主键
     * @return 含 success/status/message/incident 的结果体
     */
    @PostMapping("/{id}/repair/revert")
    public ResponseEntity<?> revertRepair(@PathVariable String id) throws IOException {
        return reviewRepair(id, false);
    }

    /**
     * 对 CodeX 修复执行接受或撤销，写回上下文与时间线，并驱动运行时续跑。
     *
     * @param id     故障主键
     * @param accept true 接受，false 撤销
     * @return 评审结果与最新故障
     */
    private ResponseEntity<?> reviewRepair(String id, boolean accept) throws IOException {
        IncidentTask task = dataPermission.requireIncident(id);
        String workspace = String.valueOf(task.getContext().getOrDefault("workspacePath", ""));
        String repairId = String.valueOf(task.getContext().getOrDefault("codexRepairId", ""));
        LocalRepairGateway.ReviewResult result = localRepair.review(workspace, repairId, accept);
        if (result.success()) {
            task.getContext().put("codexReviewStatus", result.status());
            task.getContext().put("codexReviewedAt", Instant.now().toString());
            String engine = String.valueOf(task.getContext().getOrDefault("repairEngine", localRepair.engineDisplayName()));
            task.getTimeline().add(new AgentStepRecord(
                    accept ? "接受 " + engine + " 修复" : "撤销 " + engine + " 修复",
                    true, result.message(), Instant.now()));
            incidents.save(task);
            runtime.continueAfterCodexReview(task, accept);
        }
        return ResponseEntity.ok(Map.of(
                "success", result.success(),
                "status", result.status(),
                "message", result.message(),
                "incident", task));
    }

    /**
     * 下载当前故障关联的修复产物（如 jar）为附件流。
     *
     * @param id 故障主键
     * @return 二进制附件响应
     */
    @GetMapping("/{id}/artifact/download")
    public ResponseEntity<Resource> downloadArtifact(@PathVariable String id) {
        IncidentTask task = dataPermission.requireIncident(id);
        Resource resource = artifacts.downloadableArtifact(task);
        String filename = resource.getFilename() == null ? "repair.jar" : resource.getFilename();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build().toString())
                .body(resource);
    }

    /** 制品生成后确认是否合并运维分支到主分支。 */
    @PostMapping("/{id}/branch-merge/decide")
    public ResponseEntity<?> decideBranchMerge(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> request) {
        IncidentTask task = dataPermission.requireIncident(id);
        boolean merge = request != null && Boolean.TRUE.equals(request.get("merge"));
        IncidentTask updated = runtime.decideBranchMerge(task, merge);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", updated.getLatestSummary(),
                "incident", updated));
    }

    /**
     * 下载当前故障生成的 Java 备份、启动和一键部署脚本压缩包。
     *
     * @param id 故障主键
     * @return zip 附件响应
     */
    @GetMapping("/{id}/deployment-scripts/download")
    public ResponseEntity<Resource> downloadDeploymentScripts(@PathVariable String id) {
        IncidentTask task = dataPermission.requireIncident(id);
        Resource resource = artifacts.downloadableDeploymentScripts(task);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("java-deployment-scripts.zip", StandardCharsets.UTF_8)
                                .build().toString())
                .body(resource);
    }

    /**
     * 人工确认产物部署结果，驱动运行时完成手动部署步骤。
     *
     * @param id      故障主键
     * @param request 可选，含 success、note
     * @return 含 success/message/incident 的结果体
     */
    @PostMapping("/{id}/artifact/complete")
    public ResponseEntity<?> completeArtifactVerification(
            @PathVariable String id, @RequestBody(required = false) Map<String, Object> request) {
        IncidentTask task = dataPermission.requireIncident(id);
        boolean successful = request != null && Boolean.TRUE.equals(request.get("success"));
        String note = request == null ? "" : String.valueOf(request.getOrDefault("note", ""));
        IncidentTask completed = runtime.completeManualDeployment(task, successful, note);
        return ResponseEntity.ok(Map.of(
                "success", successful,
                "message", completed.getLatestSummary(),
                "incident", completed));
    }

    /**
     * 简易健康检查，供探活或联调确认服务可用。
     *
     * @return status=ok 与服务名
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "service", "super-employee");
    }
}
