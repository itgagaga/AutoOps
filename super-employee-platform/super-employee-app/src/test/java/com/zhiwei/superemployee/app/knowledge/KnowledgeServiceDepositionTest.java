package com.zhiwei.superemployee.app.knowledge;

import com.zhiwei.superemployee.app.security.OpsDataPermissionService;
import com.zhiwei.superemployee.core.domain.IncidentPriority;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceDepositionTest {
    private KnowledgeRepository repository;
    private KnowledgeService service;

    @BeforeEach
    void setUp() {
        repository = mock(KnowledgeRepository.class);
        OpsDataPermissionService permission = mock(OpsDataPermissionService.class);
        when(permission.currentUsername()).thenReturn("operator");
        when(repository.findAll()).thenReturn(List.of());
        service = new KnowledgeService(repository, permission);
    }

    @Test
    void shouldIgnoreNormalInspectionAndProcessMessages() {
        IncidentTask normal = incident("[定时巡检] 广州人才日常运维");
        service.recordIncidentResolution(normal, "本次巡检日志全部为 INFO 和 WARN，系统运行正常，未发现异常。过程检查已经完成。");
        service.recordIncidentResolution(normal,
                "<system-notification>Background subagent task task_123 has completed.</system-notification>");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldCreateStructuredVerifiedCase() {
        IncidentTask incident = incident("[定时巡检] 广州人才日常运维");
        incident.getContext().put("patchTitle", "修复空指针保护");
        incident.getContext().put("patchSummary", "请求参数为空时增加前置校验");
        incident.getContext().put("codexChangedFiles", List.of("DemoService.java"));
        incident.getContext().put("verificationPassed", true);
        when(repository.findBySourceIncidentId(incident.getId())).thenReturn(Optional.empty());

        service.recordIncidentResolution(incident,
                "根因是 DemoService 未校验空请求参数，已完成最小修复并通过相关自动化测试验证。");

        ArgumentCaptor<KnowledgeEntry> saved = ArgumentCaptor.forClass(KnowledgeEntry.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().title()).isEqualTo("故障案例：广州人才日常运维");
        assertThat(saved.getValue().content()).contains("## 关键证据", "## 根因判断", "## 处置方案", "## 验证结果");
        assertThat(saved.getValue().content()).doesNotContain("system-notification", "task_id:");
    }

    @Test
    void shouldUpdateExistingCaseInsteadOfCreatingDuplicate() {
        IncidentTask incident = incident("[定时巡检] 广州人才日常运维");
        incident.getContext().put("topHypothesis", "下游连接池耗尽");
        when(repository.findBySourceIncidentId(incident.getId())).thenReturn(Optional.empty());
        KnowledgeEntry existing = new KnowledgeEntry("knowledge-1", "事件经验：广州人才日常运维",
                "INCIDENT", "自动沉淀", "OPS", "old", "old-incident", true,
                12, "operator", "created", "updated");
        when(repository.findAll()).thenReturn(List.of(existing));

        service.recordIncidentResolution(incident,
                "本次异常根因已定位为下游连接池耗尽，完成参数调整并观察确认服务已经恢复稳定运行。");

        verify(repository, never()).save(any());
        ArgumentCaptor<KnowledgeEntry> updated = ArgumentCaptor.forClass(KnowledgeEntry.class);
        verify(repository).update(updated.capture());
        assertThat(updated.getValue().id()).isEqualTo("knowledge-1");
        assertThat(updated.getValue().hitCount()).isEqualTo(12);
        assertThat(updated.getValue().title()).isEqualTo("故障案例：广州人才日常运维");
    }

    @Test
    void shouldDepositWhenVerifiedEvenIfSummaryIsShort() {
        IncidentTask incident = incident("[定时巡检] 广州人才日常运维");
        incident.getContext().put("patchTitle", "修复空指针保护");
        incident.getContext().put("patchSummary", "请求参数为空时增加前置校验");
        incident.getContext().put("automaticDeploymentSuccessful", true);
        when(repository.findBySourceIncidentId(incident.getId())).thenReturn(Optional.empty());

        service.recordIncidentResolution(incident, "制品已自动部署，主分支合并确认已完成");

        verify(repository).save(any());
    }

    private IncidentTask incident(String title) {
        IncidentTask task = new IncidentTask(title, "业务接口出现异常并影响用户请求", IncidentPriority.P2);
        task.setCreatedBy("operator");
        return task;
    }
}
