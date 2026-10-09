package com.zhiwei.superemployee.core.gateway;

import com.zhiwei.superemployee.core.domain.IncidentTask;

/**
 * 内存模拟的工具网关：不启动真实进程，始终返回成功的预览、验证与部署说明文本。
 * <p>
 * 用于单元测试与无本地 Maven 环境的演示编排，与 {@link InMemoryIncidentModelGateway} 配对使用。
 * @author yangdb
 */
public class InMemoryIncidentToolGateway implements IncidentToolGateway {

    /**
     * 模拟生成补丁预览结果。
     *
     * @param task 故障任务（本实现未使用）
     * @param plan 补丁方案
     * @return 始终成功的预览结果
     */
    @Override
    public ToolResult previewPatch(IncidentTask task, IncidentModelGateway.PatchPlan plan) {
        return new ToolResult(true, "dry-run ok for " + plan.getTitle());
    }

    /**
     * 模拟执行补丁验证。
     *
     * @param task 故障任务（本实现未使用）
     * @param plan 补丁方案
     * @return 始终成功的验证结果
     */
    @Override
    public ToolResult runVerification(IncidentTask task, IncidentModelGateway.PatchPlan plan) {
        return new ToolResult(true, "verification passed for " + plan.getTitle());
    }

    /**
     * 模拟执行补丁部署。
     *
     * @param task 故障任务（本实现未使用）
     * @param plan 补丁方案
     * @return 始终成功的部署结果
     */
    @Override
    public ToolResult deploy(IncidentTask task, IncidentModelGateway.PatchPlan plan) {
        return new ToolResult(true, "deployment simulated for " + plan.getTitle());
    }
}
