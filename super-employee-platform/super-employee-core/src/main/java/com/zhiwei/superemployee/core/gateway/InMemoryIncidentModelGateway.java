package com.zhiwei.superemployee.core.gateway;

import com.zhiwei.superemployee.core.domain.IncidentTask;

/**
 * 内存模拟的模型网关：根据任务上下文中的日志范围生成固定根因与示例 diff。
 * <p>
 * 不调用外部 LLM，适合本地工作流联调与单测；生产环境可替换为真实模型适配实现。
 * @author yangdb
 */
public class InMemoryIncidentModelGateway implements IncidentModelGateway {

    /**
     * 基于事件上下文生成模拟根因分析结果。
     *
     * @param task 故障任务，可读 {@code logScope}
     * @return 固定根因标签与证据摘要
     */
    @Override
    public AnalysisResult analyze(IncidentTask task) {
        String evidence = String.valueOf(task.getContext().getOrDefault("logScope", "recent-5m"));
        return new AnalysisResult(
                "config-mismatch-or-parameter-error",
                "logs scoped to " + evidence,
                "identified probable configuration mismatch"
        );
    }

    /**
     * 基于分析结果生成模拟补丁方案（示例 service.conf diff）。
     *
     * @param task           故障任务，用于截取 ID 生成标题
     * @param analysisResult 上游根因结论
     * @return 含示例 diff 的补丁方案
     */
    @Override
    public PatchPlan draftPatch(IncidentTask task, AnalysisResult analysisResult) {
        String diffPreview = "diff --git a/service.conf b/service.conf\n- old_value\n+ fixed_value";
        return new PatchPlan(
                "fix-" + task.getId().substring(0, 8),
                diffPreview,
                "drafted dry-run patch for " + analysisResult.getRootCause()
        );
    }
}
