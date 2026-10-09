package com.zhiwei.superemployee.app.runtime;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent 会话租约管理器。
 * <p>
 * 保证同一会话在同一时刻仅被一个运行实例执行；获取租约后启动心跳续约，
 * 关闭租约时释放占用，供多实例部署下的安全恢复。
 * </p>
 * @author yangdb
 */
@Component
public class AgentLeaseManager {
    private final AgentSessionRepository sessions;
    private final Duration leaseDuration;
    private final Duration heartbeatInterval;
    private final String ownerId = "agent-runtime-" + UUID.randomUUID();
    private final ScheduledExecutorService heartbeatExecutor;

    /**
     * @param sessions         会话仓储
     * @param leaseSeconds     租约时长秒数，最小 10
     * @param heartbeatSeconds 心跳间隔秒数，不超过租约一半
     * @param heartbeatThreads 心跳线程池大小
     */
    public AgentLeaseManager(
            AgentSessionRepository sessions,
            @Value("${super-employee.recovery.lease-seconds:120}") long leaseSeconds,
            @Value("${super-employee.recovery.heartbeat-seconds:30}") long heartbeatSeconds,
            @Value("${super-employee.recovery.heartbeat-threads:8}") int heartbeatThreads) {
        this.sessions = sessions;
        this.leaseDuration = Duration.ofSeconds(Math.max(10, leaseSeconds));
        this.heartbeatInterval = Duration.ofSeconds(Math.max(1,
                Math.min(heartbeatSeconds, Math.max(1, leaseSeconds / 2))));
        int threads = Math.max(2, Math.min(32, heartbeatThreads));
        this.heartbeatExecutor = Executors.newScheduledThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "agent-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 获取会话执行租约并计入一次尝试。
     *
     * @param session 目标会话
     * @return 可自动关闭的租约句柄
     * @throws IllegalStateException 租约已被其他实例占用
     */
    public Lease acquire(AgentSession session) {
        return acquire(session, true);
    }

    /**
     * 获取会话续执行租约但不增加尝试次数。
     *
     * @param session 目标会话
     * @return 可自动关闭的租约句柄
     * @throws IllegalStateException 租约已被其他实例占用
     */
    public Lease acquireContinuation(AgentSession session) {
        return acquire(session, false);
    }

    /**
     * 原子占用租约并启动心跳续约。
     *
     * @param session      目标会话
     * @param countAttempt 是否增加尝试计数
     * @return 租约句柄
     * @throws IllegalStateException 无法获取租约
     */
    private Lease acquire(AgentSession session, boolean countAttempt) {
        Instant now = Instant.now();
        if (!sessions.tryAcquireLease(session, ownerId, now, leaseDuration)) {
            throw new IllegalStateException("会话已由其他运行实例接管");
        }
        AgentSession running;
        try {
            running = countAttempt
                    ? sessions.beginAttempt(session.getId(), ownerId, now, leaseDuration)
                    : sessions.resumeWithLease(session.getId(), ownerId, now, leaseDuration);
        }
        catch (RuntimeException ex) {
            sessions.releaseLease(session.getId(), ownerId);
            throw ex;
        }
        AtomicBoolean active = new AtomicBoolean(true);
        ScheduledFuture<?> heartbeat = heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (!active.get()) {
                return;
            }
            if (!sessions.renewLease(session.getId(), ownerId, Instant.now(), leaseDuration)) {
                active.set(false);
            }
        }, heartbeatInterval.toSeconds(), heartbeatInterval.toSeconds(), TimeUnit.SECONDS);
        return new Lease(running, active, heartbeat);
    }

    /**
     * @return 当前运行实例的租约所有者 ID
     */
    public String getOwnerId() {
        return ownerId;
    }

    /**
     * 容器销毁时关闭租约心跳线程池。
     */
    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
    }

    /**
     * 单次会话执行所持有的租约，关闭时停止心跳并释放。
     */
    public final class Lease implements AutoCloseable {
        private final AgentSession session;
        private final AtomicBoolean active;
        private final ScheduledFuture<?> heartbeat;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        /**
         * @param session   已被本实例接管的会话
         * @param active    租约是否仍有效
         * @param heartbeat 心跳任务
         */
        private Lease(AgentSession session, AtomicBoolean active, ScheduledFuture<?> heartbeat) {
            this.session = session;
            this.active = active;
            this.heartbeat = heartbeat;
        }

        /**
         * @return 已被本租约接管的会话
         */
        public AgentSession getSession() {
            return session;
        }

        /**
         * @return 心跳是否仍认为本实例持有有效租约
         */
        public boolean isActive() {
            return active.get() && !closed.get();
        }

        /**
         * 停止心跳并释放仍由当前实例持有的租约。
         * <p>即使心跳已因续约失败将 active 置为 false，仍会尝试释放，避免租约悬挂到过期。</p>
         */
        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            active.set(false);
            heartbeat.cancel(false);
            sessions.releaseLease(session.getId(), ownerId);
        }
    }
}
