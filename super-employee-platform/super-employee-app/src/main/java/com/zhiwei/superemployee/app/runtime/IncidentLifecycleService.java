package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.core.domain.IncidentStatus;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.workflow.IncidentStateMachine;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

/**
 * 将模型产生的工具意图映射为后端可验证的处置阶段和门禁。
 * @author yangdb
 */
@Service
public class IncidentLifecycleService {
    private final IncidentStateMachine stateMachine = new IncidentStateMachine();

    /**
     * 将新建事件切换至处理中。
     *
     * @param task 运维/故障任务
     */
    public void begin(IncidentTask task) {
        if (task.getStatus() == IncidentStatus.RUNNING) {
            return;
        }
        if (task.getStatus() != IncidentStatus.NEW) {
            throw new IllegalStateException("当前事件处于 " + task.getStatus()
                    + "，不能重新开始分析（需要 NEW）。请从运维任务重新启动分析以创建新事件");
        }
        stateMachine.transition(task, IncidentStatus.RUNNING);
    }

    /**
     * 记录已采集证据并进入分诊阶段。
     * <p>已过分诊/补丁阶段时幂等忽略，避免 Agent 在 PATCH_READY 后再次 INSPECT 被状态门禁误杀。</p>
     *
     * @param task 运维/故障任务
     */
    public void evidenceCollected(IncidentTask task) {
        if (task.getStatus() != IncidentStatus.NEW && task.getStatus() != IncidentStatus.RUNNING) {
            return;
        }
        require(task, IncidentStatus.RUNNING);
        stateMachine.transition(task, IncidentStatus.TRIAGED);
        task.getContext().put("evidenceCollectedAt", Instant.now().toString());
    }

    /**
     * 校验证据后登记补丁指纹并进入补丁就绪阶段。
     * <p>已处于 {@link IncidentStatus#PATCH_READY} 时允许刷新预览内容（修复尚未完成），
     * 避免续跑时重复 PATCH_PREVIEW 触发「不能执行此操作」。</p>
     *
     * @param task 运维/故障任务
     * @param title 标题
     * @param diffPreview diffPreview
     * @param summary 摘要
     * @return 校验证据后登记补丁指纹并进入补丁就绪阶段
     */
    public String previewPatch(IncidentTask task, String title, String diffPreview, String summary) {
        if (!task.getContext().containsKey("evidenceReference")) {
            throw new IllegalStateException("缺少已审计的证据，不能生成补丁预览");
        }
        String fingerprint = patchFingerprint(title, diffPreview, summary);
        if (task.getStatus() == IncidentStatus.PATCH_READY) {
            task.getContext().put("patchFingerprint", fingerprint);
            task.getContext().put("patchPreparedAt", Instant.now().toString());
            return fingerprint;
        }
        if (task.getStatus() == IncidentStatus.DIAGNOSED) {
            task.getContext().put("patchFingerprint", fingerprint);
            task.getContext().put("patchPreparedAt", Instant.now().toString());
            stateMachine.transition(task, IncidentStatus.PATCH_READY);
            return fingerprint;
        }
        require(task, IncidentStatus.TRIAGED);
        stateMachine.transition(task, IncidentStatus.DIAGNOSED);
        task.getContext().put("patchFingerprint", fingerprint);
        task.getContext().put("patchPreparedAt", Instant.now().toString());
        stateMachine.transition(task, IncidentStatus.PATCH_READY);
        return fingerprint;
    }

    /**
     * 在 CodeX 执行前校验预览补丁并记录批准的文件范围。
     *
     * @param task 运维/故障任务
     * @param title 标题
     * @param diffPreview diffPreview
     * @param summary 摘要
     * @param allowedFiles allowedFiles
     */
    public void beforeCodexRepair(IncidentTask task, String title, String diffPreview, String summary,
                                  List<String> allowedFiles) {
        recoverPrematureTesting(task);
        require(task, IncidentStatus.PATCH_READY);
        Object branchReady = task.getContext().get("opsBranchReady");
        if (!(Boolean.TRUE.equals(branchReady)
                || "true".equalsIgnoreCase(String.valueOf(branchReady)))) {
            throw new IllegalStateException("运维分支未准备成功，禁止调用 "
                    + RepairEngineLabels.cliName(task) + "："
                    + task.getContext().getOrDefault("opsBranchResult", "无分支诊断信息"));
        }
        requirePatch(task, title, diffPreview, summary);
        if (!task.getContext().containsKey("evidenceReference")) {
            throw new IllegalStateException("缺少已审计证据，不能执行 "
                    + RepairEngineLabels.shortName(task) + " 本地修复");
        }
        task.getContext().put("patchPreview", diffPreview);
        task.getContext().put("patchTitle", title);
        task.getContext().put("patchSummary", summary);
        if (allowedFiles == null || allowedFiles.isEmpty()) {
            task.getContext().put("codexAllowedScope", "WORKSPACE");
        }
        else {
            task.getContext().put("codexAllowedFiles", List.copyOf(allowedFiles));
        }
        task.getContext().put("codexRepairStartedAt", Instant.now().toString());
    }

    /**
     * 绑定 CodeX 实际差异与文件范围，并使旧测试结果失效。
     *
     * @param task 运维/故障任务
     * @param title 标题
     * @param summary 摘要
     * @param actualDiff actualDiff
     * @param changedFiles changedFiles
     * @param successful 是否成功
     * @param withinScope withinScope
     */
    public void codexRepairCompleted(IncidentTask task, String title, String summary, String actualDiff,
                                     List<String> changedFiles, boolean successful, boolean withinScope) {
        require(task, IncidentStatus.PATCH_READY);
        List<String> files = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        task.getContext().put("codexChangedFiles", files);
        task.getContext().put("codexRepairCompletedAt", Instant.now().toString());
        task.getContext().put("codexRepairWithinScope", withinScope);
        task.getContext().remove("verificationPassed");
        task.getContext().remove("verificationPatchFingerprint");
        if (!successful || !withinScope) {
            task.getContext().put("codexRepairIdempotent", false);
            throw new IllegalStateException(RepairEngineLabels.shortName(task)
                    + " 本地修复失败、超出批准范围或未生成差异");
        }
        String effectiveDiff = actualDiff == null ? "" : actualDiff;
        boolean idempotent = effectiveDiff.isBlank() && files.isEmpty();
        if (idempotent) {
            // CLI 成功但无新增工作区差异：目标代码可能已是修复后状态，沿用补丁预览完成指纹绑定。
            effectiveDiff = String.valueOf(task.getContext().getOrDefault("patchPreview", ""));
            if (effectiveDiff.isBlank()) {
                effectiveDiff = "(idempotent-no-new-diff)\ntitle=" + value(title) + "\nsummary=" + value(summary);
            }
            task.getContext().put("codexRepairIdempotent", true);
            if (!"ACCEPTED".equals(String.valueOf(task.getContext().get("codexReviewStatus")))) {
                task.getContext().put("codexReviewStatus", "ACCEPTED");
            }
        }
        else if (effectiveDiff.isBlank()) {
            task.getContext().put("codexRepairIdempotent", false);
            throw new IllegalStateException(RepairEngineLabels.shortName(task)
                    + " 本地修复失败、超出批准范围或未生成差异");
        }
        else {
            task.getContext().put("codexRepairIdempotent", false);
        }
        String fingerprint = patchFingerprint(title, effectiveDiff, summary);
        task.getContext().put("patchFingerprint", fingerprint);
        task.getContext().put("codexRepairPatchFingerprint", fingerprint);
        task.getContext().put("codexActualDiff", effectiveDiff);
    }

    /**
     * 验证执行前先确认 CodeX 修复链已经完成，防止 Agent 跳过修复直接把事件推进到 TESTING。
     *
     * @param task 运维/故障任务
     */
    public void beforeVerification(IncidentTask task) {
        require(task, IncidentStatus.PATCH_READY);
        if (codexRequired(task) && !hasCompletedCodexRepair(task)) {
            throw new IllegalStateException(RepairEngineLabels.shortName(task)
                    + " 本地修复尚未完成，必须先执行 CODEX_LOCAL_REPAIR 再进行测试验证");
        }
    }

    /**
     * JAR 构建成功后进入等待人工部署验证状态。
     *
     * @param task 运维/故障任务
     */
    public void artifactReady(IncidentTask task) {
        require(task, IncidentStatus.PATCH_READY);
        stateMachine.transition(task, IncidentStatus.TESTING);
        stateMachine.transition(task, IncidentStatus.WAITING_INPUT);
        task.getContext().put("artifactReadyAt", Instant.now().toString());
    }

    /**
     * 人工部署验证结束后将事件推进到最终成功或失败状态。
     *
     * @param task 运维/故障任务
     * @param successful 是否成功
     */
    public void manualDeploymentCompleted(IncidentTask task, boolean successful) {
        require(task, IncidentStatus.WAITING_INPUT);
        stateMachine.transition(task, successful ? IncidentStatus.RESOLVED : IncidentStatus.FAILED);
        task.getContext().put("manualDeploymentVerified", successful);
        task.getContext().put("manualDeploymentVerifiedAt", Instant.now().toString());
    }

    /**
     * JAR 构建后由受控 Java 部署器自动完成部署。
     *
     * @param task 运维/故障任务
     * @param successful 是否成功
     */
    public void automaticDeploymentCompleted(IncidentTask task, boolean successful) {
        require(task, IncidentStatus.WAITING_INPUT);
        stateMachine.transition(task, successful ? IncidentStatus.RESOLVED : IncidentStatus.FAILED);
        task.getContext().put("automaticDeploymentSuccessful", successful);
        task.getContext().put("automaticDeploymentCompletedAt", Instant.now().toString());
    }

    /**
     * 记录补丁验证结果。
     *
     * @param task 运维/故障任务
     * @param title 标题
     * @param diffPreview diffPreview
     * @param summary 摘要
     * @param successful 是否成功
     */
    public void verificationCompleted(IncidentTask task, String title, String diffPreview, String summary,
                                      boolean successful) {
        beforeVerification(task);
        if (task.getContext().containsKey("codexActualDiff")
                && !safeEquals(String.valueOf(task.getContext().get("codexActualDiff")), diffPreview)) {
            throw new IllegalStateException("验证必须使用 "
                    + RepairEngineLabels.shortName(task) + " 生成的实际差异");
        }
        requirePatch(task, title, diffPreview, summary);
        task.getContext().put("verificationPassed", successful);
        task.getContext().put("verificationPatchFingerprint", requirePatch(task, title, diffPreview, summary));
        task.getContext().put("verificationCompletedAt", Instant.now().toString());
        if (!successful) {
            throw new IllegalStateException("验证未通过，禁止进入审批或部署阶段");
        }
        stateMachine.transition(task, IncidentStatus.TESTING);
    }

    /**
     * 为已验证补丁创建与内容绑定的部署审批。
     *
     * @param task 运维/故障任务
     * @param title 标题
     * @param diffPreview diffPreview
     * @param summary 摘要
     * @return 为已验证补丁创建与内容绑定的部署审批
     */
    public ApprovalBinding requestDeploymentApproval(IncidentTask task, String title, String diffPreview,
                                                     String summary) {
        require(task, IncidentStatus.TESTING);
        if (!Boolean.TRUE.equals(task.getContext().get("verificationPassed"))) {
            throw new IllegalStateException("验证未通过，不能请求部署审批");
        }
        String fingerprint = requirePatch(task, title, diffPreview, summary);
        if (!safeEquals(fingerprint, String.valueOf(task.getContext().get("verificationPatchFingerprint")))) {
            throw new IllegalStateException("当前补丁未经过匹配版本的测试验证");
        }
        String deploymentKey = task.getId() + ":" + fingerprint;
        stateMachine.transition(task, IncidentStatus.APPROVAL_PENDING);
        task.getContext().put("deploymentKey", deploymentKey);
        task.getContext().put("approvalRequestedAt", Instant.now().toString());
        return new ApprovalBinding(fingerprint, deploymentKey);
    }

    /**
     * 校验并记录部署审批通过结果。
     *
     * @param task 运维/故障任务
     * @param action action
     */
    public void approvalGranted(IncidentTask task, PendingAgentAction action) {
        if (!ReactAgentFactory.DEPLOY_PATCH.equals(action.getToolCode())) {
            return;
        }
        require(task, IncidentStatus.APPROVAL_PENDING);
        if (!safeEquals(String.valueOf(task.getContext().get("patchFingerprint")), action.getPatchFingerprint())
                || !safeEquals(String.valueOf(task.getContext().get("deploymentKey")), action.getDeploymentKey())) {
            throw new IllegalStateException("审批绑定的补丁版本或部署幂等键已失效");
        }
        task.getContext().put("approvedDeploymentKey", action.getDeploymentKey());
        task.getContext().put("approvalGrantedAt", Instant.now().toString());
    }

    /**
     * 将被拒绝的部署审批标记为拒绝状态。
     *
     * @param task 运维/故障任务
     * @param toolCode 工具编码
     */
    public void approvalRejected(IncidentTask task, String toolCode) {
        if (ReactAgentFactory.DEPLOY_PATCH.equals(toolCode) && task.getStatus() == IncidentStatus.APPROVAL_PENDING) {
            stateMachine.transition(task, IncidentStatus.REJECTED);
        }
    }

    /**
     * 校验审批绑定后进入部署阶段。
     *
     * @param task 运维/故障任务
     * @param title 标题
     * @param diffPreview diffPreview
     * @param summary 摘要
     */
    public void beforeDeploy(IncidentTask task, String title, String diffPreview, String summary) {
        require(task, IncidentStatus.APPROVAL_PENDING);
        String fingerprint = requirePatch(task, title, diffPreview, summary);
        if (!safeEquals(fingerprint, String.valueOf(task.getContext().get("verificationPatchFingerprint")))) {
            throw new IllegalStateException("当前补丁未经过匹配版本的测试验证");
        }
        String deploymentKey = task.getId() + ":" + fingerprint;
        if (!safeEquals(deploymentKey, String.valueOf(task.getContext().get("approvedDeploymentKey")))) {
            throw new IllegalStateException("缺少当前补丁的有效部署审批");
        }
        stateMachine.transition(task, IncidentStatus.DEPLOYING);
    }

    /**
     * 根据部署结果结束部署阶段。
     *
     * @param task 运维/故障任务
     * @param successful 是否成功
     */
    public void deploymentFinished(IncidentTask task, boolean successful) {
        require(task, IncidentStatus.DEPLOYING);
        stateMachine.transition(task, successful ? IncidentStatus.RESOLVED : IncidentStatus.FAILED);
    }

    /**
     * 将事件标记为失败。
     *
     * @param task 运维/故障任务
     */
    public void markFailed(IncidentTask task) {
        task.setStatus(IncidentStatus.FAILED);
    }

    /**
     * 校验当前补丁内容仍与已登记指纹一致。
     *
     * @param task 运维/故障任务
     * @param title 标题
     * @param diffPreview diffPreview
     * @param summary 摘要
     * @return 校验当前补丁内容仍与已登记指纹一致
     */
    private String requirePatch(IncidentTask task, String title, String diffPreview, String summary) {
        String fingerprint = patchFingerprint(title, diffPreview, summary);
        if (!safeEquals(fingerprint, String.valueOf(task.getContext().get("patchFingerprint")))) {
            throw new IllegalStateException("补丁内容已变更，必须重新预览和验证");
        }
        return fingerprint;
    }

    /**
     * 要求事件处于指定生命周期状态。
     *
     * @param task 运维/故障任务
     * @param status 状态
     */
    private void require(IncidentTask task, IncidentStatus status) {
        if (task.getStatus() != status) {
            throw new IllegalStateException("当前事件处于 " + task.getStatus()
                    + "，需要 " + status + " 才能执行此操作");
        }
    }

    /**
     * 安全比较两个非空字符串。
     *
     * @param expected expected
     * @param actual actual
     * @return 是否满足条件
     */
    private boolean safeEquals(String expected, String actual) {
        return expected != null && expected.equals(actual);
    }

    /**
     * 兼容修复旧版本遗留的错误状态：未产生 CodeX 实际差异却已进入 TESTING，
     *
     * @param task 运维/故障任务
     */
    private void recoverPrematureTesting(IncidentTask task) {
        if (task.getStatus() != IncidentStatus.TESTING || hasCompletedCodexRepair(task)) {
            return;
        }
        task.setStatus(IncidentStatus.PATCH_READY);
        task.getContext().remove("verificationPassed");
        task.getContext().remove("verificationPatchFingerprint");
        task.getContext().remove("verificationCompletedAt");
        task.getContext().put("testingStateRecoveredAt", Instant.now().toString());
    }

    /**
     * 判断故障上下文是否要求必须完成 CodeX 本地修复。
     *
     * @param task 故障任务
     * @return 上下文 {@code codexRequired} 为 true 时返回 {@code true}
     */
    private boolean codexRequired(IncidentTask task) {
        Object required = task.getContext().get("codexRequired");
        return Boolean.TRUE.equals(required) || "true".equalsIgnoreCase(String.valueOf(required));
    }

    /**
     * 判断是否已完成一次有效的 CodeX 修复（含实际 diff 与完成标记）。
     *
     * @param task 故障任务
     * @return 具备非空 {@code codexActualDiff} 及完成时间、补丁指纹时返回 {@code true}
     */
    private boolean hasCompletedCodexRepair(IncidentTask task) {
        Object actualDiff = task.getContext().get("codexActualDiff");
        return actualDiff != null && !String.valueOf(actualDiff).isBlank()
                && task.getContext().containsKey("codexRepairCompletedAt")
                && task.getContext().containsKey("codexRepairPatchFingerprint");
    }

    /**
     * 计算补丁标题、差异和摘要的 SHA-256 指纹。
     *
     * @param title 标题
     * @param diffPreview diffPreview
     * @param summary 摘要
     * @return 计算补丁标题、差异和摘要的 SHA-256 指纹
     */
    public String patchFingerprint(String title, String diffPreview, String summary) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = value(title) + "\n" + value(diffPreview) + "\n" + value(summary);
            return java.util.HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    /**
     * 将空值规范化为空字符串。
     *
     * @param value 取值
     * @return 将空值规范化为空字符串
     */
    private String value(String value) {
        return value == null ? "" : value;
    }

    /**
     * 部署审批绑定信息：将补丁指纹与部署键关联，供待审批动作过期与校验使用。
     *
     * @param patchFingerprint 补丁内容指纹
     * @param deploymentKey    部署绑定键
     */
    public record ApprovalBinding(String patchFingerprint, String deploymentKey) {
    }
}
