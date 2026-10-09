package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.agent.ManagedAgent;
import com.zhiwei.superemployee.app.agent.ManagedAgentRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpecialistCatalogServiceTest {

    @Test
    void shouldOnlyReturnEnabledOnlineSpecialistsWithEndpoint() {
        ManagedAgentRepository repository = mock(ManagedAgentRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                agent("LOG_EXTRACTION", "REMOTE", "http://127.0.0.1:8101", "IDLE", true),
                agent("ROOT_CAUSE_ANALYSIS", "REMOTE", "http://127.0.0.1:8102", "OFFLINE", true),
                agent("CODE_REPAIR", "REMOTE", "http://127.0.0.1:8103", "IDLE", false),
                agent("OPS_REACT", "REACT", null, "RUNNING", true)));

        SpecialistCatalogService catalog = new SpecialistCatalogService(repository, Optional.empty());

        assertThat(catalog.listOnlineSpecialists())
                .extracting(SpecialistDescriptor::code)
                .containsExactly("LOG_EXTRACTION");
        assertThat(catalog.onlineCatalogPrompt()).contains("LOG_EXTRACTION");
        assertThat(catalog.findOnline("log-extraction")).isPresent();
        assertThat(catalog.findOnline("ROOT_CAUSE_ANALYSIS")).isEmpty();
    }

    @Test
    void shouldExposeEnabledBuiltInAgentWithoutRemoteEndpoint() {
        ManagedAgentRepository repository = mock(ManagedAgentRepository.class);
        LocalSpecialistRuntime local = mock(LocalSpecialistRuntime.class);
        when(local.supports("LOG_EXTRACTION")).thenReturn(true);
        when(local.status("LOG_EXTRACTION")).thenReturn("IDLE");
        when(repository.findAll()).thenReturn(List.of(
                agent("LOG_EXTRACTION", "BUILT_IN", null, "OFFLINE", true)));

        SpecialistCatalogService catalog = new SpecialistCatalogService(repository, Optional.of(local));

        assertThat(catalog.listOnlineSpecialists())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.code()).isEqualTo("LOG_EXTRACTION");
                    assertThat(item.endpointUrl()).isNull();
                    assertThat(item.runtimeStatus()).isEqualTo("IDLE");
                });
    }

    private static ManagedAgent agent(String code, String type, String endpoint, String status,
                                      boolean enabled) {
        return new ManagedAgent("id-" + code, code, code, type, "desc", endpoint, null,
                enabled, status, "now", 0, 0, 0, "now", "now");
    }
}
