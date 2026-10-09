package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.core.gateway.SshDiagnosticGateway;
import com.zhiwei.superemployee.core.gateway.SshDiagnosticRequest;
import com.zhiwei.superemployee.core.gateway.SshDiagnosticResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SSH 诊断未启用时的占位实现。
 * <p>在 {@code super-employee.ssh.enabled=false}（默认）时注册，调用即抛出明确异常。</p>
 * @author yangdb
 */
@Component
@ConditionalOnProperty(prefix = "super-employee.ssh", name = "enabled", havingValue = "false", matchIfMissing = true)
public class DisabledSshDiagnosticGateway implements SshDiagnosticGateway {
    /**
     * 拒绝在未启用 SSH 时执行诊断。
     *
     * @param request 诊断请求（未使用）
     * @return 不会返回
     * @throws IllegalStateException 始终抛出，提示 SSH 未启用
     */
    @Override
    public SshDiagnosticResult execute(SshDiagnosticRequest request) {
        throw new IllegalStateException("SSH 只读诊断未启用");
    }
}
