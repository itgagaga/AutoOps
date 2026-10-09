package com.zhiwei.superemployee.agent.starter;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本进程任务计数与运行态，供心跳上报使用。
 */
@Component
public class SpecialistRuntimeStats {
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger total = new AtomicInteger();
    private final AtomicInteger success = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    public void begin() {
        active.incrementAndGet();
        total.incrementAndGet();
    }

    public void endSuccess() {
        active.decrementAndGet();
        success.incrementAndGet();
    }

    public void endFailed() {
        active.decrementAndGet();
        failed.incrementAndGet();
    }

    public String runtimeStatus() {
        return active.get() > 0 ? "RUNNING" : "IDLE";
    }

    public int totalTasks() {
        return total.get();
    }

    public int successTasks() {
        return success.get();
    }

    public int failedTasks() {
        return failed.get();
    }
}
