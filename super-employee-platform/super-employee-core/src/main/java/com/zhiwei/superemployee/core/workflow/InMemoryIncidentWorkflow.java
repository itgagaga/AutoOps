package com.zhiwei.superemployee.core.workflow;

import com.zhiwei.superemployee.core.agent.LogExtractionAgent;
import com.zhiwei.superemployee.core.domain.AgentResult;
import com.zhiwei.superemployee.core.domain.AgentStepRecord;
import com.zhiwei.superemployee.core.domain.IncidentPriority;
import com.zhiwei.superemployee.core.domain.IncidentStatus;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.IncidentModelGateway;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
import com.zhiwei.superemployee.core.gateway.InMemoryIncidentModelGateway;
import com.zhiwei.superemployee.core.gateway.InMemoryIncidentToolGateway;
import com.zhiwei.superemployee.core.repository.IncidentRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/**
 * 基于仓储与可插拔网关的内存工作流实现。
 * <p>
 * {@link #run} 串联日志提取、模型根因分析、补丁草案、工具预览与验证，最终进入审批等待；
 * {@link #approve}/{@link #reject} 完成人工门禁后的部署或拒绝收尾。默认使用内存模型/工具网关，
 * 也可注入真实 {@link IncidentModelGateway}/{@link IncidentToolGateway}。
 * @author yangdb
 */
public class InMemoryIncidentWorkflow implements IncidentWorkflow {

    /** 故障任务持久化仓储。 */
    private final IncidentRepository repository;
    /** 根因分析与补丁草案模型网关。 */
    private final IncidentModelGateway modelGateway;
    /** 补丁预览、验证与部署工具网关。 */
    private final IncidentToolGateway toolGateway;
    /** 分诊阶段使用的日志提取 Agent。 */
    private final LogExtractionAgent logExtractionAgent = new LogExtractionAgent();

    /**
     * 使用内存模型网关与内存工具网关构造工作流。
     *
     * @param repository 故障任务仓储
     */
    public InMemoryIncidentWorkflow(IncidentRepository repository) {
        this(repository, new InMemoryIncidentModelGateway(), new InMemoryIncidentToolGateway());
    }

    /**
     * 注入自定义模型与工具网关构造工作流。
     *
     * @param repository   故障任务仓储
     * @param modelGateway 模型分析网关
     * @param toolGateway  工具执行网关
     */
    public InMemoryIncidentWorkflow(IncidentRepository repository, IncidentModelGateway modelGateway, IncidentToolGateway toolGateway) {
        this.repository = repository;
        this.modelGateway = modelGateway;
        this.toolGateway = toolGateway;
    }

    /**
     * 创建并持久化新的待处置事件。
     *
     * @param title       故障标题
     * @param description 故障描述
     * @param priority    优先级枚举名
     * @return 已保存的故障任务
     */
    @Override
    public IncidentTask create(String title, String description, String priority) {
        IncidentTask task = new IncidentTask(title, description, IncidentPriority.valueOf(priority));
        return repository.save(task);
    }

    /**
     * 执行事件的诊断、补丁预览和验证流程，结束后状态为 {@link IncidentStatus#APPROVAL_PENDING}。
     *
     * @param id 故障任务标识
     * @return 更新后的任务；不存在时返回 {@code null}
     */
    @Override
    public IncidentTask run(String id) {
        IncidentTask task = get(id);
        if (task == null) {
            return null;
        }

        task.setStatus(IncidentStatus.TRIAGED);
        append(task, logExtractionAgent.execute(task));

        task.setStatus(IncidentStatus.DIAGNOSED);
        IncidentModelGateway.AnalysisResult analysisResult = modelGateway.analyze(task);
        task.getContext().put("rootCause", analysisResult.getRootCause());
        task.getContext().put("evidence", analysisResult.getEvidence());
        append(task, new AgentResult("model-analysis", true, analysisResult.getSummary()));

        task.setStatus(IncidentStatus.PATCH_READY);
        IncidentModelGateway.PatchPlan patchPlan = modelGateway.draftPatch(task, analysisResult);
        task.getContext().put("patchTitle", patchPlan.getTitle());
        task.getContext().put("patchPreview", patchPlan.getDiffPreview());
        append(task, new AgentResult("patch-draft", true, patchPlan.getSummary()));

        task.setStatus(IncidentStatus.PATCH_READY);
        IncidentToolGateway.ToolResult previewResult = toolGateway.previewPatch(task, patchPlan);
        task.getContext().put("previewOutput", previewResult.getOutput());
        append(task, new AgentResult("tool-preview", previewResult.isSuccess(), previewResult.getOutput()));

        task.setStatus(IncidentStatus.TESTING);
        IncidentToolGateway.ToolResult verifyResult = toolGateway.runVerification(task, patchPlan);
        task.getContext().put("verificationOutput", verifyResult.getOutput());
        append(task, new AgentResult("tool-verification", verifyResult.isSuccess(), verifyResult.getOutput()));

        task.setStatus(IncidentStatus.APPROVAL_PENDING);
        task.setLatestSummary("basic flow completed, awaiting approval");
        repository.save(task);
        return task;
    }

    /**
     * 记录审批意见并执行补丁部署；成功则 {@link IncidentStatus#RESOLVED}，否则 {@link IncidentStatus#FAILED}。
     *
     * @param id      故障任务标识
     * @param comment 审批意见
     * @return 更新后的任务；不存在时返回 {@code null}
     */
    @Override
    public IncidentTask approve(String id, String comment) {
        IncidentTask task = get(id);
        if (task == null) {
            return null;
        }
        task.getContext().put("approvalComment", comment);
        task.setStatus(IncidentStatus.DEPLOYING);
        task.getTimeline().add(new AgentStepRecord("approval", true, comment, Instant.now()));
        IncidentModelGateway.PatchPlan patchPlan = new IncidentModelGateway.PatchPlan(
                String.valueOf(task.getContext().getOrDefault("patchTitle", "unknown-patch")),
                String.valueOf(task.getContext().getOrDefault("patchPreview", "")),
                "approved patch"
        );
        IncidentToolGateway.ToolResult deployResult = toolGateway.deploy(task, patchPlan);
        task.getContext().put("deployOutput", deployResult.getOutput());
        task.getTimeline().add(new AgentStepRecord("deployment", deployResult.isSuccess(), deployResult.getOutput(), Instant.now()));
        task.setLatestSummary("approval granted, deployment completed");
        task.setStatus(deployResult.isSuccess() ? IncidentStatus.RESOLVED : IncidentStatus.FAILED);
        repository.save(task);
        return task;
    }

    /**
     * 记录拒绝原因并将状态置为 {@link IncidentStatus#REJECTED}。
     *
     * @param id     故障任务标识
     * @param reason 拒绝原因
     * @return 更新后的任务；不存在时返回 {@code null}
     */
    @Override
    public IncidentTask reject(String id, String reason) {
        IncidentTask task = get(id);
        if (task == null) {
            return null;
        }
        task.getContext().put("rejectionReason", reason);
        task.setStatus(IncidentStatus.REJECTED);
        task.getTimeline().add(new AgentStepRecord("rejection", false, reason, Instant.now()));
        task.setLatestSummary("approval rejected");
        repository.save(task);
        return task;
    }

    /**
     * 按标识查询事件，不存在时返回空值。
     *
     * @param id 故障任务标识
     * @return 任务实体，或 {@code null}
     */
    @Override
    public IncidentTask get(String id) {
        return repository.findById(id).orElse(null);
    }

    /**
     * 获取按创建时间倒序排列的全部事件。
     *
     * @return 任务列表
     */
    @Override
    public List<IncidentTask> list() {
        return repository.findAll();
    }

    /**
     * 将智能体执行结果写入事件上下文和时间线，并刷新 {@code latestSummary}。
     *
     * @param task   目标故障任务
     * @param result Agent 执行结果
     */
    private void append(IncidentTask task, AgentResult result) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("agent", result.getStep());
        log.put("success", result.isSuccess());
        log.put("summary", result.getSummary());
        task.getContext().put(result.getStep(), log);
        task.getTimeline().add(new AgentStepRecord(result.getStep(), result.isSuccess(), result.getSummary(), Instant.now()));
        task.setLatestSummary(result.getSummary());
    }
}
