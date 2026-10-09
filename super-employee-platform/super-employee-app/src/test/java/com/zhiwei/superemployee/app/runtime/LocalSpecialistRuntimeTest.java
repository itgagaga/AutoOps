package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.agent.api.AgentCodes;
import com.zhiwei.superemployee.agent.api.SpecialistTaskRequest;
import com.zhiwei.superemployee.agent.api.SpecialistTaskResult;
import com.zhiwei.superemployee.app.agent.ManagedAgentRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LocalSpecialistRuntimeTest {

    @Test
    void shouldExecuteAllBuiltInAgentsInsidePlatformProcess() {
        LocalSpecialistRuntime runtime = new LocalSpecialistRuntime(mock(ManagedAgentRepository.class));
        for (String code : new String[]{AgentCodes.LOG_EXTRACTION, AgentCodes.ROOT_CAUSE_ANALYSIS,
                AgentCodes.CODE_REPAIR, AgentCodes.TEST_VERIFICATION}) {
            SpecialistTaskRequest request = new SpecialistTaskRequest();
            request.setTaskId("task-" + code);
            request.setAgentCode(code);
            request.setObjective("处理本地故障");
            request.setContext(new HashMap<>());

            SpecialistTaskResult result = runtime.invoke(code, request);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAgentCode()).isEqualTo(code);
            assertThat(runtime.status(code)).isEqualTo("IDLE");
        }
    }
}
