package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.core.domain.IncidentPriority;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CollaborativeAgentBridge}：spawn / 后台 task 与个体会话生命周期。
 */
@ExtendWith(MockitoExtension.class)
class CollaborativeAgentBridgeTest {

    @Mock
    private AgentSessionRepository sessions;
    @Mock
    private IncidentRepository incidents;

    private CollaborativeAgentBridge bridge;
    private IncidentTask task;

    @BeforeEach
    void setUp() {
        bridge = new CollaborativeAgentBridge(sessions, incidents);
        task = new IncidentTask("fault", "detail", IncidentPriority.P1);
        lenient().when(incidents.findById(task.getId())).thenReturn(Optional.of(task));
        lenient().doAnswer(invocation -> {
            task.getTimeline().add(invocation.getArgument(1));
            return Optional.of(task);
        }).when(incidents).appendTimeline(eq(task.getId()), any());
    }

    @Test
    void shouldCreateRunningSessionOnSpecialistStart() {
        AgentSession created = new AgentSession(
                "spec-1", task.getId(), CollaborativeAgentRegistry.LOG_EXTRACTION, "thread",
                AgentSessionStatus.CREATED, "extract", null, null,
                "now", null, "now", "now");
        when(sessions.create(eq(task.getId()), eq(CollaborativeAgentRegistry.LOG_EXTRACTION),
                any(), eq(1))).thenReturn(created);
        when(sessions.markRunning("spec-1")).thenReturn(created);
        when(sessions.findById("spec-1")).thenReturn(Optional.of(new AgentSession(
                "spec-1", task.getId(), CollaborativeAgentRegistry.LOG_EXTRACTION, "thread",
                AgentSessionStatus.RUNNING, "extract", null, null,
                "now", null, "now", "now")));
        when(sessions.finishIfStatus(eq("spec-1"), eq(AgentSessionStatus.RUNNING),
                eq(AgentSessionStatus.SUCCEEDED), any(), isNull())).thenReturn(true);

        bridge.onSpecialistStart(task.getId(), "call-1", "evidence-collector", "提取日志证据");
        bridge.onSpawnResult("call-1", false, "证据时间线：...\n结论：磁盘将满");

        verify(sessions).create(task.getId(), CollaborativeAgentRegistry.LOG_EXTRACTION,
                "提取日志证据", 1);
        verify(sessions).markRunning("spec-1");
        verify(sessions).finishIfStatus("spec-1", AgentSessionStatus.RUNNING, AgentSessionStatus.SUCCEEDED,
                "证据时间线：...\n结论：磁盘将满", null);
        assertThat(task.getTimeline()).isNotEmpty();
    }

    @Test
    void shouldKeepSessionOpenUntilBackgroundTaskCompletes() {
        AgentSession created = new AgentSession(
                "spec-2", task.getId(), CollaborativeAgentRegistry.ROOT_CAUSE_ANALYSIS, "thread",
                AgentSessionStatus.CREATED, "analyze", null, null,
                "now", null, "now", "now");
        when(sessions.create(eq(task.getId()), eq(CollaborativeAgentRegistry.ROOT_CAUSE_ANALYSIS),
                any(), eq(1))).thenReturn(created);
        when(sessions.markRunning("spec-2")).thenReturn(created);
        when(sessions.findById("spec-2")).thenReturn(Optional.of(new AgentSession(
                "spec-2", task.getId(), CollaborativeAgentRegistry.ROOT_CAUSE_ANALYSIS, "thread",
                AgentSessionStatus.RUNNING, "analyze", null, null,
                "now", null, "now", "now")));
        when(sessions.finishIfStatus(eq("spec-2"), eq(AgentSessionStatus.RUNNING),
                eq(AgentSessionStatus.SUCCEEDED), any(), isNull())).thenReturn(true);

        bridge.onSpecialistStart(task.getId(), "call-2", "ROOT_CAUSE_ANALYSIS", "分析根因");
        bridge.onSpawnResult("call-2", false,
                "status: accepted\ntask_id: task-abc\nUse task_output");
        verify(sessions, never()).finishIfStatus(any(), any(), any(), any(), any());

        bridge.onBackgroundTaskResult("task-abc", true, "status: completed\n根因：NPE", true);
        verify(sessions).finishIfStatus("spec-2", AgentSessionStatus.RUNNING, AgentSessionStatus.SUCCEEDED,
                "status: completed\n根因：NPE", null);
    }

    @Test
    void shouldIgnoreUnknownAgentIds() {
        bridge.onSpecialistStart(task.getId(), "call-x", "deployment-planner", "部署");
        verify(sessions, never()).create(any(), any(), any(), anyInt());
    }

    @Test
    void shouldDetectTerminalTaskOutput() {
        assertThat(CollaborativeAgentBridge.isTerminalTaskOutput("status: running")).isFalse();
        assertThat(CollaborativeAgentBridge.isTerminalTaskOutput(
                "status: accepted\ntask_id: x")).isFalse();
        assertThat(CollaborativeAgentBridge.isTerminalTaskOutput(
                "status: completed\n证据充分")).isTrue();
        assertThat(CollaborativeAgentBridge.extract(
                CollaborativeAgentBridge.TASK_ID_PATTERN,
                "status: accepted\ntask_id: tid-1")).isEqualTo("tid-1");
    }
}
