package com.zhiwei.superemployee.core.gateway;

/**
 * SSH 只读诊断网关：在上层完成授权与参数校验后，执行安全的远程诊断命令。
 * <p>
 * 请求见 {@link SshDiagnosticRequest}，结果见 {@link SshDiagnosticResult}；
 * 允许的诊断类型由 {@link SshDiagnosticTemplate} 枚举约束。
 * @author yangdb
 */
public interface SshDiagnosticGateway {
    /**
     * 执行已授权的只读 SSH 诊断请求。
     *
     * @param request 含主机、模板与参数的诊断请求
     * @return 可审计的执行结果（可能已截断输出）
     */
    SshDiagnosticResult execute(SshDiagnosticRequest request);
}
