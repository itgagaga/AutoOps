package com.zhiwei.superemployee.core.workflow;

import com.zhiwei.superemployee.core.domain.IncidentStatus;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.repository.InMemoryIncidentRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InMemoryIncidentWorkflow} 的集成风格单元测试：验证创建 → 自动处置 → 审批解决主路径。
 * @author yangdb
 */
class InMemoryIncidentWorkflowTest {

    /**
     * 验证内存工作流可完成处置、审批和解决状态流转，并写入预期时间线与上下文键。
     */
    @Test
    void shouldRunBasicIncidentFlow() {
        IncidentWorkflow workflow = new InMemoryIncidentWorkflow(new InMemoryIncidentRepository());
        IncidentTask task = workflow.create("demo", "service down", "P1");

        IncidentTask result = workflow.run(task.getId());

        assertNotNull(result);
        assertEquals(IncidentStatus.APPROVAL_PENDING, result.getStatus());
        assertEquals(5, result.getTimeline().size());
        assertTrue(result.getContext().containsKey("log-extraction"));
        assertTrue(result.getContext().containsKey("model-analysis"));
        assertTrue(result.getContext().containsKey("patch-draft"));
        assertTrue(result.getContext().containsKey("tool-preview"));
        assertTrue(result.getContext().containsKey("tool-verification"));
        assertEquals(1, workflow.list().size());

        IncidentTask approved = workflow.approve(task.getId(), "go ahead");
        assertEquals(IncidentStatus.RESOLVED, approved.getStatus());
        assertTrue(approved.getContext().containsKey("approvalComment"));
    }
}
