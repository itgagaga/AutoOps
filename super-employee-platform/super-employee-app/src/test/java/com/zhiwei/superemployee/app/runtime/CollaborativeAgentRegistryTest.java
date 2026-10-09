package com.zhiwei.superemployee.app.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CollaborativeAgentRegistry}：Harness 子智能体名与管理台 code 映射。
 */
class CollaborativeAgentRegistryTest {

    @Test
    void shouldMapLegacyHarnessNamesAndManagedCodes() {
        assertThat(CollaborativeAgentRegistry.toManagedCode("LOG_EXTRACTION"))
                .contains(CollaborativeAgentRegistry.LOG_EXTRACTION);
        assertThat(CollaborativeAgentRegistry.toManagedCode("evidence-collector"))
                .contains(CollaborativeAgentRegistry.LOG_EXTRACTION);
        assertThat(CollaborativeAgentRegistry.toManagedCode("root-cause-analyst"))
                .contains(CollaborativeAgentRegistry.ROOT_CAUSE_ANALYSIS);
        assertThat(CollaborativeAgentRegistry.toManagedCode("repair-planner"))
                .contains(CollaborativeAgentRegistry.CODE_REPAIR);
        assertThat(CollaborativeAgentRegistry.toManagedCode("verification-specialist"))
                .contains(CollaborativeAgentRegistry.TEST_VERIFICATION);
        assertThat(CollaborativeAgentRegistry.toManagedCode("unknown-agent")).isEmpty();
    }

    @Test
    void shouldExposeSpecialistCatalogAlignedWithManagedAgents() {
        assertThat(CollaborativeAgentRegistry.specialistCatalog().keySet())
                .containsExactly(
                        CollaborativeAgentRegistry.LOG_EXTRACTION,
                        CollaborativeAgentRegistry.ROOT_CAUSE_ANALYSIS,
                        CollaborativeAgentRegistry.CODE_REPAIR,
                        CollaborativeAgentRegistry.TEST_VERIFICATION);
    }
}
