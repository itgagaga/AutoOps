package com.zhiwei.superemployee.agent.coderepair;

import com.zhiwei.superemployee.agent.api.AgentCodes;
import com.zhiwei.superemployee.agent.api.SpecialistHandler;
import com.zhiwei.superemployee.agent.api.SpecialistTaskRequest;
import com.zhiwei.superemployee.agent.api.SpecialistTaskResult;
import org.springframework.stereotype.Component;

@Component
public class CodeRepairHandler implements SpecialistHandler {
    @Override
    public String code() {
        return AgentCodes.CODE_REPAIR;
    }

    @Override
    public String description() {
        return "设计影响最小、可回滚的修复方案与补丁建议";
    }

    @Override
    public String sysPrompt() {
        return "你是独立的修复规划智能体（CODE_REPAIR）。设计最小且可回滚的修复方案，"
                + "说明影响范围、回滚方式和验证方法。可将本地 Git 状态作为参考，"
                + "将建议补丁返回给协调智能体，不得自行执行变更。";
    }

    @Override
    public SpecialistTaskResult execute(SpecialistTaskRequest request) {
        Object rootCause = request.getContext().getOrDefault("rootCause",
                request.getContext().getOrDefault("topHypothesis", "待确认根因"));
        String summary = """
                status: succeeded
                agent: CODE_REPAIR
                objective: %s
                修复方案（规划，不直接改代码）：
                1. 针对根因「%s」定位相关源码与配置，准备最小 diff。
                2. 影响范围：优先单模块/单接口；避免无关重构。
                3. 回滚方式：保留运维分支提交，可一键 revert。
                4. 下一步：由协调器调用 PATCH_PREVIEW → CODEX_LOCAL_REPAIR 落地改码，再做验证。
                """.formatted(request.getObjective() == null ? "" : request.getObjective(), rootCause);
        SpecialistTaskResult result = SpecialistTaskResult.succeeded(request.getTaskId(), code(), summary);
        result.getData().put("requiresLocalRepair", true);
        return result;
    }
}
