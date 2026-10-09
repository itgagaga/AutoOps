package com.zhiwei.superemployee.app.runtime;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLTransientConnectionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentFailureClassifier} 单测：区分可重试的瞬时故障与不可重试的业务/参数错误。
 * @author yangdb
 */
class AgentFailureClassifierTest {
    private final AgentFailureClassifier classifier = new AgentFailureClassifier();

    /**
     * 场景：分别输入 IO/超时/临时数据库异常与 IllegalArgumentException。
     * 预期：前三类 classify 结果 retryable=true；参数错误 retryable=false。
     */
    @Test
    void shouldRetryOnlyTransientFailures() {
        assertThat(classifier.classify(new RuntimeException(new IOException("断网"))).retryable())
                .isTrue();
        assertThat(classifier.classify(new TimeoutException("超时")).retryable()).isTrue();
        assertThat(classifier.classify(
                new SQLTransientConnectionException("数据库暂不可用")).retryable()).isTrue();
        assertThat(classifier.classify(new IllegalArgumentException("参数错误")).retryable())
                .isFalse();
    }
}
