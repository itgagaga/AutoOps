package com.zhiwei.superemployee.core.repository;

import com.zhiwei.superemployee.core.domain.AgentStepRecord;
import com.zhiwei.superemployee.core.domain.IncidentPriority;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryIncidentRepositoryConcurrencyTest {

    @Test
    void shouldRetainAllTimelineRecordsAppendedByConcurrentAgents() throws Exception {
        InMemoryIncidentRepository repository = new InMemoryIncidentRepository();
        IncidentTask task = repository.save(new IncidentTask("并发排障", "多个独立 Agent 回传", IncidentPriority.P1));
        int agentCount = 64;
        CountDownLatch ready = new CountDownLatch(agentCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(agentCount);
        try {
            for (int index = 0; index < agentCount; index++) {
                int agentIndex = index;
                executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    repository.appendTimeline(task.getId(), new AgentStepRecord(
                            "agent-" + agentIndex, true, "result-" + agentIndex, Instant.now()));
                    return null;
                });
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(repository.findById(task.getId()).orElseThrow().getTimeline())
                .hasSize(agentCount)
                .extracting(AgentStepRecord::getStep)
                .doesNotHaveDuplicates();
    }
}
