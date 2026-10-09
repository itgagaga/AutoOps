package com.zhiwei.superemployee.core.agent;

import com.zhiwei.superemployee.core.domain.AgentResult;
import com.zhiwei.superemployee.core.domain.IncidentTask;

/**
 * 测试验证智能体：将故障任务标记为待执行验证，供后续工具网关真正跑测试。
 * <p>
 * 写入上下文键 {@code testStatus=pending}；实际验证由
 * {@link com.zhiwei.superemployee.core.gateway.IncidentToolGateway#runVerification} 完成。
 * @author yangdb
 */
public class TestVerificationAgent implements Agent {

    /**
     * 返回测试验证智能体标识 {@code test-verification}。
     *
     * @return 固定标识 {@code test-verification}
     */
    @Override
    public String name() {
        return "test-verification";
    }

    /**
     * 将事件标记为待执行验证（{@code testStatus=pending}）。
     *
     * @param task 待标记的故障任务
     * @return 表示验证准备完成的成功结果
     */
    @Override
    public AgentResult execute(IncidentTask task) {
        task.getContext().put("testStatus", "pending");
        return new AgentResult(name(), true, "verification prepared");
    }
}
