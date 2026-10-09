package com.zhiwei.superemployee.app.runtime;

import io.agentscope.core.model.ExecutionConfig;
import org.springframework.stereotype.Component;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;

/**
 * Agent 执行失败分类器。
 * <p>根据异常类型判断是否允许自动重试（模型/网络瞬时故障、数据库瞬时故障等）。</p>
 * @author yangdb
 */
@Component
public class AgentFailureClassifier {

    /**
     * 将异常归类为可重试或不可重试故障。
     *
     * @param error 原始异常
     * @return 故障类型与是否可重试
     */
    public Failure classify(Throwable error) {
        if (ExecutionConfig.RETRYABLE_ERRORS.test(error)) {
            return new Failure("MODEL_OR_NETWORK_TRANSIENT", true);
        }
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLTransientException
                    || current instanceof SQLRecoverableException) {
                return new Failure("DATABASE_TRANSIENT", true);
            }
            current = current.getCause();
        }
        return new Failure("NON_RETRYABLE", false);
    }

    /**
     * 描述故障类型及其重试资格。
     *
     * @param type      故障类型编码
     * @param retryable 是否允许自动重试
     */
    public record Failure(String type, boolean retryable) {
    }
}
