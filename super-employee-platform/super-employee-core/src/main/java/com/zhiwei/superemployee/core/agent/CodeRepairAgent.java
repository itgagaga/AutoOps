package com.zhiwei.superemployee.core.agent;

import com.zhiwei.superemployee.core.domain.AgentResult;
import com.zhiwei.superemployee.core.domain.IncidentTask;

/**
 * 代码修复智能体：将故障任务标记为仅生成补丁草案（dry-run），不直接改生产配置。
 * <p>
 * 写入上下文键 {@code patchMode}；实际补丁内容通常由
 * {@link com.zhiwei.superemployee.core.gateway.IncidentModelGateway#draftPatch} 生成。
 * @author yangdb
 */
public class CodeRepairAgent implements Agent {

    /**
     * 返回代码修复智能体标识 {@code code-repair}。
     *
     * @return 固定标识 {@code code-repair}
     */
    @Override
    public String name() {
        return "code-repair";
    }

    /**
     * 将事件标记为仅生成补丁草案（{@code patchMode=dry-run}）。
     *
     * @param task 待标记的故障任务
     * @return 表示补丁草案已起草的成功结果
     */
    @Override
    public AgentResult execute(IncidentTask task) {
        task.getContext().put("patchMode", "dry-run");
        return new AgentResult(name(), true, "patch drafted");
    }
}
