package com.zhiwei.superemployee.app.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiwei.superemployee.app.ops.OpsTaskRepository;
import com.zhiwei.superemployee.app.ops.OpsTaskStatus;
import com.zhiwei.superemployee.app.knowledge.KnowledgeService;
import com.zhiwei.superemployee.core.domain.AgentStepRecord;
import com.zhiwei.superemployee.core.domain.IncidentStatus;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
import com.zhiwei.superemployee.core.repository.IncidentRepository;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.AgentStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 基于 AgentScope React Agent 的运维运行时实现。
 * <p>
 * 负责创建/恢复会话、工具审批闭环、CodeX 直连修复、制品构建与部署协同，
 * 以及失败分类重试与运维任务状态回写。仅在 {@code super-employee.react.enabled=true}
 * 时注册为 {@link AgentRuntimeService} 实现。
 * </p>
 * @author yangdb
 */
@Service
@ConditionalOnProperty(prefix = "super-employee.react", name = "enabled", havingValue = "true")
public class ReactAgentRuntimeService implements AgentRuntimeService {
    private static final Logger log = LoggerFactory.getLogger(ReactAgentRuntimeService.class);
    private final AgentSessionRepository sessions;
    private final PendingAgentActionRepository pendingActions;
    private final ReactAgentFactory agentFactory;
    private final IncidentRepository incidents;
    private final ObjectMapper objectMapper;
    private final AgentStateStore stateStore;
    private final AgentLeaseManager leases;
    private final AgentFailureClassifier failureClassifier;
    private final IncidentLifecycleService lifecycle;
    private final CollaborativeAgentBridge collaborativeBridge;
    private final LocalGitRepositoryGateway gitRepository;
    private final RoutingLocalRepairGateway localRepair;
    private final RepairArtifactService artifacts;
    private final PatchDeploymentFacade deploymentService;
    private final ObjectProvider<OpsTaskRepository> opsTasks;
    private final ObjectProvider<KnowledgeService> knowledge;
    private final int maxAttempts;
    private final long initialBackoffSeconds;
    private final long maxBackoffSeconds;
    private final ExecutorService repairReviewExecutor =
            Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "codex-review-worker");
                thread.setDaemon(true);
                return thread;
            });
    /** sessionId → 修复确认后异步构建/部署任务，取消会话时可中断。 */
    private final ConcurrentHashMap<String, Future<?>> repairReviewJobs = new ConcurrentHashMap<>();

    /**
     * 构造 React Agent 运行时服务。
     *
     * @param sessions               Agent 会话仓储
     * @param pendingActions         待审批动作仓储
     * @param agentFactory           React Agent 工厂
     * @param incidents              故障任务仓储
     * @param objectMapper           JSON 序列化
     * @param stateStore             AgentScope 状态存储
     * @param leases                 会话租约管理器
     * @param failureClassifier      失败可重试性分类器
     * @param lifecycle              故障生命周期服务
     * @param collaborativeBridge    个体智能体会话桥接
     * @param gitRepository          本地 Git 网关
     * @param localRepair            本地修复网关路由（CodeX / Cursor）
     * @param artifacts              JAR 制品构建服务
     * @param deploymentService      Java 补丁部署服务
     * @param opsTasks               运维任务仓储（可选）
     * @param knowledge              知识沉淀服务（可选）
     * @param maxAttempts            单会话最大尝试次数
     * @param initialBackoffSeconds  重试初始退避秒数
     * @param maxBackoffSeconds      重试最大退避秒数
     */
    public ReactAgentRuntimeService(
            AgentSessionRepository sessions,
            PendingAgentActionRepository pendingActions,
            ReactAgentFactory agentFactory,
            IncidentRepository incidents,
            ObjectMapper objectMapper,
            AgentStateStore stateStore,
            AgentLeaseManager leases,
            AgentFailureClassifier failureClassifier,
            IncidentLifecycleService lifecycle,
            CollaborativeAgentBridge collaborativeBridge,
            LocalGitRepositoryGateway gitRepository,
            RoutingLocalRepairGateway localRepair,
            RepairArtifactService artifacts,
            PatchDeploymentFacade deploymentService,
            ObjectProvider<OpsTaskRepository> opsTasks,
            ObjectProvider<KnowledgeService> knowledge,
            @Value("${super-employee.recovery.max-attempts:3}") int maxAttempts,
            @Value("${super-employee.recovery.initial-backoff-seconds:2}") long initialBackoffSeconds,
            @Value("${super-employee.recovery.max-backoff-seconds:30}") long maxBackoffSeconds) {
        this.sessions = sessions;
        this.pendingActions = pendingActions;
        this.agentFactory = agentFactory;
        this.incidents = incidents;
        this.objectMapper = objectMapper;
        this.stateStore = stateStore;
        this.leases = leases;
        this.failureClassifier = failureClassifier;
        this.lifecycle = lifecycle;
        this.collaborativeBridge = collaborativeBridge;
        this.gitRepository = gitRepository;
        this.localRepair = localRepair;
        this.artifacts = artifacts;
        this.deploymentService = deploymentService;
        this.opsTasks = opsTasks;
        this.knowledge = knowledge;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffSeconds = Math.max(1, initialBackoffSeconds);
        this.maxBackoffSeconds = Math.max(this.initialBackoffSeconds, maxBackoffSeconds);
    }

    /**
     * 创建故障会话并开始首次 Agent 执行。
     *
     * @param task 待调查的故障任务
     * @return 执行后的会话快照（可能已成功、失败或等待审批）
     */
    @Override
    public AgentSession start(IncidentTask task) {
        String objective = "调查运维事件：" + task.getTitle();
        AgentSession session = sessions.create(
                task.getId(), ReactAgentFactory.AGENT_CODE, objective, maxAttempts);
        sessions.appendTurn(session.getId(), "OBJECTIVE", objective);
        lifecycle.begin(task);
        task.setLatestSummary("AgentScope 运维团队正在调查该事件");
        incidents.save(task);
        return execute(session, task, new UserMessage(buildPrompt(task)), false);
    }

    /**
     * 从租约超时或可重试失败中安全恢复会话。
     * <p>若存在未完成的有副作用工具则停止自动恢复；若有待审批工具则重建审批态；否则从 Checkpoint 续跑。</p>
     *
     * @param session 待恢复会话
     * @return 恢复后的会话快照
     */
    public AgentSession recover(AgentSession session) {
        IncidentTask task = incidents.findById(session.getIncidentId()).orElseThrow();
        String stage = String.valueOf(task.getContext().getOrDefault("codexRepairStage", ""));
        if ("LOCAL_REPAIR_RUNNING".equals(stage) || "BUILDING_ARTIFACT".equals(stage)) {
            log.info("跳过恢复：本地修复/制品构建仍在后台执行 incidentId={} sessionId={} stage={}",
                    task.getId(), session.getId(), stage);
            return sessions.findById(session.getId()).orElse(session);
        }
        if (sessions.hasUnfinishedNonRetryableToolCall(session.getId())) {
            AgentLeaseManager.Lease lease = leases.acquire(session);
            try {
                String error = "实例中断时有副作用工具仍处于 STARTED 状态，已停止自动恢复，请人工核查";
                sessions.appendTurn(session.getId(), "ERROR", error);
                    lifecycle.markFailed(task);
                task.setLatestSummary(error);
                incidents.save(task);
                return finishCoordinatorSession(session, AgentSessionStatus.FAILED, null, error);
            }
            finally {
                lease.close();
            }
        }
        List<ToolUseBlock> pendingTools = findPendingToolCalls(session);
        if (!pendingTools.isEmpty()) {
            AgentLeaseManager.Lease lease = leases.acquire(session);
            try {
                ReactAgentFactory.AgentHandle handle = agentFactory.create(task, lease.getSession());
                ensurePendingActions(lease.getSession(), task, handle, pendingTools);
                sessions.updateStatus(session.getId(), AgentSessionStatus.WAITING_APPROVAL);
                task.setLatestSummary("已从 Checkpoint 恢复待审批工具操作");
                incidents.save(task);
                return sessions.findById(session.getId()).orElseThrow();
            }
            finally {
                lease.close();
            }
        }
        AgentLeaseManager.Lease lease = leases.acquire(session);
        try {
            task.setLatestSummary("检测到执行中断，正在从数据库 Checkpoint 恢复");
            incidents.save(task);
            sessions.appendTurn(session.getId(), "RECOVERY",
                    "实例已接管超时或待重试会话，并从 Checkpoint 恢复执行");
            return executeWithLease(lease, task,
                    new UserMessage("上次执行因瞬时故障或实例中断而停止。请基于已保存的上下文安全地继续任务，"
                            + "不要重复已经完成的有副作用操作。"));
        }
        finally {
            lease.close();
        }
    }

    /**
     * 批准待执行的高风险工具操作。
     *
     * @param actionId 待审批动作 ID
     * @param comment  审批意见
     * @param operator 操作人
     * @return 更新后的会话；若仍有待批动作则保持等待审批
     */
    @Override
    public AgentSession approveAction(String actionId, String comment, String operator) {
        return decide(actionId, "APPROVED", comment, operator);
    }

    /**
     * 拒绝待执行的高风险工具操作。
     *
     * @param actionId 待审批动作 ID
     * @param reason   拒绝原因
     * @param operator 操作人
     * @return 更新后的会话
     */
    @Override
    public AgentSession rejectAction(String actionId, String reason, String operator) {
        return decide(actionId, "REJECTED", reason, operator);
    }

    /**
     * 保存审批决定；当本会话全部待批动作均已决策后恢复 Agent 执行。
     *
     * @param actionId 待审批动作 ID
     * @param decision {@code APPROVED} 或 {@code REJECTED}
     * @param comment  说明文字
     * @param operator 操作人
     * @return 会话快照
     * @throws IllegalStateException 动作不存在
     */
    private AgentSession decide(String actionId, String decision, String comment, String operator) {
        pendingActions.findById(actionId).orElseThrow(() -> new IllegalStateException("待审批操作不存在"));
        PendingAgentAction decided = pendingActions.decide(actionId, decision, comment, operator);
        IncidentTask task = incidents.findById(decided.getIncidentId()).orElseThrow();
        if ("APPROVED".equals(decision)) {
            lifecycle.approvalGranted(task, decided);
        }
        else {
            lifecycle.approvalRejected(task, decided.getToolCode());
        }
        task.getTimeline().add(new AgentStepRecord(
                "工具审批",
                "APPROVED".equals(decision),
                decided.getToolCode() + ("APPROVED".equals(decision) ? " 已批准" : " 已拒绝"),
                Instant.now()));
        incidents.save(task);
        String decisionText = "APPROVED".equals(decision) ? "已批准" : "已拒绝";
        sessions.appendTurn(decided.getSessionId(), "APPROVAL",
                decided.getToolCode() + " " + decisionText + "，操作人员：" + operator + "，说明：" + comment);

        List<PendingAgentAction> sessionActions = pendingActions.findBySession(decided.getSessionId());
        if (sessionActions.stream().anyMatch(item -> "PENDING".equals(item.getStatus()))) {
            return sessions.findById(decided.getSessionId()).orElseThrow();
        }
        return resume(decided.getSessionId(), sessionActions);
    }

    /**
     * 将审批结果注入恢复消息（或用户提示）并继续执行 Agent。
     *
     * @param sessionId 会话 ID
     * @param actions   本会话已决策的审批动作列表
     * @return 续跑后的会话快照
     */
    private AgentSession resume(String sessionId, List<PendingAgentAction> actions) {
        AgentSession session = sessions.findById(sessionId).orElseThrow();
        IncidentTask task = incidents.findById(session.getIncidentId()).orElseThrow();
        List<ToolUseBlock> toolCalls = loadPendingToolCalls(session);
        if (toolCalls.isEmpty()) {
            String decisions = actions.stream()
                    .map(action -> action.getToolCode() + "=" + action.getStatus())
                    .reduce((left, right) -> left + "，" + right)
                    .orElse("无待处理操作");
            task.setLatestSummary("审批结果已记录，正在恢复智能体总结");
            incidents.save(task);
            return execute(session, task, new UserMessage(
                    "操作人员已处理审批请求：" + decisions + "。请遵守处理结果，不要重复被拒绝的操作，并总结当前结论。"), true);
        }

        List<ConfirmResult> confirmations = toolCalls.stream().map(toolCall -> {
            PendingAgentAction action = actions.stream()
                    .filter(item -> toolCall.getId().equals(item.getToolCallId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("审批反馈信息不完整"));
            return new ConfirmResult("APPROVED".equals(action.getStatus()), toolCall);
        }).toList();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(Msg.METADATA_CONFIRM_RESULTS, confirmations);
        Msg resumeMessage = Msg.builder()
                .name("operator")
                .role(MsgRole.USER)
                .textContent("操作人员已提交待审批操作的处理决定")
                .metadata(metadata)
                .build();

        task.setLatestSummary("操作人员完成审批后，AgentScope 运维团队已恢复执行");
        incidents.save(task);
        return execute(session, task, resumeMessage, true);
    }

    /**
     * 加载会话 Checkpoint 中处于等待确认状态的工具调用。
     *
     * @param session Agent 会话
     * @return 待审批工具调用列表，可能为空
     */
    private List<ToolUseBlock> loadPendingToolCalls(AgentSession session) {
        return findPendingToolCalls(session);
    }

    /**
     * 从持久化 AgentState 中过滤状态为 {@code ASKING} 的工具调用。
     *
     * @param session Agent 会话
     * @return 待确认工具调用列表
     */
    private List<ToolUseBlock> findPendingToolCalls(AgentSession session) {
        Optional<AgentState> state = stateStore.get(session.getIncidentId(), session.getThreadId(),
                "agent_state", AgentState.class);
        if (state.isEmpty()) {
            return List.of();
        }
        return state.orElseThrow().getContext().stream()
                .flatMap(message -> message.getContent().stream())
                .filter(ToolUseBlock.class::isInstance)
                .map(ToolUseBlock.class::cast)
                .filter(tool -> tool.getState() == ToolCallState.ASKING)
                .toList();
    }

    /**
     * 为尚未入库的待审批工具调用创建审批动作记录。
     * <p>部署类工具会额外绑定补丁指纹与部署键。</p>
     *
     * @param session   Agent 会话
     * @param task      故障任务
     * @param handle    当前 Agent 句柄（用于风险等级）
     * @param toolCalls 待审批工具调用
     */
    private void ensurePendingActions(AgentSession session, IncidentTask task,
                                      ReactAgentFactory.AgentHandle handle,
                                      List<ToolUseBlock> toolCalls) {
        List<String> existingCallIds = pendingActions.findBySession(session.getId()).stream()
                .map(PendingAgentAction::getToolCallId)
                .toList();
        for (ToolUseBlock toolCall : toolCalls) {
            if (!existingCallIds.contains(toolCall.getId())) {
                String arguments = writeJson(toolCall.getInput());
                IncidentLifecycleService.ApprovalBinding binding = null;
                if (ReactAgentFactory.DEPLOY_PATCH.equals(toolCall.getName())) {
                    if (task.getStatus() != IncidentStatus.TESTING
                            && task.getStatus() != IncidentStatus.APPROVAL_PENDING) {
                        throw new IllegalStateException("部署请求过早：当前事件处于 "
                                + task.getStatus() + "，需要 TESTING 才能申请部署审批");
                    }
                    binding = lifecycle.requestDeploymentApproval(task,
                            stringValue(toolCall.getInput(), "title"),
                            stringValue(toolCall.getInput(), "diffPreview"),
                            stringValue(toolCall.getInput(), "summary"));
                }
                pendingActions.create(session.getId(), task.getId(), toolCall.getId(), toolCall.getName(),
                        arguments, handle.getCapabilities().riskLevel(toolCall.getName()),
                        "AgentScope 请求执行工具：" + toolCall.getName(),
                        binding == null ? null : binding.patchFingerprint(),
                        binding == null ? null : binding.deploymentKey());
            }
        }
    }

    /**
     * 获取会话租约后调用智能体，并处理响应或故障。
     *
     * @param session      当前会话
     * @param task         故障任务
     * @param message      发给 Agent 的消息
     * @param continuation 是否为审批/恢复后的续跑（影响租约获取方式）
     * @return 处理后的会话快照
     */
    private AgentSession execute(AgentSession session, IncidentTask task, Msg message,
                                 boolean continuation) {
        AgentLeaseManager.Lease lease = continuation
                ? leases.acquireContinuation(session) : leases.acquire(session);
        try {
            return executeWithLease(lease, task, message);
        }
        finally {
            lease.close();
        }
    }

    /**
     * 在已持有租约的前提下执行 Agent 调用（供 recover 先抢租约再写副作用）。
     *
     * @param lease   已获取的租约
     * @param task    故障任务
     * @param message 发给 Agent 的消息
     * @return 处理后的会话快照
     */
    private AgentSession executeWithLease(AgentLeaseManager.Lease lease, IncidentTask task, Msg message) {
        AgentSession running = lease.getSession();
        try {
            if (!lease.isActive()) {
                throw new IllegalStateException("会话租约已失效，已中止执行");
            }
            ReactAgentFactory.AgentHandle handle = agentFactory.create(task, running);
            Msg response = handle.getAgent().call(message, context(running)).block();
            if (!lease.isActive()) {
                log.warn("租约续约失败，丢弃迟到响应 incidentId={} sessionId={}",
                        task.getId(), running.getId());
                return sessions.findById(running.getId()).orElse(running);
            }
            return processResponse(running, task, handle, response, lease);
        }
        catch (Exception ex) {
            return handleFailure(running, task, ex);
        }
    }

    /**
     * 处理 Agent 响应：权限请求则暂停审批；需要 CodeX 则推进修复流水线；否则完结会话。
     *
     * @param session  会话
     * @param task     故障任务
     * @param handle   Agent 句柄
     * @param response Agent 返回消息，允许为 {@code null}
     * @param lease    当前持有的租约
     * @return 更新后的会话
     */
    private AgentSession processResponse(AgentSession session, IncidentTask task,
                                         ReactAgentFactory.AgentHandle handle, Msg response,
                                         AgentLeaseManager.Lease lease) throws IOException {
        AgentSession latestSession = sessions.findById(session.getId()).orElse(session);
        if (latestSession.getStatus() == AgentSessionStatus.CANCELLED
                || latestSession.getStatus() == AgentSessionStatus.TIMEOUT) {
            log.info("忽略已终止会话的迟到响应 incidentId={} sessionId={} status={}",
                    task.getId(), session.getId(), latestSession.getStatus());
            return latestSession;
        }
        if (!lease.isActive()) {
            log.warn("租约已失效，跳过响应副作用 incidentId={} sessionId={}",
                    task.getId(), session.getId());
            return latestSession;
        }
        if (response != null && response.getGenerateReason() == GenerateReason.PERMISSION_ASKING) {
            if (hasPrematureDeployAsk(response, task)) {
                String nextTool = nextRequiredRepairTool(session.getId(), handle, task);
                if (nextTool != null && lease.isActive()) {
                    log.warn("忽略过早的 DEPLOY_PATCH 审批请求，继续修复链路 incidentId={} status={} next={}",
                            task.getId(), task.getStatus(), nextTool);
                    sessions.appendTurn(session.getId(), "CONTINUE",
                            "部署请求过早（当前 " + task.getStatus() + "），已改为继续执行 " + nextTool);
                    task.setLatestSummary("部署请求过早，自动修复流程继续执行：" + nextTool);
                    incidents.save(task);
                    Msg continued = handle.getAgent().call(new UserMessage(
                            repairContinuationPrompt(nextTool, task)
                                    + "不要调用 DEPLOY_PATCH，当前尚未完成验证。"),
                            context(session)).block();
                    return processResponse(session, task, handle, continued, lease);
                }
                return finishMissingCodex(session, task,
                        "部署请求过早：当前事件仍处于 " + task.getStatus()
                                + "，须先完成补丁预览、本地修复与验证");
            }
            return pauseForApproval(session, task, handle, response);
        }
        if (codexRequired(task)) {
            if (!opsBranchReady(task)) {
                return finishMissingCodex(session, task,
                        "运维分支未准备成功，已禁止在错误分支调用 "
                                + RepairEngineLabels.cliName(task) + "："
                                + String.valueOf(task.getContext().getOrDefault("opsBranchResult", "未知原因")));
            }
            if (!handle.getCapabilities().getAllowedTools().contains(ReactAgentFactory.CODEX_LOCAL_REPAIR)) {
                return finishMissingCodex(session, task,
                        "自动修复任务未获得 CODEX_LOCAL_REPAIR 工具授权，不能将调查建议当作已完成");
            }
            String nextTool = nextRequiredRepairTool(session.getId(), handle, task);
            if (ReactAgentFactory.CODEX_LOCAL_REPAIR.equals(nextTool)) {
                if (!lease.isActive()) {
                    return sessions.findById(session.getId()).orElse(session);
                }
                enqueueLocalRepair(session, task);
                return sessions.findById(session.getId()).orElse(session);
            }
            if (isCodexReviewPending(task)) {
                return pauseForCodexReview(session, task);
            }
            if (ReactAgentFactory.TEST_VERIFICATION.equals(nextTool)) {
                // 仅认「最近一次成功本地修复之后」的验证失败；修复前模型抢跑的验证不阻断流水线
                Optional<AgentToolCall> failedVerification = latestFailedVerificationAfterCodex(session.getId());
                if (failedVerification.isPresent()) {
                    return finishMissingCodex(session, task,
                            "代码验证未通过：" + failedVerification.orElseThrow().getResultSummary());
                }
            }
            if (nextTool != null) {
                int continuationCount = repairContinuationCount(task);
                if (continuationCount >= 6) {
                    return finishMissingCodex(session, task,
                            "自动修复流程续跑已达到上限，仍未成功完成 " + nextTool
                                    + "；请检查对应工具的最近一次调用结果");
                }
                if (!lease.isActive()) {
                    return sessions.findById(session.getId()).orElse(session);
                }
                task.getContext().put("codexContinuationCount", continuationCount + 1);
                task.getContext().put("codexRepairStage", nextTool);
                task.setLatestSummary("自动修复流程正在继续执行：" + nextTool);
                log.info("自动修复阶段继续 incidentId={} sessionId={} stage={} attempt={}",
                        task.getId(), session.getId(), nextTool, continuationCount + 1);
                incidents.save(task);
                sessions.appendTurn(session.getId(), "CONTINUE",
                        "自动修复阶段尚未完成；运行时要求继续执行 " + nextTool);
                Msg continued = handle.getAgent().call(new UserMessage(
                        repairContinuationPrompt(nextTool, task)),
                        context(session)).block();
                return processResponse(session, task, handle, continued, lease);
            }
            // 制品/合并/人工部署仍在等人确认时，禁止提前 COMPLETED + 知识沉淀
            if (isRepairPipelinePaused(task)) {
                incidents.save(task);
                return sessions.findById(session.getId()).orElse(session);
            }
            task.getContext().put("codexRepairStage", "COMPLETED");
            log.info("自动修复阶段全部完成 incidentId={} sessionId={}", task.getId(), session.getId());
        }
        String summary = response == null ? "" : response.getTextContent();
        if (summary == null || summary.isBlank()) {
            summary = "AgentScope 运维团队已完成任务，但未返回文字摘要";
        }
        sessions.appendTurn(session.getId(), "FINAL", summary);
        task.setLatestSummary(summary);
        task.getContext().put("agentSessionId", session.getId());
        task.getTimeline().add(new AgentStepRecord("最终结论", true, summary, Instant.now()));
        incidents.save(task);
        syncOpsTask(task, OpsTaskStatus.COMPLETED, summary);
        depositKnowledge(task, summary);
        return finishCoordinatorSession(session, AgentSessionStatus.SUCCEEDED, summary, null);
    }

    /** 修复流水线处于后台构建或等人确认时，不得提前完结会话/沉淀知识。 */
    private boolean isRepairPipelinePaused(IncidentTask task) {
        String stage = String.valueOf(task.getContext().getOrDefault("codexRepairStage", ""));
        return "LOCAL_REPAIR_RUNNING".equals(stage)
                || "BUILDING_ARTIFACT".equals(stage)
                || "WAITING_BRANCH_MERGE".equals(stage)
                || "WAITING_MANUAL_DEPLOYMENT".equals(stage)
                || "WAITING_REVIEW".equals(stage)
                || isCodexReviewPending(task);
    }

    /**
     * 故障闭环后写入知识库；失败只记时间线，不影响运维完结结果。
     */
    private void depositKnowledge(IncidentTask task, String summary) {
        KnowledgeService knowledgeService = knowledge.getIfAvailable();
        if (knowledgeService == null) {
            return;
        }
        try {
            knowledgeService.recordIncidentResolution(task, resolveKnowledgeSummary(task, summary));
        }
        catch (RuntimeException ex) {
            log.warn("知识沉淀失败 incidentId={}: {}", task.getId(), ex.getMessage(), ex);
            String detail = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? ex.getClass().getSimpleName()
                    : ex.getMessage();
            task.getTimeline().add(new AgentStepRecord(
                    "知识沉淀", false,
                    "知识库写入失败：" + detail + "（未影响本次处置结果）", Instant.now()));
            incidents.save(task);
        }
    }

    /** 合并/部署完结摘要可能过短，用修复上下文补齐，便于知识沉淀。 */
    private String resolveKnowledgeSummary(IncidentTask task, String summary) {
        String base = summary == null ? "" : summary.trim();
        if (base.length() >= 30) {
            return base;
        }
        StringBuilder enriched = new StringBuilder(base);
        Object[] parts = {
                task.getContext().get("patchTitle"),
                task.getContext().get("patchSummary"),
                task.getContext().get("rootCause"),
                task.getContext().get("topHypothesis"),
                task.getLatestSummary()
        };
        for (Object part : parts) {
            if (part == null) {
                continue;
            }
            String text = String.valueOf(part).trim();
            if (text.isBlank() || enriched.indexOf(text) >= 0) {
                continue;
            }
            if (enriched.length() > 0) {
                enriched.append('；');
            }
            enriched.append(text);
            if (enriched.length() >= 30) {
                break;
            }
        }
        if (enriched.length() < 30) {
            if (enriched.length() > 0) {
                enriched.append('；');
            }
            enriched.append("本次故障已完成修复与验证，沉淀为可复用运维经验。");
        }
        return enriched.toString();
    }

    /**
     * 判断故障上下文是否要求走自动 CodeX 修复流程。
     *
     * @param task 故障任务
     * @return {@code true} 表示必须完成 CodeX 修复链路
     */
    private boolean codexRequired(IncidentTask task) {
        Object required = task.getContext().get("codexRequired");
        return Boolean.TRUE.equals(required) || "true".equalsIgnoreCase(String.valueOf(required));
    }

    /**
     * 判断运维修复分支是否已准备成功。
     *
     * @param task 故障任务
     * @return {@code true} 表示可在运维分支上执行修复
     */
    private boolean opsBranchReady(IncidentTask task) {
        Object ready = task.getContext().get("opsBranchReady");
        return Boolean.TRUE.equals(ready) || "true".equalsIgnoreCase(String.valueOf(ready));
    }

    /**
     * 检查会话审计轨迹中指定工具是否已有成功调用。
     *
     * @param sessionId 会话 ID
     * @param toolCode  工具编码
     * @return 存在成功调用时为 {@code true}
     */
    private boolean hasSuccessfulToolCall(String sessionId, String toolCode) {
        if (ReactAgentFactory.TEST_VERIFICATION.equals(toolCode)) {
            return hasSuccessfulVerificationAfterCodex(sessionId);
        }
        return sessions.findToolCalls(sessionId).stream()
                .anyMatch(call -> toolCode.equals(call.getToolCode())
                        && "SUCCEEDED".equalsIgnoreCase(call.getStatus()));
    }

    /**
     * 最近一次成功的本地修复之后，是否已有成功的代码验证。
     */
    private boolean hasSuccessfulVerificationAfterCodex(String sessionId) {
        List<AgentToolCall> calls = sessions.findToolCalls(sessionId);
        int lastCodex = indexOfLastSuccessful(calls, ReactAgentFactory.CODEX_LOCAL_REPAIR);
        if (lastCodex < 0) {
            return false;
        }
        for (int index = lastCodex + 1; index < calls.size(); index++) {
            AgentToolCall call = calls.get(index);
            if (ReactAgentFactory.TEST_VERIFICATION.equals(call.getToolCode())
                    && isVerificationToolSucceeded(call)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 最近一次成功本地修复之后的验证失败（忽略修复前模型抢跑产生的失败记录）。
     */
    private Optional<AgentToolCall> latestFailedVerificationAfterCodex(String sessionId) {
        List<AgentToolCall> calls = sessions.findToolCalls(sessionId);
        int lastCodex = indexOfLastSuccessful(calls, ReactAgentFactory.CODEX_LOCAL_REPAIR);
        if (lastCodex < 0) {
            return Optional.empty();
        }
        for (int index = calls.size() - 1; index > lastCodex; index--) {
            AgentToolCall call = calls.get(index);
            if (!ReactAgentFactory.TEST_VERIFICATION.equals(call.getToolCode())) {
                continue;
            }
            if ("FAILED".equalsIgnoreCase(call.getStatus()) || isVerificationPayloadFailed(call)) {
                return Optional.of(call);
            }
        }
        return Optional.empty();
    }

    private static int indexOfLastSuccessful(List<AgentToolCall> calls, String toolCode) {
        for (int index = calls.size() - 1; index >= 0; index--) {
            AgentToolCall call = calls.get(index);
            if (toolCode.equals(call.getToolCode())
                    && "SUCCEEDED".equalsIgnoreCase(call.getStatus())) {
                return index;
            }
        }
        return -1;
    }

    /**
     * 工具调用层标记成功，且结果 JSON 未声明 success=false。
     */
    private boolean isVerificationToolSucceeded(AgentToolCall call) {
        if (!"SUCCEEDED".equalsIgnoreCase(call.getStatus())) {
            return false;
        }
        return !isVerificationPayloadFailed(call);
    }

    /**
     * {@code TEST_VERIFICATION} 常以 SUCCEEDED 落库但 payload 含 {@code "success":false}。
     */
    private boolean isVerificationPayloadFailed(AgentToolCall call) {
        String summary = call.getResultSummary();
        if (summary == null || summary.isBlank()) {
            return false;
        }
        String normalized = summary.replace(" ", "");
        if (normalized.contains("\"success\":false")) {
            return true;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(summary, Map.class);
            Object success = payload.get("success");
            if (success instanceof Boolean bool) {
                return !bool;
            }
            if (success != null) {
                return !Boolean.parseBoolean(String.valueOf(success));
            }
        }
        catch (Exception ignored) {
            // 非 JSON 摘要时仅按 status / 字面量判断
        }
        return false;
    }

    /**
     * 按补丁预览 → CodeX 修复 → 测试验证顺序返回下一阶段必需工具。
     *
     * @param sessionId 会话 ID
     * @param handle    Agent 句柄（用于判断是否授权验证工具）
     * @return 下一工具编码；全部完成时为 {@code null}
     */
    private String nextRequiredRepairTool(String sessionId, ReactAgentFactory.AgentHandle handle) {
        if (!hasSuccessfulToolCall(sessionId, ReactAgentFactory.PATCH_PREVIEW)) {
            return ReactAgentFactory.PATCH_PREVIEW;
        }
        if (!hasSuccessfulToolCall(sessionId, ReactAgentFactory.CODEX_LOCAL_REPAIR)) {
            return ReactAgentFactory.CODEX_LOCAL_REPAIR;
        }
        if (handle.getCapabilities().getAllowedTools().contains(ReactAgentFactory.TEST_VERIFICATION)
                && !hasSuccessfulToolCall(sessionId, ReactAgentFactory.TEST_VERIFICATION)) {
            return ReactAgentFactory.TEST_VERIFICATION;
        }
        return null;
    }

    private String nextRequiredRepairTool(String sessionId, ReactAgentFactory.AgentHandle handle,
                                          IncidentTask task) {
        String stage = String.valueOf(task.getContext().getOrDefault("codexRepairStage", ""));
        if ("LOCAL_REPAIR_RUNNING".equals(stage)
                || "BUILDING_ARTIFACT".equals(stage)
                || "WAITING_BRANCH_MERGE".equals(stage)
                || "WAITING_REVIEW".equals(stage)
                || task.getContext().get("artifactPath") != null
                || isCodexReviewPending(task)) {
            return null;
        }
        return nextRequiredRepairTool(sessionId, handle);
    }

    private boolean hasPrematureDeployAsk(Msg response, IncidentTask task) {
        if (task.getStatus() == IncidentStatus.TESTING
                || task.getStatus() == IncidentStatus.APPROVAL_PENDING
                || Boolean.TRUE.equals(task.getContext().get("verificationPassed"))) {
            return false;
        }
        return response.getContent().stream()
                .filter(ToolUseBlock.class::isInstance)
                .map(ToolUseBlock.class::cast)
                .filter(tool -> tool.getState() == ToolCallState.ASKING)
                .anyMatch(tool -> ReactAgentFactory.DEPLOY_PATCH.equals(tool.getName()));
    }

    /**
     * 判断 CodeX 修复是否已产生差异并等待人工确认。
     *
     * @param task 故障任务
     * @return {@code true} 表示处于修复确认待办
     */
    private boolean isCodexReviewPending(IncidentTask task) {
        return task.getContext().get("codexRepairId") != null
                && "PENDING".equals(String.valueOf(task.getContext().get("codexReviewStatus")));
    }

    /** 本地修复已成功且无需再等人审（含幂等：CLI 成功但无新增差异）。 */
    private boolean isCodexRepairAccepted(IncidentTask task) {
        return "ACCEPTED".equals(String.valueOf(task.getContext().get("codexReviewStatus")));
    }

    /**
     * CodeX 已产生真实差异后暂停会话，确认前不再推进后续分析与测试。
     *
     * @param session 会话
     * @param task    故障任务
     * @return 置为等待输入后的会话
     */
    private AgentSession pauseForCodexReview(AgentSession session, IncidentTask task) {
        String engineLabel = RepairEngineLabels.shortName(task);
        String message = engineLabel + " 修复已完成，等待操作人员确认代码差异；确认前暂停后续分析与测试";
        sessions.appendTurn(session.getId(), "REPAIR_REVIEW", message);
        sessions.updateStatus(session.getId(), AgentSessionStatus.WAITING_INPUT);
        task.setLatestSummary(message);
        task.getContext().put("agentSessionId", session.getId());
        task.getContext().put("codexRepairStage", "WAITING_REVIEW");
        task.getTimeline().add(new AgentStepRecord("修复确认", true, message, Instant.now()));
        incidents.save(task);
        syncOpsTask(task, OpsTaskStatus.WAITING_APPROVAL, message);
        log.info("{} 修复等待确认 incidentId={} sessionId={} repairId={}",
                engineLabel, task.getId(), session.getId(), task.getContext().get("codexRepairId"));
        return sessions.findById(session.getId()).orElseThrow();
    }

    /** 将 Codex/Cursor/Claude Code 本地修复移交后台线程，避免阻塞当前 Agent 调用。 */
    private void enqueueLocalRepair(AgentSession session, IncidentTask task) {
        String sessionId = session.getId();
        Future<?> existing = repairReviewJobs.get(sessionId);
        if (existing != null && !existing.isDone()) {
            return;
        }
        task.getContext().put("codexRepairStage", "LOCAL_REPAIR_RUNNING");
        task.setLatestSummary(RepairEngineLabels.shortName(task) + " 已在后台开始修复，可继续查看其他任务");
        task.getTimeline().add(new AgentStepRecord(
                "本地代码修复", true, "修复任务已提交后台执行", Instant.now()));
        incidents.save(task);
        sessions.appendTurn(sessionId, "REPAIR", "本地修复已移交后台执行，暂停协调 Agent 直至修复完成");
        sessions.updateStatus(sessionId, AgentSessionStatus.WAITING_INPUT);
        syncOpsTask(task, OpsTaskStatus.ANALYZING, task.getLatestSummary());

        Future<?>[] holder = new Future<?>[1];
        holder[0] = repairReviewExecutor.submit(() -> {
            try {
                IncidentTask latestTask = incidents.findById(task.getId()).orElseThrow();
                AgentSession latestSession = sessions.findById(sessionId).orElseThrow();
                if (isRepairReviewAborted(sessionId)) return;
                executeCodexRepairDirectly(latestSession, latestTask);
                latestTask = incidents.findById(task.getId()).orElse(latestTask);
                if (isCodexReviewPending(latestTask)) {
                    pauseForCodexReview(latestSession, latestTask);
                }
                else if (isCodexRepairAccepted(latestTask)) {
                    continueAfterAcceptedRepair(latestTask, sessionId);
                }
                else {
                    finishMissingCodex(latestSession, latestTask, "本地修复未产生可审阅的代码差异");
                }
            }
            catch (Exception ex) {
                incidents.findById(task.getId()).ifPresent(failedTask -> {
                    AgentSession failedSession = sessions.findById(sessionId).orElse(session);
                    finishMissingCodex(failedSession, failedTask,
                            "本地修复执行失败：" + Optional.ofNullable(ex.getMessage())
                                    .filter(message -> !message.isBlank())
                                    .orElse(ex.getClass().getSimpleName()));
                });
            }
            finally {
                repairReviewJobs.remove(sessionId, holder[0]);
            }
        });
        repairReviewJobs.put(sessionId, holder[0]);
    }

    /**
     * 接收 CodeX 修复确认结果。
     * <p>接受后异步构建 JAR 并部署/生成脚本；撤销则结束本轮，避免 HTTP 长时间阻塞。</p>
     *
     * @param task     故障任务
     * @param accepted {@code true} 接受修复，{@code false} 撤销
     * @throws IllegalStateException 会话不存在或不处于确认状态
     */
    public void continueAfterCodexReview(IncidentTask task, boolean accepted) {
        String sessionId = String.valueOf(task.getContext().getOrDefault("agentSessionId", ""));
        if (sessions.findById(sessionId).isEmpty()) {
            throw new IllegalStateException("未找到等待确认的 Agent 会话");
        }
        if (!accepted) {
            if (!sessions.finishIfStatus(sessionId, AgentSessionStatus.WAITING_INPUT,
                    AgentSessionStatus.CANCELLED, null,
                    "操作人员已撤销 " + RepairEngineLabels.shortName(task) + " 修复，本轮自动修复已结束")) {
                throw new IllegalStateException("当前会话不处于 "
                        + RepairEngineLabels.shortName(task) + " 修复确认状态，或已被其他操作处理");
            }
            collaborativeBridge.closeOrphanSpecialists(task.getId(), false);
            String message = "操作人员已撤销 " + RepairEngineLabels.shortName(task) + " 修复，本轮自动修复已结束";
            sessions.appendTurn(sessionId, "REPAIR_REVIEW", message);
            lifecycle.markFailed(task);
            task.setLatestSummary(message);
            incidents.save(task);
            syncOpsTask(task, OpsTaskStatus.FAILED, message);
            return;
        }
        // CAS：WAITING_INPUT → RUNNING，防止双点重复触发构建/部署
        if (!sessions.compareAndSetStatus(sessionId, AgentSessionStatus.WAITING_INPUT,
                AgentSessionStatus.RUNNING)) {
            throw new IllegalStateException("当前会话不处于 "
                    + RepairEngineLabels.shortName(task) + " 修复确认状态，或构建已在进行中");
        }
        String message = "操作人员已接受 " + RepairEngineLabels.shortName(task)
                + " 修复，正在执行 Maven package 生成部署验证 JAR";
        enqueueArtifactBuild(task, sessionId, message);
    }

    /**
     * 本地修复已自动接受（含幂等无新差异）后，跳过人工确认直接构建 JAR。
     */
    private void continueAfterAcceptedRepair(IncidentTask task, String sessionId) {
        String engineLabel = RepairEngineLabels.shortName(task);
        boolean idempotent = Boolean.parseBoolean(String.valueOf(
                task.getContext().getOrDefault("codexRepairIdempotent", false)));
        String message = idempotent
                ? engineLabel + " 判定工作区无需新增改动，已跳过差异确认，正在执行 Maven package 生成部署验证 JAR"
                : engineLabel + " 修复已自动接受，正在执行 Maven package 生成部署验证 JAR";
        task.getTimeline().add(new AgentStepRecord("修复确认", true, message, Instant.now()));
        incidents.save(task);
        runArtifactBuild(task, sessionId, message);
    }

    private void enqueueArtifactBuild(IncidentTask task, String sessionId, String message) {
        sessions.appendTurn(sessionId, "REPAIR_REVIEW", message);
        task.setLatestSummary(message);
        task.getContext().put("codexRepairStage", "BUILDING_ARTIFACT");
        incidents.save(task);
        syncOpsTask(task, OpsTaskStatus.ANALYZING, message);
        Future<?>[] jobHolder = new Future<?>[1];
        jobHolder[0] = repairReviewExecutor.submit(() -> {
            try {
                runArtifactBuild(task, sessionId, null);
            }
            finally {
                repairReviewJobs.remove(sessionId, jobHolder[0]);
            }
        });
        Future<?> previous = repairReviewJobs.put(sessionId, jobHolder[0]);
        if (previous != null && !previous.isDone()) {
            previous.cancel(true);
        }
    }

    /**
     * 执行 Maven package 与部署/脚本生成。{@code startMessage} 非空时先写入时间线（同线程续跑）。
     */
    private void runArtifactBuild(IncidentTask task, String sessionId, String startMessage) {
        try {
            if (isRepairReviewAborted(sessionId)) {
                log.info("修复确认后异步任务已中止（会话已取消） sessionId={}", sessionId);
                return;
            }
            IncidentTask latestTask = incidents.findById(task.getId()).orElseThrow();
            if (startMessage != null && !startMessage.isBlank()) {
                sessions.appendTurn(sessionId, "REPAIR_REVIEW", startMessage);
                latestTask.setLatestSummary(startMessage);
                latestTask.getContext().put("codexRepairStage", "BUILDING_ARTIFACT");
                incidents.save(latestTask);
                syncOpsTask(latestTask, OpsTaskStatus.ANALYZING, startMessage);
            }
            RepairArtifactService.BuildResult result = artifacts.build(latestTask);
            if (isRepairReviewAborted(sessionId)) {
                return;
            }
            latestTask.getContext().put("artifactBuildOutput", result.output());
            if (!result.success() || result.artifact() == null) {
                String error = "JAR 制品构建失败：" + result.output();
                lifecycle.markFailed(latestTask);
                latestTask.setLatestSummary(error);
                latestTask.getTimeline().add(new AgentStepRecord(
                        "JAR 制品构建", false, error, Instant.now()));
                incidents.save(latestTask);
                syncOpsTask(latestTask, OpsTaskStatus.FAILED, error);
                finishCoordinatorSession(sessionId, latestTask.getId(),
                        AgentSessionStatus.FAILED, null, error);
                return;
            }
            Path artifact = result.artifact().toAbsolutePath().normalize();
            long artifactSize = Files.size(artifact);
            latestTask.getContext().put("artifactPath", artifact.toString());
            latestTask.getContext().put("artifactName", artifact.getFileName().toString());
            latestTask.getContext().put("artifactSize", artifactSize);
            latestTask.getTimeline().add(new AgentStepRecord(
                    "JAR 制品构建", true,
                    artifactTimelineSummary(artifact, artifactSize), Instant.now(),
                    AgentStepRecord.artifactView(latestTask.getContext())));

            if (isRepairReviewAborted(sessionId)) {
                return;
            }
            boolean autoDeploy = Boolean.parseBoolean(
                    String.valueOf(latestTask.getContext().getOrDefault("autoDeploy", false)));
            var deployment = deploymentService.deploy(latestTask);
            if (isRepairReviewAborted(sessionId)) {
                return;
            }
            latestTask.getContext().put("javaDeploymentOutput", deployment.getOutput());
            latestTask.getTimeline().add(new AgentStepRecord(
                    autoDeploy ? "Java 自动部署" : "一键部署脚本生成",
                    deployment.isSuccess(),
                    deploymentTimelineSummary(artifact, autoDeploy, deployment), Instant.now(),
                    AgentStepRecord.artifactView(latestTask.getContext())));
            if (!deployment.isSuccess()) {
                String error = deployment.getOutput();
                latestTask.getContext().put("codexRepairStage", "DEPLOYMENT_FAILED");
                lifecycle.markFailed(latestTask);
                latestTask.setLatestSummary(error);
                incidents.save(latestTask);
                syncOpsTask(latestTask, OpsTaskStatus.FAILED, error);
                finishCoordinatorSession(sessionId, latestTask.getId(),
                        AgentSessionStatus.FAILED, null, error);
                return;
            }

            lifecycle.artifactReady(latestTask);
            latestTask.getContext().put("codexRepairStage", "WAITING_BRANCH_MERGE");
            String ready = "部署制品已生成，请确认是否将运维分支合并到主分支：" + artifact.getFileName();
            latestTask.setLatestSummary(ready);
            incidents.save(latestTask);
            sessions.updateStatus(sessionId, AgentSessionStatus.WAITING_INPUT);
            syncOpsTask(latestTask, OpsTaskStatus.WAITING_APPROVAL, ready);
        }
        catch (Exception ex) {
            if (isRepairReviewAborted(sessionId)) {
                return;
            }
            log.error("CodeX 确认后构建 JAR 失败 incidentId={} sessionId={}",
                    task.getId(), sessionId, ex);
            incidents.findById(task.getId()).ifPresent(failedTask -> {
                String error = "JAR 制品构建异常：" + Optional.ofNullable(ex.getMessage())
                        .filter(value -> !value.isBlank())
                        .orElse(ex.getClass().getSimpleName());
                try {
                    lifecycle.markFailed(failedTask);
                }
                catch (RuntimeException ignored) {
                    failedTask.setStatus(com.zhiwei.superemployee.core.domain.IncidentStatus.FAILED);
                }
                failedTask.setLatestSummary(error);
                failedTask.getTimeline().add(new AgentStepRecord(
                        "JAR 制品构建", false, error, Instant.now()));
                incidents.save(failedTask);
                syncOpsTask(failedTask, OpsTaskStatus.FAILED, error);
                finishCoordinatorSession(sessionId, failedTask.getId(),
                        AgentSessionStatus.FAILED, null, error);
            });
        }
    }

    private static String artifactTimelineSummary(Path artifact, long size) {
        return "JAR：" + artifact.getFileName()
                + "（" + formatArtifactSize(size) + "）\n路径：" + artifact;
    }

    private static String deploymentTimelineSummary(Path artifact, boolean autoDeploy,
                                                    IncidentToolGateway.ToolResult deployment) {
        String header = (autoDeploy ? "已部署 JAR：" : "待部署 JAR：") + artifact.getFileName();
        String output = deployment.getOutput() == null ? "" : deployment.getOutput().trim();
        if (output.isBlank()) {
            return header;
        }
        if (output.length() > 600) {
            output = output.substring(0, 600) + "…";
        }
        return header + "\n" + output;
    }

    private static String formatArtifactSize(long size) {
        if (size < 1024) {
            return size + " B";
        }
        if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        }
        return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }

    /**
     * 异步构建/部署过程中若会话已被取消则中止后续副作用。
     *
     * @param sessionId 会话 ID
     * @return {@code true} 应中止
     */
    private boolean isRepairReviewAborted(String sessionId) {
        return sessions.findById(sessionId)
                .map(session -> session.getStatus() == AgentSessionStatus.CANCELLED
                        || session.getStatus() == AgentSessionStatus.TIMEOUT
                        || session.getStatus() == AgentSessionStatus.FAILED
                        || session.getStatus() == AgentSessionStatus.SUCCEEDED)
                .orElse(true);
    }

    /** 处理制品生成后的主分支合并确认；选择暂不合并时保留稳定运维分支供下次复用。 */
    public IncidentTask decideBranchMerge(IncidentTask task, boolean merge) {
        IncidentTask latest = incidents.findById(task.getId()).orElse(task);
        if (!"WAITING_BRANCH_MERGE".equals(String.valueOf(latest.getContext().get("codexRepairStage")))) {
            throw new IllegalStateException("当前事件不在主分支合并确认阶段");
        }
        String sessionId = String.valueOf(latest.getContext().getOrDefault("agentSessionId", ""));
        if (!sessionId.isBlank() && !sessions.compareAndSetStatus(sessionId,
                AgentSessionStatus.WAITING_INPUT, AgentSessionStatus.RUNNING)) {
            throw new IllegalStateException("合并确认已处理或正在处理中");
        }
        String message;
        if (merge) {
            @SuppressWarnings("unchecked")
            List<String> changedFiles = latest.getContext().get("codexChangedFiles") instanceof List<?> values
                    ? values.stream().map(String::valueOf).toList() : List.of();
            LocalGitRepositoryGateway.Result result = gitRepository.mergeOperationalBranch(
                    String.valueOf(latest.getContext().getOrDefault("workspacePath", "")),
                    String.valueOf(latest.getContext().getOrDefault("opsBranch", "")), changedFiles);
            if (!result.success()) {
                if (!sessionId.isBlank()) sessions.updateStatus(sessionId, AgentSessionStatus.WAITING_INPUT);
                latest.setLatestSummary("合并主分支失败：" + result.output());
                latest.getTimeline().add(new AgentStepRecord("合并主分支", false, result.output(), Instant.now()));
                incidents.save(latest);
                throw new IllegalStateException(latest.getLatestSummary());
            }
            latest.getContext().put("branchMergeStatus", "MERGED");
            latest.getContext().put("opsBranchReady", false);
            message = result.output();
        }
        else {
            latest.getContext().put("branchMergeStatus", "SKIPPED");
            message = "本次暂不合并；运维分支将按任务复用，不会重复创建新分支";
        }
        latest.getContext().put("branchMergeDecidedAt", Instant.now().toString());
        latest.getTimeline().add(new AgentStepRecord("主分支合并确认", true, message, Instant.now()));
        boolean autoDeploy = Boolean.parseBoolean(
                String.valueOf(latest.getContext().getOrDefault("autoDeploy", false)));
        if (autoDeploy) {
            lifecycle.automaticDeploymentCompleted(latest, true);
            latest.getContext().put("codexRepairStage", "COMPLETED");
            String completed = "制品已自动部署，主分支合并确认已完成";
            latest.setLatestSummary(completed);
            incidents.save(latest);
            finishCoordinatorSession(sessionId, latest.getId(), AgentSessionStatus.SUCCEEDED, completed, null);
            syncOpsTask(latest, OpsTaskStatus.COMPLETED, completed);
            depositKnowledge(latest, completed);
        }
        else {
            latest.getContext().put("codexRepairStage", "WAITING_MANUAL_DEPLOYMENT");
            latest.setLatestSummary("合并确认已完成，可下载制品并进行人工部署验证");
            incidents.save(latest);
            if (!sessionId.isBlank()) sessions.updateStatus(sessionId, AgentSessionStatus.WAITING_INPUT);
            syncOpsTask(latest, OpsTaskStatus.WAITING_APPROVAL, latest.getLatestSummary());
        }
        return latest;
    }

    /**
     * 记录人工部署验证结果，并同步完结 Agent 会话与运维任务。
     *
     * @param task       故障任务
     * @param successful 人工验证是否通过
     * @param note       失败原因或备注，允许为空
     * @return 更新后的故障任务
     * @throws IllegalStateException 当前不处于等待人工部署验证阶段
     */
    public IncidentTask completeManualDeployment(IncidentTask task, boolean successful, String note) {
        IncidentTask latest = incidents.findById(task.getId()).orElse(task);
        if (!"WAITING_MANUAL_DEPLOYMENT".equals(
                String.valueOf(latest.getContext().get("codexRepairStage")))) {
            throw new IllegalStateException("当前事件没有等待人工部署验证的 JAR 制品");
        }
        String sessionId = String.valueOf(latest.getContext().getOrDefault("agentSessionId", ""));
        String message = successful
                ? "人工部署验证通过，运维任务已完结"
                : "人工部署验证未通过：" + (note == null || note.isBlank() ? "未填写原因" : note.trim());
        if (!sessionId.isBlank()) {
            AgentSessionStatus terminal = successful
                    ? AgentSessionStatus.SUCCEEDED : AgentSessionStatus.FAILED;
            if (!sessions.finishIfStatus(sessionId, AgentSessionStatus.WAITING_INPUT, terminal,
                    successful ? message : null, successful ? null : message)) {
                throw new IllegalStateException("人工部署验证已被处理，或会话不处于等待输入状态");
            }
            collaborativeBridge.closeOrphanSpecialists(latest.getId(), successful);
        }
        else {
            // 无会话时用阶段字段乐观锁，避免双点重复完结
            Object previous = latest.getContext().put("codexRepairStage",
                    successful ? "COMPLETED" : "MANUAL_VERIFICATION_FAILED");
            if (!"WAITING_MANUAL_DEPLOYMENT".equals(String.valueOf(previous))) {
                throw new IllegalStateException("人工部署验证已被处理");
            }
        }
        lifecycle.manualDeploymentCompleted(latest, successful);
        latest.getContext().put("manualDeploymentNote", note == null ? "" : note.trim());
        latest.getContext().put("codexRepairStage", successful ? "COMPLETED" : "MANUAL_VERIFICATION_FAILED");
        latest.setLatestSummary(message);
        latest.getTimeline().add(new AgentStepRecord(
                "人工部署验证", successful, message, Instant.now()));
        incidents.save(latest);
        syncOpsTask(latest, successful ? OpsTaskStatus.COMPLETED : OpsTaskStatus.FAILED, message);
        if (successful) {
            depositKnowledge(latest, message);
        }
        return latest;
    }

    /**
     * 容器销毁时立即关闭修复确认后的异步工作线程池。
     */
    @PreDestroy
    public void shutdownRepairReviewExecutor() {
        repairReviewExecutor.shutdownNow();
    }

    /**
     * 补丁预览完成后由运行时直接调用 CodeX CLI，不再让模型二次决定是否调用工具。
     *
     * @param session 会话
     * @param task    故障任务
     * @throws IllegalStateException CodeX 未启用、分支未就绪或修复失败
     */
    private void executeCodexRepairDirectly(AgentSession session, IncidentTask task) throws IOException {
        if (!Boolean.TRUE.equals(localRepair.status().get("activeAvailable"))) {
            throw new IllegalStateException("本地修复引擎未启用（请配置 CodeX / Cursor / Claude Code CLI）");
        }
        String operationalBranch = Optional.ofNullable(task.getContext().get("opsBranch"))
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("事件未配置运维分支，禁止在当前开发分支执行修复"));
        String configuredWorkspace = Optional.ofNullable(task.getContext().get("workspacePath"))
                .map(String::valueOf)
                .orElse(null);
        String repairWorkspace = ReactAgentFactory.resolveRepairWorkspace(task);
        if (repairWorkspace == null || repairWorkspace.isBlank()) {
            repairWorkspace = configuredWorkspace;
        }
        LocalGitRepositoryGateway.Result branchResult =
                gitRepository.ensureOperationalBranch(configuredWorkspace, operationalBranch);
        task.getContext().put("opsBranchReady", branchResult.success());
        task.getContext().put("opsBranchResult", branchResult.output());
        if (!branchResult.success()) {
            incidents.save(task);
            throw new IllegalStateException(branchResult.output());
        }
        String title = String.valueOf(task.getContext().getOrDefault("patchTitle", "运维故障自动修复"));
        String diffPreview = String.valueOf(task.getContext().getOrDefault("patchPreview", ""));
        String summary = String.valueOf(task.getContext().getOrDefault("patchSummary", ""));
        String workspacePath = repairWorkspace;
        List<String> files = List.of();
        String prompt = ReactAgentFactory.appendFrontendRepairHint(task, """
                请在当前工作区实施下面的最小修复。
                必须先扫描工作区并定位真实文件，不能假设补丁预览中的路径一定准确；
                工作区配置为目录时，应在其子目录中查找对应源码。
                只修改完成本次故障修复所必需的文件，不要提交、部署或改写无关变更。

                修复标题：%s
                修复说明：%s
                补丁预览：
                %s
                """.formatted(title, summary, diffPreview));
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("title", title);
        arguments.put("summary", summary);
        arguments.put("workspacePath", workspacePath);
        arguments.put("repairRoot", task.getContext().get("repairRoot"));
        arguments.put("frontendWorkspacePath", task.getContext().get("frontendWorkspacePath"));
        arguments.put("executionMode", "RUNTIME_DIRECT");
        Object preferenceRaw = task.getContext().get("repairEnginePreference");
        String preference = preferenceRaw == null ? RoutingLocalRepairGateway.ENGINE_AUTO : String.valueOf(preferenceRaw);
        arguments.put("repairEnginePreference", preference);
        long startedAt = System.currentTimeMillis();
        String callRecordId = sessions.startToolCall(
                session.getId(), UUID.randomUUID().toString(), ReactAgentFactory.CODEX_LOCAL_REPAIR,
                writeJson(arguments), Instant.now().toString());
        task.getContext().put("codexRepairStage", ReactAgentFactory.CODEX_LOCAL_REPAIR);
        task.setLatestSummary("正在按偏好「" + preference + "」调用本地修复引擎");
        incidents.save(task);
        log.info("本地修复运行时直连开始 preference={} incidentId={} sessionId={} workspace={}",
                preference, task.getId(), session.getId(), workspacePath);
        try {
            lifecycle.beforeCodexRepair(task, title, diffPreview, summary, files);
            RoutingLocalRepairGateway.PreferencedResult preferred =
                    localRepair.repairWithPreference(prompt, files, workspacePath, preference);
            LocalRepairGateway.Result result = preferred.result();
            task.getContext().put("codexOutput", result.output());
            task.getContext().put("codexActualDiff", result.diff());
            task.getContext().put("codexChangedFiles", result.changedFiles());
            task.getContext().put("repairEngine", preferred.engineId());
            task.getContext().put("repairEngineSwitched", preferred.switched());
            task.getContext().put("repairEngineAttempts", preferred.attemptsSummary());
            if (result.repairId() != null) {
                task.getContext().put("codexRepairId", result.repairId());
                boolean pendingReview = result.success()
                        && result.changedFiles() != null
                        && !result.changedFiles().isEmpty()
                        && result.diff() != null
                        && !result.diff().isBlank();
                task.getContext().put("codexReviewStatus",
                        result.success() ? (pendingReview ? "PENDING" : "ACCEPTED") : "FAILED");
            }
            String engineLabel = repairEngineDisplayName(preferred.engineId());
            if (!result.success()) {
                throw new IllegalStateException(result.failureMessage(engineLabel));
            }
            lifecycle.codexRepairCompleted(task, title, summary, result.diff(),
                    result.changedFiles(), result.success(), result.withinScope());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", result.success());
            response.put("output", result.output());
            response.put("diff", result.diff());
            response.put("changedFiles", result.changedFiles());
            response.put("withinScope", result.withinScope());
            response.put("repairId", result.repairId());
            response.put("repairEngine", preferred.engineId());
            response.put("repairEngineSwitched", preferred.switched());
            response.put("repairEngineAttempts", preferred.attemptsSummary());
            String resultJson = writeJson(response);
            sessions.finishToolCall(callRecordId, session.getId(), ReactAgentFactory.CODEX_LOCAL_REPAIR,
                    resultJson, "SUCCEEDED", System.currentTimeMillis() - startedAt);
            task.getContext().put("codexContinuationCount", 0);
            String stepTitle = preferred.switched()
                    ? RepairEngineLabels.repairStepTitle(preferred.engineId())
                    + "（已自动切换：" + preferred.attemptsSummary() + "）"
                    : RepairEngineLabels.repairStepTitle(preferred.engineId());
            task.getTimeline().add(new AgentStepRecord(stepTitle, true, result.output(), Instant.now()));
            incidents.save(task);
            log.info("本地修复运行时直连完成 engine={} switched={} incidentId={} sessionId={} changedFiles={}",
                    preferred.engineId(), preferred.switched(), task.getId(), session.getId(),
                    result.changedFiles().size());
        }
        catch (RuntimeException ex) {
            String message = Optional.ofNullable(ex.getMessage())
                    .filter(value -> !value.isBlank())
                    .orElse(ex.getClass().getSimpleName());
            sessions.finishToolCall(callRecordId, session.getId(), ReactAgentFactory.CODEX_LOCAL_REPAIR,
                    message, "FAILED", System.currentTimeMillis() - startedAt);
            task.getContext().put("codexOutput", message);
            incidents.save(task);
            log.error("本地修复运行时直连失败 incidentId={} sessionId={} message={}",
                    task.getId(), session.getId(), message, ex);
            throw ex;
        }
    }

    /**
     * 读取故障上下文中的自动修复续跑次数。
     *
     * @param task 故障任务
     * @return 续跑计数，缺失或非法时为 0
     */
    private int repairContinuationCount(IncidentTask task) {
        Object value = task.getContext().get("codexContinuationCount");
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        }
        catch (RuntimeException ignored) {
            return 0;
        }
    }

    /**
     * 构造强制推进指定修复阶段的用户提示词。
     *
     * @param nextTool 下一阶段工具编码
     * @param task     故障任务
     * @return 发给 Agent 的续跑提示
     */
    private String repairContinuationPrompt(String nextTool, IncidentTask task) {
        String common = "当前任务要求完成自动代码修复，不能以调查总结或人工建议结束。"
                + "不要等待子智能体、额外技能或人工提供文件。本回合只执行指定工具，不要重复已完成阶段。";
        if (ReactAgentFactory.PATCH_PREVIEW.equals(nextTool)) {
            return common + "请基于已收集日志和当前代码工作区立即调用 PATCH_PREVIEW，生成最小修复预览。";
        }
        if (ReactAgentFactory.CODEX_LOCAL_REPAIR.equals(nextTool)) {
            return common + "补丁预览已经完成，请立即调用 CODEX_LOCAL_REPAIR 执行实际修复；"
                    + "不要再次调用 PATCH_PREVIEW。修复范围限定为任务工作区。";
        }
        return common + RepairEngineLabels.shortName(task)
                + " 修复已经完成。请使用事件上下文中的 codexActualDiff 立即调用 TEST_VERIFICATION，"
                + "验证本次实际代码变更；不要再次调用 PATCH_PREVIEW 或 CODEX_LOCAL_REPAIR。";
    }

    /**
     * 自动修复链路无法继续时明确失败完结，避免把建议误记为成功。
     *
     * @param session 会话
     * @param task    故障任务
     * @param message 失败说明
     * @return 失败完结后的会话
     */
    private AgentSession finishMissingCodex(AgentSession session, IncidentTask task, String message) {
        log.error("自动修复流程失败 incidentId={} sessionId={} stage={} message={}",
                task.getId(), session.getId(),
                task.getContext().getOrDefault("codexRepairStage", "UNKNOWN"), message);
        sessions.appendTurn(session.getId(), "ERROR", message);
        lifecycle.markFailed(task);
        task.setLatestSummary(message);
        task.getTimeline().add(new AgentStepRecord(repairStepTitle(task), false, message, Instant.now()));
        incidents.save(task);
        syncOpsTask(task, OpsTaskStatus.FAILED, message);
        return finishCoordinatorSession(session, AgentSessionStatus.FAILED, null, message);
    }

    /**
     * 时间线步骤标题：按实际/偏好修复引擎动态命名。
     */
    private static String repairStepTitle(IncidentTask task) {
        return RepairEngineLabels.repairStepTitle(RepairEngineLabels.fromTask(task));
    }

    private static String repairEngineDisplayName(String engine) {
        return RepairEngineLabels.shortName(engine);
    }

    /**
     * 创建审批操作并将会话置为等待审批。
     *
     * @param session  会话
     * @param task     故障任务
     * @param handle   Agent 句柄
     * @param response 含权限请求的响应消息
     * @return 等待审批状态的会话
     * @throws IllegalStateException 响应中无待审批工具调用
     */
    private AgentSession pauseForApproval(AgentSession session, IncidentTask task,
                                          ReactAgentFactory.AgentHandle handle, Msg response) {
        List<ToolUseBlock> toolCalls = response.getContent().stream()
                .filter(ToolUseBlock.class::isInstance)
                .map(ToolUseBlock.class::cast)
                .filter(tool -> tool.getState() == ToolCallState.ASKING)
                .toList();
        if (toolCalls.isEmpty()) {
            throw new IllegalStateException("AgentScope 请求了操作权限，但没有提供待审批的工具调用");
        }
        ensurePendingActions(session, task, handle, toolCalls);
        sessions.appendTurn(session.getId(), "APPROVAL", "AgentScope 正在等待操作人员审批");
        sessions.updateStatus(session.getId(), AgentSessionStatus.WAITING_APPROVAL);
        task.setLatestSummary("AgentScope 请求审批一项受风险控制的工具操作");
        task.getContext().put("agentSessionId", session.getId());
        incidents.save(task);
        syncOpsTask(task, OpsTaskStatus.WAITING_APPROVAL,
                "等待审批：" + toolCalls.stream().map(ToolUseBlock::getName).distinct().toList());
        return sessions.findById(session.getId()).orElseThrow();
    }

    /**
     * 按失败可重试性与是否已进入副作用工具，决定安排重试或标记失败。
     *
     * @param session 会话
     * @param task    故障任务
     * @param ex      执行异常
     * @return 重试中或失败完结的会话
     */
    private AgentSession handleFailure(AgentSession session, IncidentTask task, Exception ex) {
        AgentSession latest = sessions.findById(session.getId()).orElse(session);
        if (latest.getStatus() == AgentSessionStatus.CANCELLED) {
            log.info("忽略已取消会话的迟到异常 incidentId={} sessionId={}",
                    task.getId(), session.getId());
            return latest;
        }
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        AgentFailureClassifier.Failure failure = failureClassifier.classify(ex);
        boolean sideEffectEntered = sessions.hasNonRetryableToolCallSince(
                session.getId(), session.getLastHeartbeatAt());
        boolean canRetry = failure.retryable() && !sideEffectEntered
                && session.getAttemptCount() < session.getMaxAttempts();
        if (canRetry) {
            long delay = retryDelaySeconds(session.getAttemptCount());
            Instant nextRetry = Instant.now().plusSeconds(delay);
            sessions.appendTurn(session.getId(), "RETRY",
                    "第 " + session.getAttemptCount() + " 次执行发生瞬时故障（"
                            + failure.type() + "），将在 " + delay + " 秒后重试：" + message);
            task.setLatestSummary("执行发生瞬时故障，已安排安全重试");
            incidents.save(task);
            return sessions.scheduleRetry(session.getId(), leases.getOwnerId(),
                    message, failure.type(), nextRetry);
        }
        if (sideEffectEntered && failure.retryable()) {
            message = message + "；本次尝试已经进入有副作用的工具，已禁止自动重试";
        }
        sessions.appendTurn(session.getId(), "ERROR", message);
        lifecycle.markFailed(task);
        task.setLatestSummary("AgentScope 执行失败：" + message);
        task.getTimeline().add(new AgentStepRecord("执行异常", false, message, Instant.now()));
        incidents.save(task);
        syncOpsTask(task, OpsTaskStatus.FAILED, task.getLatestSummary());
        return finishCoordinatorSession(session, AgentSessionStatus.FAILED, null, message);
    }

    /**
     * 将 Agent 会话状态即时回写到来源运维任务。
     *
     * @param incident 故障任务（上下文含 opsTaskId）
     * @param status   运维任务目标状态
     * @param summary  最新摘要
     */
    private void syncOpsTask(IncidentTask incident, OpsTaskStatus status, String summary) {
        Object opsTaskId = incident.getContext().get("opsTaskId");
        OpsTaskRepository repository = opsTasks.getIfAvailable();
        if (opsTaskId == null || repository == null) {
            return;
        }
        repository.findById(String.valueOf(opsTaskId)).ifPresent(opsTask -> {
            opsTask.setStatus(status);
            opsTask.setLatestSummary(summary);
            repository.update(opsTask);
        });
    }

    /**
     * 计算带上限的指数退避重试秒数。
     *
     * @param attemptCount 当前已尝试次数
     * @return 下次重试等待秒数
     */
    private long retryDelaySeconds(int attemptCount) {
        long multiplier = 1L << Math.min(Math.max(0, attemptCount - 1), 20);
        return Math.min(maxBackoffSeconds, initialBackoffSeconds * multiplier);
    }

    /**
     * 构建与持久化会话对应的 AgentScope 运行上下文。
     *
     * @param session Agent 会话
     * @return 运行上下文
     */
    private RuntimeContext context(AgentSession session) {
        return RuntimeContext.builder()
                .sessionId(session.getThreadId())
                .userId(session.getIncidentId())
                .build();
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 任意可序列化对象
     * @return JSON 文本
     * @throws IllegalStateException 序列化失败
     */
    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception ex) {
            throw new IllegalStateException("待审批工具参数序列化失败", ex);
        }
    }

    /**
     * 从工具输入映射中安全读取字符串字段。
     *
     * @param input 工具输入，期望为 Map
     * @param key   字段名
     * @return 字段字符串；缺失时为空串
     */
    private String stringValue(Object input, String key) {
        if (input instanceof Map<?, ?> values) {
            Object value = values.get(key);
            return value == null ? "" : String.valueOf(value);
        }
        return "";
    }

    /**
     * 按会话 ID 查询会话。
     *
     * @param sessionId 会话 ID
     * @return 会话；不存在则为 empty
     */
    @Override
    public Optional<AgentSession> get(String sessionId) {
        return sessions.findById(sessionId);
    }

    /**
     * 查询指定故障的最近一次 Agent 会话。
     *
     * @param incidentId 故障 ID
     * @return 会话；不存在则为 empty
     */
    @Override
    public Optional<AgentSession> getLatestByIncident(String incidentId) {
        return sessions.findLatestByIncident(incidentId);
    }

    /**
     * 列出全部 Agent 会话。
     *
     * @return 会话列表
     */
    @Override
    public List<AgentSession> list() {
        return sessions.findAll();
    }

    /**
     * 按可见故障 ID 集合查询会话。
     *
     * @param incidentIds 故障 ID 集合
     * @return 匹配的会话列表
     */
    @Override
    public List<AgentSession> listByIncidentIds(java.util.Set<String> incidentIds) {
        return sessions.findByIncidentIds(incidentIds);
    }

    /**
     * 查询会话回合时间线。
     *
     * @param sessionId 会话 ID
     * @return 回合列表
     */
    @Override
    public List<AgentTurn> turns(String sessionId) {
        return sessions.findTurns(sessionId);
    }

    /**
     * 查询会话的工具调用审计记录。
     *
     * @param sessionId 会话 ID
     * @return 工具调用列表
     */
    @Override
    public List<AgentToolCall> toolCalls(String sessionId) {
        return sessions.findToolCalls(sessionId);
    }

    /**
     * 查询所有待审批动作。
     *
     * @return 待审批动作列表
     */
    @Override
    public List<PendingAgentAction> pendingActions() {
        return pendingActions.findPending();
    }

    /**
     * 按可见故障 ID 集合查询待审批动作。
     *
     * @param incidentIds 故障 ID 集合
     * @return 待审批动作列表
     */
    @Override
    public List<PendingAgentAction> pendingActionsByIncidentIds(java.util.Set<String> incidentIds) {
        return pendingActions.findPendingByIncidentIds(incidentIds);
    }

    /**
     * 按 ID 查询审批动作，供行级权限校验。
     *
     * @param actionId 动作 ID
     * @return 动作；不存在则为 empty
     */
    @Override
    public Optional<PendingAgentAction> getApprovalAction(String actionId) {
        return pendingActions.findById(actionId);
    }

    /**
     * 查询指定故障下的全部审批动作。
     *
     * @param incidentId 故障 ID
     * @return 审批动作列表
     */
    @Override
    public List<PendingAgentAction> approvalActions(String incidentId) {
        return pendingActions.findByIncident(incidentId);
    }

    /**
     * 查询指定会话下的全部审批动作。
     *
     * @param sessionId 会话 ID
     * @return 审批动作列表
     */
    @Override
    public List<PendingAgentAction> approvalActionsBySession(String sessionId) {
        return pendingActions.findBySession(sessionId);
    }

    /**
     * 取消指定故障的最近一轮非终态会话（默认非应用重启场景）。
     *
     * @param incidentId 故障 ID
     * @param reason     取消原因
     * @return 取消后的会话；无会话则为 empty
     * @throws IllegalStateException 有副作用工具仍在执行且不允许强制取消
     */
    @Override
    public Optional<AgentSession> cancelLatestByIncident(String incidentId, String reason) {
        return cancelLatestByIncident(incidentId, reason, false);
    }

    /**
     * 取消指定故障的最近一轮非终态会话。
     * <p>应用重启场景可强制取消，即使有副作用工具未完成。</p>
     *
     * @param incidentId         故障 ID
     * @param reason             取消原因
     * @param applicationRestart 是否因应用重启触发
     * @return 取消后的会话；无会话或已终态则返回原会话/empty
     * @throws IllegalStateException 非重启场景下副作用工具仍在执行
     */
    @Override
    public Optional<AgentSession> cancelLatestByIncident(
            String incidentId, String reason, boolean applicationRestart) {
        Optional<AgentSession> found = sessions.findLatestByIncident(incidentId);
        if (found.isEmpty()) return Optional.empty();
        AgentSession session = found.get();
        if (session.getStatus() == AgentSessionStatus.SUCCEEDED
                || session.getStatus() == AgentSessionStatus.FAILED
                || session.getStatus() == AgentSessionStatus.CANCELLED
                || session.getStatus() == AgentSessionStatus.TIMEOUT) {
            return found;
        }
        if (!applicationRestart && sessions.hasUnfinishedNonRetryableToolCall(session.getId())) {
            throw new IllegalStateException("上一轮本地修复、验证或部署工具仍在执行，暂不能安全重启，请等待该工具结束后重试");
        }
        Future<?> repairJob = repairReviewJobs.remove(session.getId());
        if (repairJob != null) {
            repairJob.cancel(true);
        }
        pendingActions.invalidatePendingBySession(session.getId(), reason);
        sessions.appendTurn(session.getId(), "CANCELLED", reason);
        AgentSession cancelled = finishCoordinatorSession(
                session, AgentSessionStatus.CANCELLED, null, reason);
        incidents.findById(incidentId).ifPresent(task -> {
            task.setStatus(com.zhiwei.superemployee.core.domain.IncidentStatus.CANCELLED);
            task.setLatestSummary(reason);
            task.getTimeline().add(new AgentStepRecord("结束上一轮分析", false, reason, Instant.now()));
            incidents.save(task);
        });
        log.info("已结束上一轮分析 incidentId={} sessionId={} applicationRestart={} reason={}",
                incidentId, session.getId(), applicationRestart, reason);
        return Optional.of(cancelled);
    }

    /**
     * 构建首次执行所需的事件调查提示词（含主机、日志、工作区与修复约束）。
     *
     * @param task 故障任务
     * @return 用户提示词文本
     */
    private String buildPrompt(IncidentTask task) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("事件 ID：").append(task.getId())
                .append("\n标题：").append(task.getTitle())
                .append("\n优先级：").append(task.getPriority().name())
                .append("\n描述：").append(task.getDescription());
        Object opsTaskId = task.getContext().get("opsTaskId");
        if (opsTaskId != null) {
            prompt.append("\n来源运维任务 ID：").append(opsTaskId);
        }
        Object hostId = task.getContext().get("preferredHostId");
        if (hostId != null) {
            prompt.append("\n优先诊断主机 ID：").append(hostId);
            Object hostName = task.getContext().get("preferredHostName");
            Object hostIp = task.getContext().get("preferredHostIp");
            if (hostName != null) {
                prompt.append("（").append(hostName);
                if (hostIp != null) {
                    prompt.append(" / ").append(hostIp);
                }
                prompt.append("）");
            }
            prompt.append("\n请优先使用 LOG_QUERY 对该主机做只读诊断，不要虚构其他主机或 Kubernetes 资源。");
        }
        Object pullSucceeded = task.getContext().get("pullSucceeded");
        Object logTemplate = task.getContext().get("logTemplate");
        Object logPath = task.getContext().get("logPath");
        Object pulledLogLines = task.getContext().get("pulledLogLines");
        if (Boolean.TRUE.equals(pullSucceeded) || "true".equalsIgnoreCase(String.valueOf(pullSucceeded))) {
            prompt.append("\n本次巡检已成功从目标主机拉取应用日志");
            if (logTemplate != null) {
                prompt.append("，模板=").append(logTemplate);
            }
            if (logPath != null && !String.valueOf(logPath).isBlank()) {
                prompt.append("，路径=").append(logPath);
            }
            if (pulledLogLines != null) {
                prompt.append("，实际取得约 ").append(pulledLogLines).append(" 行");
            }
            prompt.append("。这已证明目标路径存在且应用日志可读取；不得仅因通用进程查询未命中就推断应用未部署、未运行或主机错误。")
                    .append("\n必须先调用 INSPECT_INCIDENT；其 collectedLogContent 是本次后端本地落盘的完整日志证据。")
                    .append(" localLogFile/collectedLogFile 是 Windows 本地路径，绝对不得传给 LOG_QUERY。")
                    .append("\n本次自动巡检已有本地完整日志，LOG_QUERY 不会重复开放；请直接分析 collectedLogContent。")
                    .append("\nINSPECT_INCIDENT 返回 collectedLogContent 后即视为本地日志验证完成；"
                            + "日志未被 Git 纳管不影响读取，也不是停止补丁或 ")
                    .append(RepairEngineLabels.shortName(task)).append(" 修复的理由。")
                    .append("\nRedis/MinIO 命令行连通性、额外验证技能和子智能体均不是修复前置条件；"
                            + "应直接在配置的代码工作区定位日志堆栈对应源码。")
                    .append("\n不得建议或声称调用当前工具清单中不存在的 PROCESS_LIST、Kubernetes 查询等工具。");
        }
        Object workspacePath = task.getContext().get("workspacePath");
        Object frontendWorkspacePath = task.getContext().get("frontendWorkspacePath");
        Object repairRoot = task.getContext().get("repairRoot");
        if (workspacePath != null) {
            prompt.append("\n后端代码工作区：").append(workspacePath)
                    .append("（Maven 验证使用该目录或其子模块）");
        }
        Object clientKind = task.getContext().get("clientKind");
        Object clientVerifyCommand = task.getContext().get("clientVerifyCommand");
        Object backendKind = task.getContext().get("backendKind");
        if (backendKind != null) {
            prompt.append("\n后端工程画像：").append(backendKind);
        }
        if (frontendWorkspacePath != null && !String.valueOf(frontendWorkspacePath).isBlank()) {
            prompt.append("\n端侧工程目录：").append(frontendWorkspacePath);
            if (clientKind != null) {
                prompt.append("（类型=").append(clientKind).append("）");
            }
            if (clientVerifyCommand != null && !String.valueOf(clientVerifyCommand).isBlank()) {
                prompt.append("\n端侧验证命令：").append(clientVerifyCommand);
            }
            prompt.append("\n若接口/页面/小程序相关，补丁须同时覆盖后端与端侧，避免只改一端。");
        }
        if (repairRoot != null && !String.valueOf(repairRoot).isBlank()) {
            prompt.append("\nCLI 修码沙箱根：").append(repairRoot)
                    .append("（").append(RepairEngineLabels.shortName(task)).append(" 在此根目录下改码）");
        }
        Object opsBranch = task.getContext().get("opsBranch");
        Object opsBranchReady = task.getContext().get("opsBranchReady");
        Object codexRequired = task.getContext().get("codexRequired");
        String engineShort = RepairEngineLabels.shortName(task);
        String engineCli = RepairEngineLabels.cliName(task);
        if (opsBranch != null) {
            prompt.append("\n运维修复分支：").append(opsBranch)
                    .append("，准备状态：").append(opsBranchReady);
        }
        if (Boolean.TRUE.equals(codexRequired)
                || "true".equalsIgnoreCase(String.valueOf(codexRequired))) {
            prompt.append("\n这是自动巡检产生的代码修复任务。完成日志根因分析后，必须定位相关代码文件、生成最小补丁预览，")
                    .append("随后请求 CODEX_LOCAL_REPAIR 在上述运维分支执行修复；不得跳过 ")
                    .append(engineShort).append(" 而只给人工建议。")
                    .append(engineShort).append(" 修复完成后必须执行 TEST_VERIFICATION，并根据验证结果给出结论。");
        }
        else {
            prompt.append("\n本周期 anomalyDetected=false，是健康巡检而非故障修复。")
                    .append("只调用一次 INSPECT_INCIDENT 读取 collectedLogContent，然后直接给出简洁的健康摘要和风险观察并结束。")
                    .append("不要派生任何子智能体，不要查询 Git，不要生成补丁，也不要调用 ")
                    .append(engineCli).append("、测试或部署工具。");
        }
        if (Boolean.TRUE.equals(codexRequired)
                || "true".equalsIgnoreCase(String.valueOf(codexRequired))) {
            prompt.append("\n请协调个体智能体开展调查：依次 agent_spawn(agent_id=LOG_EXTRACTION)、")
                    .append("ROOT_CAUSE_ANALYSIS、CODE_REPAIR；若返回 task_id 则用 task_output 取回结果后再综合分析。")
                    .append("仅使用已授权工具；本地改码走 ").append(engineShort)
                    .append(" 受控修复（PATCH_PREVIEW → CODEX_LOCAL_REPAIR → TEST_VERIFICATION），验证通过后请求审批部署。");
        }
        return prompt.toString();
    }

    /**
     * 完结协调会话，并收口仍挂起的个体智能体会话。
     */
    private AgentSession finishCoordinatorSession(AgentSession session, AgentSessionStatus status,
                                                  String summary, String error) {
        return finishCoordinatorSession(session.getId(), session.getIncidentId(), status, summary, error);
    }

    private AgentSession finishCoordinatorSession(String sessionId, String incidentId,
                                                  AgentSessionStatus status, String summary, String error) {
        AgentSession finished = sessions.finish(sessionId, status, summary, error);
        collaborativeBridge.closeOrphanSpecialists(incidentId, status == AgentSessionStatus.SUCCEEDED);
        return finished;
    }
}
