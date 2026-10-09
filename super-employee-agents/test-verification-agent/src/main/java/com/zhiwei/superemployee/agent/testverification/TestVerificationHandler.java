package com.zhiwei.superemployee.agent.testverification;

import com.zhiwei.superemployee.agent.api.AgentCodes;
import com.zhiwei.superemployee.agent.api.SpecialistHandler;
import com.zhiwei.superemployee.agent.api.SpecialistTaskRequest;
import com.zhiwei.superemployee.agent.api.SpecialistTaskResult;
import org.springframework.stereotype.Component;

@Component
public class TestVerificationHandler implements SpecialistHandler {
    @Override
    public String code() {
        return AgentCodes.TEST_VERIFICATION;
    }

    @Override
    public String description() {
        return "审查修复建议并制定验证通过标准";
    }

    @Override
    public String sysPrompt() {
        return "你是独立的测试验证智能体（TEST_VERIFICATION）。结合事件证据审查修复建议并制定验证计划。"
                + "将通过标准和残余风险返回给协调智能体，不得自行执行变更。";
    }

    @Override
    public SpecialistTaskResult execute(SpecialistTaskRequest request) {
        String summary = """
                status: succeeded
                agent: TEST_VERIFICATION
                objective: %s
                验证方案：
                1. 通过标准：目标错误日志不再复现；关键接口/用例通过；无新增严重告警。
                2. 建议步骤：协调器在本地修复后调用 TEST_VERIFICATION 工具执行 Maven/端侧验证。
                3. 残余风险：环境差异、数据依赖、并发路径未覆盖。
                """.formatted(request.getObjective() == null ? "" : request.getObjective());
        return SpecialistTaskResult.succeeded(request.getTaskId(), code(), summary);
    }
}
