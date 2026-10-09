package com.zhiwei.superemployee.app.ops;

import com.zhiwei.superemployee.app.ssh.ManagedSshConnector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DeploymentPathDiscoveryService} 单测：评分择优、路径规范化，以及未启用/不存在主机的前置拒绝。
 * @author yangdb
 */
class DeploymentPathDiscoveryServiceTest {
    private final ManagedSshConnector connector = mock(ManagedSshConnector.class);
    private final DeploymentPathDiscoveryService service =
            new DeploymentPathDiscoveryService(connector, 20);

    /**
     * 场景：候选目录中同时存在“正在运行的 Java 进程”与“标准目录标记（jar/脚本等）”。
     * 预期：运行中进程得分更高（100 优于 80），最终择优路径为运行中进程所在目录。
     */
    @Test
    void shouldPreferRunningJavaProcessOverDirectoryMarkers() {
        var running = service.scoreCandidate("/opt/order", true, false, false, false);
        var standard = service.scoreCandidate("/srv/order", false, true, true, true);

        var result = service.selectBest(List.of(standard, running));

        assertThat(result.detected()).isTrue();
        assertThat(result.deploymentPath()).isEqualTo("/opt/order");
        assertThat(running.score()).isEqualTo(100);
        assertThat(standard.score()).isEqualTo(80);
    }

    /**
     * 场景：应用已停止，仅能依赖标准 jar/启动脚本等目录标记打分。
     * 预期：具备更完整标准标记的目录被选为部署路径。
     */
    @Test
    void shouldRecognizeStoppedApplicationFromStandardJar() {
        var result = service.selectBest(List.of(
                service.scoreCandidate("/opt/order", false, true, true, true),
                service.scoreCandidate("/srv/order", false, false, false, true)));

        assertThat(result.detected()).isTrue();
        assertThat(result.deploymentPath()).isEqualTo("/opt/order");
    }

    /**
     * 场景：多个同等可信候选并存，或所有候选均无有效标记。
     * 预期：歧义时 {@code detected=false}；无匹配时返回消息包含「未发现」。
     */
    @Test
    void shouldRejectAmbiguousAndMissingMatches() {
        var first = service.scoreCandidate("/opt/a", false, false, true, false);
        var second = service.scoreCandidate("/opt/b", false, false, true, false);

        var ambiguous = service.selectBest(List.of(first, second));
        assertThat(ambiguous.detected()).isFalse();
        assertThat(ambiguous.message()).contains("绑定");
        assertThat(service.selectBest(List.of(
                service.scoreCandidate("/opt/none", false, false, false, false))).message())
                .contains("未发现");
    }

    /**
     * 场景：候选路径含尾部斜杠重复项、路径穿越与相对路径。
     * 预期：规范化后去重；含 {@code ..} 或非绝对路径时抛出 {@link IllegalArgumentException}。
     */
    @Test
    void shouldNormalizeDeduplicateAndRejectUnsafePaths() {
        assertThat(service.normalizeCandidates(List.of("/opt/order/", "/opt/order", "/srv/order")))
                .containsExactly("/opt/order", "/srv/order");
        assertThatThrownBy(() -> service.normalizeCandidates(List.of("/opt/../etc")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.normalizeCandidates(List.of("relative/path")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * 场景：浏览目录或自动探测时主机不存在/未启用。
     * 预期：在打开 SSH 连接前即抛出对应异常，且绝不调用 {@code connector.open}。
     */
    @Test
    void shouldRejectMissingOrDisabledHostBeforeBrowsing() throws Exception {
        when(connector.requireEnabledHost("missing"))
                .thenThrow(new IllegalArgumentException("主机不存在"));
        when(connector.requireEnabledHost("disabled"))
                .thenThrow(new IllegalStateException("主机未启用"));

        assertThatThrownBy(() -> service.listDirectories("missing", "/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("主机不存在");
        assertThatThrownBy(() -> service.detect("disabled", "order", List.of("/opt/order")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("主机未启用");
        verify(connector, never()).open(anyString());
    }
}
