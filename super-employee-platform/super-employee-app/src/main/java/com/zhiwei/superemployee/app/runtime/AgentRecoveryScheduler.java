package com.zhiwei.superemployee.app.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Agent 会话恢复调度器。
 * <p>定期扫描租约超时或到期可重试的会话，交由 {@link ReactAgentRuntimeService#recover} 接管。</p>
 * @author yangdb
 */
@Component
@ConditionalOnProperty(prefix = "super-employee.react", name = "enabled", havingValue = "true")
public class AgentRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(AgentRecoveryScheduler.class);

    private final AgentSessionRepository sessions;
    private final ReactAgentRuntimeService runtime;
    private final int batchSize;

    /**
     * @param sessions  会话仓储
     * @param runtime   React Agent 运行时
     * @param batchSize 每轮最多恢复的会话数
     */
    public AgentRecoveryScheduler(
            AgentSessionRepository sessions,
            ReactAgentRuntimeService runtime,
            @Value("${super-employee.recovery.batch-size:10}") int batchSize) {
        this.sessions = sessions;
        this.runtime = runtime;
        this.batchSize = Math.max(1, batchSize);
    }

    /**
     * 定期接管超时或到期重试的会话。
     * <p>已被其他实例持有租约时仅记录 debug，其它运行时异常记 error 后继续下一会话。</p>
     */
    @Scheduled(fixedDelayString = "${super-employee.recovery.scan-interval-ms:30000}")
    public void recoverDueSessions() {
        for (AgentSession session : sessions.findRecoverable(Instant.now(), batchSize)) {
            try {
                runtime.recover(session);
            }
            catch (IllegalStateException ex) {
                log.debug("会话 {} 未被当前实例接管：{}", session.getId(), ex.getMessage());
            }
            catch (RuntimeException ex) {
                log.error("恢复 AgentScope 会话 {} 失败", session.getId(), ex);
            }
        }
    }
}
