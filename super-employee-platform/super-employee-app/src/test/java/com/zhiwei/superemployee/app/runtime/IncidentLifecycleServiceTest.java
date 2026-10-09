package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.core.domain.IncidentPriority;
import com.zhiwei.superemployee.core.domain.IncidentStatus;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link IncidentLifecycleService} 单测：故障生命周期门禁（证据、预览、验证、审批、CodeX、部署）。
 * @author yangdb
 */
class IncidentLifecycleServiceTest {

    private final IncidentLifecycleService lifecycle = new IncidentLifecycleService();

    /**
     * 场景：按序完成证据、预览、验证、审批绑定与部署。
     * 预期：部署成功后故障状态变为 {@link IncidentStatus#RESOLVED}。
     */
    @Test
    void shouldRequireEvidenceVerificationAndMatchingApprovalBeforeDeployment() {
        IncidentTask task = incident();
        lifecycle.begin(task);
        task.getContext().put("evidenceReference", "host-1:journal");
        lifecycle.evidenceCollected(task);
        lifecycle.previewPatch(task, "修复连接池", "diff --git a/app", "限制连接数");
        lifecycle.verificationCompleted(task, "修复连接池", "diff --git a/app", "限制连接数", true);

        IncidentLifecycleService.ApprovalBinding binding =
                lifecycle.requestDeploymentApproval(task, "修复连接池", "diff --git a/app", "限制连接数");
        PendingAgentAction action = new PendingAgentAction("action-1", "session-1", task.getId(), "call-1",
                ReactAgentFactory.DEPLOY_PATCH, "{}", "CRITICAL", "部署", "APPROVED", null,
                "now", "now", "admin", binding.patchFingerprint(), binding.deploymentKey(), null, null);
        lifecycle.approvalGranted(task, action);
        lifecycle.beforeDeploy(task, "修复连接池", "diff --git a/app", "限制连接数");
        lifecycle.deploymentFinished(task, true);

        assertThat(task.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
    }

    /**
     * 场景：分诊后再次采集只读证据。
     * 预期：状态仍为 TRIAGED，且 evidenceCollectedAt 时间戳不被覆盖。
     */
    @Test
    void shouldAllowAdditionalEvidenceAfterTriaging() {
        IncidentTask task = incident();
        lifecycle.begin(task);
        lifecycle.evidenceCollected(task);
        String collectedAt = String.valueOf(task.getContext().get("evidenceCollectedAt"));

        lifecycle.evidenceCollected(task);

        assertThat(task.getStatus()).isEqualTo(IncidentStatus.TRIAGED);
        assertThat(task.getContext().get("evidenceCollectedAt")).isEqualTo(collectedAt);
    }

    /**
     * 场景：补丁预览完成后 Agent 再次 INSPECT / PATCH_PREVIEW。
     * 预期：不再抛出「当前事件处于 PATCH_READY，不能执行此操作」。
     */
    @Test
    void shouldAllowInspectAndPreviewRetryAfterPatchReady() {
        IncidentTask task = preparedForCodex();
        String fingerprint = String.valueOf(task.getContext().get("patchFingerprint"));

        lifecycle.evidenceCollected(task);
        String refreshed = lifecycle.previewPatch(task, "修复连接池", "diff --git a/app", "限制连接数");

        assertThat(task.getStatus()).isEqualTo(IncidentStatus.PATCH_READY);
        assertThat(refreshed).isEqualTo(fingerprint);
    }

    /**
     * 场景：审批通过后 beforeDeploy 使用与审批指纹不一致的补丁内容。
     * 预期：抛出 IllegalStateException，提示「补丁内容已变更」。
     */
    @Test
    void shouldRejectDeploymentWhenPatchChangesAfterApproval() {
        IncidentTask task = incident();
        lifecycle.begin(task);
        task.getContext().put("evidenceReference", "host-1:journal");
        lifecycle.evidenceCollected(task);
        lifecycle.previewPatch(task, "修复连接池", "diff --git a/app", "限制连接数");
        lifecycle.verificationCompleted(task, "修复连接池", "diff --git a/app", "限制连接数", true);
        IncidentLifecycleService.ApprovalBinding binding =
                lifecycle.requestDeploymentApproval(task, "修复连接池", "diff --git a/app", "限制连接数");
        lifecycle.approvalGranted(task, new PendingAgentAction("action-1", "session-1", task.getId(), "call-1",
                ReactAgentFactory.DEPLOY_PATCH, "{}", "CRITICAL", "部署", "APPROVED", null,
                "now", "now", "admin", binding.patchFingerprint(), binding.deploymentKey(), null, null));

        assertThatThrownBy(() -> lifecycle.beforeDeploy(task, "修复连接池", "diff --git a/app\n+变更", "限制连接数"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("补丁内容已变更");
    }

    /**
     * 场景：审批有效但验证指纹被篡改为过期值。
     * 预期：beforeDeploy 拒绝，消息含「测试验证」。
     */
    @Test
    void shouldRejectDeploymentWithoutMatchingVerification() {
        IncidentTask task = incident();
        lifecycle.begin(task);
        task.getContext().put("evidenceReference", "host-1:journal");
        lifecycle.evidenceCollected(task);
        lifecycle.previewPatch(task, "修复连接池", "diff --git a/app", "限制连接数");
        lifecycle.verificationCompleted(task, "修复连接池", "diff --git a/app", "限制连接数", true);
        IncidentLifecycleService.ApprovalBinding binding =
                lifecycle.requestDeploymentApproval(task, "修复连接池", "diff --git a/app", "限制连接数");
        lifecycle.approvalGranted(task, new PendingAgentAction("action-1", "session-1", task.getId(), "call-1",
                ReactAgentFactory.DEPLOY_PATCH, "{}", "CRITICAL", "部署", "APPROVED", null,
                "now", "now", "admin", binding.patchFingerprint(), binding.deploymentKey(), null, null));
        task.getContext().put("verificationPatchFingerprint", "obsolete");

        assertThatThrownBy(() -> lifecycle.beforeDeploy(task, "修复连接池", "diff --git a/app", "限制连接数"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("测试验证");
    }

    /**
     * 场景：已走证据采集流程但 context 缺少 evidenceReference。
     * 预期：previewPatch 拒绝，提示「缺少已审计的证据」。
     */
    @Test
    void shouldRejectPreviewWithoutEvidence() {
        IncidentTask task = incident();
        lifecycle.begin(task);
        lifecycle.evidenceCollected(task);
        task.getContext().remove("evidenceReference");

        assertThatThrownBy(() -> lifecycle.previewPatch(task, "修复", "diff", "说明"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("缺少已审计的证据");
    }

    /**
     * 场景：已有旧验证记录时 CodeX 修复成功并写出实际差异。
     * 预期：清除旧 verification 字段；用旧预览 diff 再验证会因「实际差异」不匹配而失败。
     */
    @Test
    void shouldInvalidateVerificationAfterSuccessfulCodexRepair() {
        IncidentTask task = preparedForCodex();
        task.getContext().put("verificationPassed", true);
        task.getContext().put("verificationPatchFingerprint", "stale");

        lifecycle.beforeCodexRepair(task, "修复连接池", "diff --git a/app", "限制连接数",
                java.util.List.of("src/Main.java"));
        lifecycle.codexRepairCompleted(task, "修复连接池", "限制连接数", "diff --git a/app\n+fixed",
                java.util.List.of("src/Main.java"), true, true);

        assertThat(task.getContext().get("verificationPassed")).isNull();
        assertThat(task.getContext().get("verificationPatchFingerprint")).isNull();
        assertThat(task.getContext().get("codexActualDiff")).isEqualTo("diff --git a/app\n+fixed");
        assertThatThrownBy(() -> lifecycle.verificationCompleted(
                task, "修复连接池", "diff --git a/app", "限制连接数", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("实际差异");
    }

    /**
     * 场景：CodeX 完成时实际改动文件超出批准白名单。
     * 预期：抛出「超出批准范围」；清除 verificationPassed；状态回落 PATCH_READY。
     */
    @Test
    void shouldBlockWhenCodexDiffExceedsAllowedFiles() {
        IncidentTask task = preparedForCodex();
        task.getContext().put("verificationPassed", true);
        task.getContext().put("verificationPatchFingerprint", "stale");

        lifecycle.beforeCodexRepair(task, "修复连接池", "diff --git a/app", "限制连接数",
                java.util.List.of("src/Main.java"));
        assertThatThrownBy(() -> lifecycle.codexRepairCompleted(task, "修复连接池", "限制连接数",
                "diff --git a/other", java.util.List.of("src/Other.java"), false, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("超出批准范围");
        assertThat(task.getContext().get("verificationPassed")).isNull();
        assertThat(task.getStatus()).isEqualTo(IncidentStatus.PATCH_READY);
    }

    @Test
    void shouldAcceptIdempotentCodexRepairWithoutNewDiff() {
        IncidentTask task = preparedForCodex();
        lifecycle.beforeCodexRepair(task, "修复连接池", "diff --git a/app", "限制连接数",
                java.util.List.of("src/Main.java"));

        lifecycle.codexRepairCompleted(task, "修复连接池", "限制连接数", "",
                java.util.List.of(), true, true);

        assertThat(task.getContext().get("codexRepairIdempotent")).isEqualTo(true);
        assertThat(task.getContext().get("codexReviewStatus")).isEqualTo("ACCEPTED");
        assertThat(task.getContext().get("codexActualDiff")).isEqualTo("diff --git a/app");
        assertThat(task.getContext().get("codexRepairPatchFingerprint")).isNotNull();
    }

    /**
     * 场景：beforeCodexRepair 使用与预览绑定不一致的补丁内容。
     * 预期：拒绝启动 CodeX，提示「补丁内容已变更」。
     */
    @Test
    void shouldRejectCodexRepairWithoutPreviewBinding() {
        IncidentTask task = incident();
        lifecycle.begin(task);
        lifecycle.evidenceCollected(task);
        task.getContext().put("evidenceReference", "host-1:journal");
        task.getContext().put("opsBranchReady", true);
        lifecycle.previewPatch(task, "修复连接池", "diff --git a/app", "限制连接数");

        assertThatThrownBy(() -> lifecycle.beforeCodexRepair(task, "修复连接池", "diff --git a/app\n+x",
                "限制连接数", java.util.List.of("src/Main.java")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("补丁内容已变更");
    }

    /**
     * 场景：CodeX 产出实际差异后，用该差异完成验证并申请部署审批。
     * 预期：审批绑定指纹与任务 patchFingerprint 一致；状态进入 APPROVAL_PENDING。
     */
    @Test
    void shouldAllowVerificationWithCodexActualDiff() {
        IncidentTask task = preparedForCodex();
        lifecycle.beforeCodexRepair(task, "修复连接池", "diff --git a/app", "限制连接数",
                java.util.List.of("src/Main.java"));
        String actualDiff = "diff --git a/src/Main.java\n+return;";
        lifecycle.codexRepairCompleted(task, "修复连接池", "限制连接数", actualDiff,
                java.util.List.of("src/Main.java"), true, true);
        lifecycle.verificationCompleted(task, "修复连接池", actualDiff, "限制连接数", true);

        IncidentLifecycleService.ApprovalBinding binding =
                lifecycle.requestDeploymentApproval(task, "修复连接池", actualDiff, "限制连接数");
        assertThat(binding.patchFingerprint()).isEqualTo(task.getContext().get("patchFingerprint"));
        assertThat(task.getStatus()).isEqualTo(IncidentStatus.APPROVAL_PENDING);
    }

    /**
     * 场景：标记 codexRequired 后尚未执行 CodeX 即进入验证。
     * 预期：beforeVerification 拒绝并提示 CODEX_LOCAL_REPAIR；状态保持 PATCH_READY。
     */
    @Test
    void shouldRejectVerificationBeforeRequiredCodexRepair() {
        IncidentTask task = preparedForCodex();
        task.getContext().put("codexRequired", true);

        assertThatThrownBy(() -> lifecycle.beforeVerification(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CODEX_LOCAL_REPAIR");
        assertThat(task.getStatus()).isEqualTo(IncidentStatus.PATCH_READY);
    }

    /**
     * 场景：历史错误状态已推进到 TESTING 且无 CodeX 产物，随后启动 CodeX。
     * 预期：回退到 PATCH_READY，清除失败验证标记，并记录 testingStateRecoveredAt。
     */
    @Test
    void shouldRecoverPrematureTestingBeforeCodexRepair() {
        IncidentTask task = preparedForCodex();
        task.getContext().put("codexRequired", true);
        task.setStatus(IncidentStatus.TESTING);
        task.getContext().put("verificationPassed", false);

        lifecycle.beforeCodexRepair(task, "修复连接池", "diff --git a/app", "限制连接数",
                java.util.List.of("src/Main.java"));

        assertThat(task.getStatus()).isEqualTo(IncidentStatus.PATCH_READY);
        assertThat(task.getContext().get("verificationPassed")).isNull();
        assertThat(task.getContext()).containsKey("testingStateRecoveredAt");
    }

    /**
     * 构造已完成 begin、证据采集与补丁预览的故障任务，供 CodeX 相关用例复用。
     *
     * @return 处于补丁预览就绪态的 {@link IncidentTask}
     */
    private IncidentTask preparedForCodex() {
        IncidentTask task = incident();
        lifecycle.begin(task);
        task.getContext().put("evidenceReference", "host-1:journal");
        task.getContext().put("opsBranchReady", true);
        lifecycle.evidenceCollected(task);
        lifecycle.previewPatch(task, "修复连接池", "diff --git a/app", "限制连接数");
        return task;
    }

    /**
     * 创建用于生命周期流转的基础故障任务（P1，连接池耗尽场景）。
     *
     * @return 未进入生命周期的新建 {@link IncidentTask}
     */
    private IncidentTask incident() {
        return new IncidentTask("数据库连接异常", "连接池耗尽", IncidentPriority.P1);
    }
}
