package com.zhiwei.superemployee.core.gateway;

/**
 * 已截断、可安全审计的 SSH 诊断执行结果。
 * <p>
 * 由 {@link SshDiagnosticGateway#execute} 返回，供上层展示摘要与完整（或截断）输出。
 *
 * @param success   诊断命令是否成功
 * @param summary   面向用户的简短结论
 * @param output    命令原始或规范化输出
 * @param truncated 输出是否因长度限制被截断
 * @author yangdb
 */
public record SshDiagnosticResult(
        boolean success,
        String summary,
        String output,
        boolean truncated) {
}
