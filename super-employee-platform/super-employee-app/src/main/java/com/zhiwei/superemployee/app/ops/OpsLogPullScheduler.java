package com.zhiwei.superemployee.app.ops;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 运维周期拉日志的调度入口。
 * <p>
 * 在应用就绪后恢复已启用任务，并按固定间隔扫描到期任务；
 * 另以独立 cron 触发本地日志清理。可通过 {@code super-employee.ops-log.enabled}
 * 关闭整组调度。
 * </p>
 * @author yangdb
 */
@Component
@ConditionalOnProperty(prefix = "super-employee.ops-log", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpsLogPullScheduler {
    private final OpsLogPullService pullService;
    private final OpsLogCleanupService cleanupService;

    /**
     * @param pullService    日志拉取与分析触发服务
     * @param cleanupService 本地冗余日志清理服务
     */
    public OpsLogPullScheduler(OpsLogPullService pullService, OpsLogCleanupService cleanupService) {
        this.pullService = pullService;
        this.cleanupService = cleanupService;
    }

    /**
     * 应用完全启动后立即恢复所有已启用的定时运维任务。
     * <p>不要求用户重新切换启用状态，确保进程重启后巡检不中断。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeAfterRestart() {
        pullService.resumeEnabledTasks();
    }

    /**
     * 扫描并提交已到期的周期拉日志任务。
     * <p>初始延迟与扫描间隔由配置 {@code super-employee.ops-log.*} 控制。</p>
     */
    @Scheduled(
            initialDelayString = "${super-employee.ops-log.initial-delay-ms:5000}",
            fixedDelayString = "${super-employee.ops-log.scan-interval-ms:60000}")
    public void scan() {
        pullService.pullDueTasks();
    }

    /**
     * 独立清理过期及超出数量、容量配额的本地日志。
     * <p>默认每小时第 15 分钟执行，cron 可通过配置覆盖。</p>
     */
    @Scheduled(cron = "${super-employee.ops-log.cleanup-cron:0 15 * * * *}")
    public void cleanup() {
        cleanupService.cleanup();
    }
}
