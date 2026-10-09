package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.system.SystemSettingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RoutingLocalRepairGateway}：固定引擎、auto 失败切换与越权不切换。
 * @author yangdb
 */
@ExtendWith(MockitoExtension.class)
class RoutingLocalRepairGatewayTest {

    @Mock
    SystemSettingRepository settings;
    @Mock
    ObjectProvider<CodexCliRepairGateway> codexProvider;
    @Mock
    ObjectProvider<CursorCliRepairGateway> cursorProvider;
    @Mock
    ObjectProvider<ClaudeCodeCliRepairGateway> claudeProvider;
    @Mock
    CodexCliRepairGateway codex;
    @Mock
    CursorCliRepairGateway cursor;
    @Mock
    ClaudeCodeCliRepairGateway claude;

    RoutingLocalRepairGateway gateway;

    @BeforeEach
    void setUp() {
        lenient().when(settings.find(RoutingLocalRepairGateway.KEY_ENGINE)).thenReturn(Optional.of("codex"));
        gateway = new RoutingLocalRepairGateway(settings, "codex", codexProvider, cursorProvider, claudeProvider);
    }

    @Test
    void shouldUsePinnedEngineWithoutFailover() throws Exception {
        when(codexProvider.getIfAvailable()).thenReturn(codex);
        when(codex.engineDisplayName()).thenReturn("CodeX CLI");
        LocalRepairGateway.Result success = new LocalRepairGateway.Result(
                true, "ok", "diff", List.of("a.java"), true, "11111111-1111-1111-1111-111111111111");
        when(codex.repair(anyString(), anyList(), anyString())).thenReturn(success);

        RoutingLocalRepairGateway.PreferencedResult result =
                gateway.repairWithPreference("fix", List.of(), "/ws", "codex");

        assertThat(result.result().success()).isTrue();
        assertThat(result.engineId()).isEqualTo("codex");
        assertThat(result.switched()).isFalse();
        verify(cursorProvider, never()).getIfAvailable();
    }

    @Test
    void shouldFailWhenPinnedEngineUnavailable() throws Exception {
        when(cursorProvider.getIfAvailable()).thenReturn(null);

        RoutingLocalRepairGateway.PreferencedResult result =
                gateway.repairWithPreference("fix", List.of(), "/ws", "cursor");

        assertThat(result.result().success()).isFalse();
        assertThat(result.engineId()).isEqualTo("cursor");
        assertThat(result.result().output()).contains("未启用");
    }

    @Test
    void shouldFailoverOnRetryableFailureThenSucceed() throws Exception {
        when(codexProvider.getIfAvailable()).thenReturn(codex);
        when(cursorProvider.getIfAvailable()).thenReturn(cursor);
        when(claudeProvider.getIfAvailable()).thenReturn(null);
        when(codex.engineDisplayName()).thenReturn("CodeX CLI");
        when(cursor.engineDisplayName()).thenReturn("Cursor CLI");

        String repairId = "22222222-2222-2222-2222-222222222222";
        LocalRepairGateway.Result failed = new LocalRepairGateway.Result(
                false, "usage_limit_exceeded", "", List.of(), true, repairId);
        LocalRepairGateway.Result success = new LocalRepairGateway.Result(
                true, "fixed", "diff", List.of("b.java"), true, "33333333-3333-3333-3333-333333333333");
        when(codex.repair(anyString(), anyList(), anyString())).thenReturn(failed);
        when(codex.review(eq("/ws"), eq(repairId), eq(false)))
                .thenReturn(new LocalRepairGateway.ReviewResult(true, "REVERTED", "ok"));
        when(cursor.repair(anyString(), anyList(), anyString())).thenReturn(success);

        RoutingLocalRepairGateway.PreferencedResult result =
                gateway.repairWithPreference("fix", List.of(), "/ws", "auto");

        assertThat(result.result().success()).isTrue();
        assertThat(result.engineId()).isEqualTo("cursor");
        assertThat(result.switched()).isTrue();
        assertThat(result.attemptsSummary()).contains("CodeX", "Cursor");
        verify(codex).review("/ws", repairId, false);
    }

    @Test
    void shouldNotFailoverWhenOutOfScope() throws Exception {
        when(codexProvider.getIfAvailable()).thenReturn(codex);
        when(cursorProvider.getIfAvailable()).thenReturn(cursor);
        when(claudeProvider.getIfAvailable()).thenReturn(null);
        when(codex.engineDisplayName()).thenReturn("CodeX CLI");

        LocalRepairGateway.Result outOfScope = new LocalRepairGateway.Result(
                false, "changed secrets", "diff", List.of("../x"), false,
                "44444444-4444-4444-4444-444444444444");
        when(codex.repair(anyString(), anyList(), anyString())).thenReturn(outOfScope);

        RoutingLocalRepairGateway.PreferencedResult result =
                gateway.repairWithPreference("fix", List.of(), "/ws", "auto");

        assertThat(result.result().success()).isFalse();
        assertThat(result.engineId()).isEqualTo("codex");
        assertThat(result.switched()).isFalse();
        verify(cursor, never()).repair(anyString(), anyList(), any());
        verify(codex, never()).review(anyString(), anyString(), anyBoolean());
    }

    @Test
    void shouldRejectUnknownPreference() {
        assertThatThrownBy(() -> gateway.repairWithPreference("fix", List.of(), "/ws", "gemini"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("auto");
    }

    @Test
    void shouldFailoverPinnedEngineWhenCliMissing() throws Exception {
        when(codexProvider.getIfAvailable()).thenReturn(codex);
        when(cursorProvider.getIfAvailable()).thenReturn(cursor);
        when(claudeProvider.getIfAvailable()).thenReturn(null);
        when(codex.engineDisplayName()).thenReturn("CodeX CLI");
        when(cursor.engineDisplayName()).thenReturn("Cursor CLI");

        LocalRepairGateway.Result missing = new LocalRepairGateway.Result(
                false, "CodeX CLI 无法启动：未找到 CodeX CLI 可执行文件", "", List.of(), true, null);
        LocalRepairGateway.Result success = new LocalRepairGateway.Result(
                true, "fixed", "diff", List.of("b.java"), true, "55555555-5555-5555-5555-555555555555");
        when(codex.repair(anyString(), anyList(), anyString())).thenReturn(missing);
        when(cursor.repair(anyString(), anyList(), anyString())).thenReturn(success);

        RoutingLocalRepairGateway.PreferencedResult result =
                gateway.repairWithPreference("fix", List.of(), "/ws", "codex");

        assertThat(result.result().success()).isTrue();
        assertThat(result.engineId()).isEqualTo("cursor");
        assertThat(result.switched()).isTrue();
        assertThat(result.attemptsSummary()).contains("不可用").contains("Cursor");
        verify(cursor).repair(anyString(), anyList(), anyString());
    }

    @Test
    void isCliUnavailableDetectsMissingExecutable() {
        LocalRepairGateway.Result missing = new LocalRepairGateway.Result(
                false, "未找到 CodeX CLI 可执行文件", "", List.of(), true, null);
        LocalRepairGateway.Result quota = new LocalRepairGateway.Result(
                false, "usage_limit_exceeded", "", List.of(), true, null);
        assertThat(RoutingLocalRepairGateway.isCliUnavailable(missing)).isTrue();
        assertThat(RoutingLocalRepairGateway.isCliUnavailable(quota)).isFalse();
    }

    @Test
    void isRetryableRejectsOutOfScope() {
        LocalRepairGateway.Result outOfScope = new LocalRepairGateway.Result(
                false, "bad", "", List.of("x"), false, null);
        assertThat(RoutingLocalRepairGateway.isRetryable(outOfScope)).isFalse();
    }
}
