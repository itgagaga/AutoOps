package com.zhiwei.superemployee.core.gateway;

import com.zhiwei.superemployee.core.domain.IncidentTask;

/**
 * 故障处置工具执行网关：负责补丁预览、验证与部署等副作用操作。
 * <p>
 * 与 {@link IncidentModelGateway} 分工——模型侧产出方案，本接口执行或模拟执行；
 * 实现包括内存模拟（{@link InMemoryIncidentToolGateway}）与本地 Maven 命令（{@link CommandIncidentToolGateway}）。
 * @author yangdb
 */
public interface IncidentToolGateway {

    /**
     * 生成补丁的可审阅预览结果（通常不改动目标环境）。
     *
     * @param task 关联的故障任务
     * @param plan 模型拟定的补丁方案
     * @return 预览是否成功及输出文本
     */
    ToolResult previewPatch(IncidentTask task, IncidentModelGateway.PatchPlan plan);

    /**
     * 执行补丁部署前的验证操作（如 Maven test/verify）。
     *
     * @param task 关联的故障任务
     * @param plan 待验证的补丁方案
     * @return 验证是否成功及输出文本
     */
    ToolResult runVerification(IncidentTask task, IncidentModelGateway.PatchPlan plan);

    /**
     * 部署已获批准的补丁方案。
     *
     * @param task 关联的故障任务
     * @param plan 已批准的补丁方案
     * @return 部署是否成功及输出文本
     */
    ToolResult deploy(IncidentTask task, IncidentModelGateway.PatchPlan plan);

    /**
     * 单次工具调用的结果载体：成功标志与可展示/可审计的输出。
     */
    final class ToolResult {
        /** 命令或模拟操作是否成功。 */
        private final boolean success;
        /** 标准输出或说明文本。 */
        private final String output;

        /**
         * @param success 是否成功
         * @param output  输出或说明
         */
        public ToolResult(boolean success, String output) {
            this.success = success;
            this.output = output;
        }

        /**
         * @return 是否成功
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * @return 输出或说明文本
         */
        public String getOutput() {
            return output;
        }
    }
}
