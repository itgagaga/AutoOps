package com.zhiwei.superemployee.app.knowledge;

import com.zhiwei.superemployee.app.ops.InMemoryOpsTaskRepository;
import com.zhiwei.superemployee.app.security.OpsDataPermissionService;
import com.zhiwei.superemployee.core.repository.InMemoryIncidentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库行级权限单测：管理列表隔离、越权更新 404、检索仍共享只读。
 * @author yangdb
 */
class KnowledgePermissionTest {
    private KnowledgeRepository repository;
    private KnowledgeService service;

    /**
     * 构造 mock 仓储与真实 {@link OpsDataPermissionService}，并以持有 OPS_KNOWLEDGE 的 alice 登录。
     */
    @BeforeEach
    void setUp() {
        repository = mock(KnowledgeRepository.class);
        OpsDataPermissionService permission = new OpsDataPermissionService(
                new InMemoryOpsTaskRepository(), new InMemoryIncidentRepository());
        service = new KnowledgeService(repository, permission);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "alice", null, List.of(new SimpleGrantedAuthority("OPS_KNOWLEDGE"))));
    }

    /**
     * 每个用例结束后清理 SecurityContext，避免认证状态串扰。
     */
    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /**
     * 场景：非管理员调用知识库管理列表。
     * 预期：仅走 findAllByCreatedBy(alice)，绝不调用全量 findAll。
     */
    @Test
    void listOnlyReturnsOwnEntriesForNonAdmin() {
        when(repository.findAllByCreatedBy("alice")).thenReturn(List.of(entry("k1", "alice")));
        assertThat(service.list()).extracting(KnowledgeEntry::id).containsExactly("k1");
        verify(repository, never()).findAll();
    }

    /**
     * 场景：alice 尝试更新归属 bob 的知识条目。
     * 预期：抛出 404，且仓储 update 不被调用。
     */
    @Test
    void updateForeignEntryReturns404() {
        when(repository.findById("k-bob")).thenReturn(Optional.of(entry("k-bob", "bob")));
        assertThatThrownBy(() -> service.save("k-bob", "标题", "RUNBOOK", "", "OPS", "内容", true))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verify(repository, never()).update(any());
    }

    /**
     * 场景：检索命中他人启用条目与本人停用条目。
     * 预期：search 仍可读共享启用内容，仅返回他人启用条目，停用条目不出现。
     */
    @Test
    void searchStillReadsAllEnabledEntries() {
        when(repository.findAll()).thenReturn(List.of(
                entry("k-bob", "bob"),
                new KnowledgeEntry("k2", "停用", "RUNBOOK", "", "OPS", "内容", null, false, 0, "alice", "now", "now")));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        assertThat(service.search("内容", "OPS", 5)).extracting(KnowledgeEntry::id).containsExactly("k-bob");
    }

    /**
     * 构造启用中的测试条目。
     *
     * @param id    主键
     * @param owner 创建人
     * @return 领域模型
     */
    private KnowledgeEntry entry(String id, String owner) {
        return new KnowledgeEntry(id, "标题", "RUNBOOK", "标签", "OPS", "内容正文足够长", null, true, 0, owner, "now", "now");
    }
}
