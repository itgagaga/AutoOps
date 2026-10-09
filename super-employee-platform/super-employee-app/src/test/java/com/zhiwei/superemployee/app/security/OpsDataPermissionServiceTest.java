package com.zhiwei.superemployee.app.security;

import com.zhiwei.superemployee.app.ops.InMemoryOpsTaskRepository;
import com.zhiwei.superemployee.app.ops.OpsTask;
import com.zhiwei.superemployee.core.domain.IncidentPriority;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.repository.InMemoryIncidentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OpsDataPermissionService} 单测：普通用户隔离、无 createdBy 历史数据仅管理员可见。
 * @author yangdb
 */
class OpsDataPermissionServiceTest {
    private final InMemoryOpsTaskRepository opsTasks = new InMemoryOpsTaskRepository();
    private final InMemoryIncidentRepository incidents = new InMemoryIncidentRepository();
    private final OpsDataPermissionService service = new OpsDataPermissionService(opsTasks, incidents);

    /**
     * 每个用例结束后清理 SecurityContext，避免用例间认证状态串扰。
     */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 场景：alice 登录后仓库中同时存在本人、他人与 createdBy 为空的运维/故障数据。
     * 预期：可见与 require 仅覆盖本人数据；访问他人数据抛 404。
     */
    @Test
    void shouldOnlyReturnAndAccessCurrentUsersData() {
        OpsTask aliceOps = opsTask("alice");
        OpsTask bobOps = opsTask("bob");
        OpsTask legacyOps = opsTask(null);
        opsTasks.save(aliceOps);
        opsTasks.save(bobOps);
        opsTasks.save(legacyOps);
        IncidentTask aliceIncident = incident("alice");
        IncidentTask bobIncident = incident("bob");
        incidents.save(aliceIncident);
        incidents.save(bobIncident);
        authenticate("alice", "OPS_DASHBOARD");

        assertThat(service.visibleOpsTasks()).extracting(OpsTask::getId).containsExactly(aliceOps.getId());
        assertThat(service.visibleIncidents()).extracting(IncidentTask::getId)
                .containsExactly(aliceIncident.getId());
        assertThat(service.requireOpsTask(aliceOps.getId())).isSameAs(aliceOps);
        assertThatThrownBy(() -> service.requireOpsTask(bobOps.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThatThrownBy(() -> service.requireIncident(bobIncident.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    /**
     * 场景：管理员查看含归属用户与 createdBy 为空的历史运维任务。
     * 预期：visibleOpsTasks 包含全部；requireOpsTask 对归属行与历史行均可放行。
     */
    @Test
    void shouldAllowAdminToSeeAllIncludingLegacyRows() {
        OpsTask owned = opsTask("alice");
        OpsTask legacy = opsTask(null);
        opsTasks.save(owned);
        opsTasks.save(legacy);
        authenticate("admin", "ROLE_ADMIN");

        assertThat(service.visibleOpsTasks()).containsExactlyInAnyOrder(owned, legacy);
        assertThat(service.requireOpsTask(owned.getId())).isSameAs(owned);
        assertThat(service.requireOpsTask(legacy.getId())).isSameAs(legacy);
    }

    /**
     * 构造带创建人的运维任务测试数据。
     *
     * @param createdBy 创建人，允许 {@code null} 模拟历史数据
     * @return 未持久化前的任务实体（调用方负责 save）
     */
    private OpsTask opsTask(String createdBy) {
        OpsTask task = new OpsTask("任务", "描述", "P2", null, "D:/workspace");
        task.setCreatedBy(createdBy);
        return task;
    }

    /**
     * 构造带创建人的故障任务测试数据。
     *
     * @param createdBy 创建人
     * @return 故障实体
     */
    private IncidentTask incident(String createdBy) {
        IncidentTask task = new IncidentTask("故障", "描述", IncidentPriority.P2);
        task.setCreatedBy(createdBy);
        return task;
    }

    /**
     * 写入指定用户与单个权限到 SecurityContext。
     *
     * @param username  登录名
     * @param authority Authority 字符串
     */
    private void authenticate(String username, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username, null, List.of(new SimpleGrantedAuthority(authority))));
    }
}
