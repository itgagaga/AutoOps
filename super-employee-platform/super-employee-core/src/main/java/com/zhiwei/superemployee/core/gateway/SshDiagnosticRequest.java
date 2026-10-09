package com.zhiwei.superemployee.core.gateway;

/**
 * 由上层完成授权和参数校验后的只读 SSH 诊断请求。
 * <p>
 * 交给 {@link SshDiagnosticGateway#execute} 执行；模板枚举见 {@link SshDiagnosticTemplate}，
 * 部分字段仅在对应模板下有意义（如 {@code serviceName}、{@code logPath}）。
 *
 * @param hostId       目标主机标识
 * @param template     只读诊断模板类型
 * @param serviceName  服务名（如 {@link SshDiagnosticTemplate#SERVICE_STATUS} 使用）
 * @param logPath      日志文件路径（如 {@link SshDiagnosticTemplate#APP_LOG_FILE} 使用）
 * @param sinceMinutes 回溯分钟数（日志类模板）
 * @param maxLines     最大返回行数
 * @author yangdb
 */
public record SshDiagnosticRequest(
        String hostId,
        SshDiagnosticTemplate template,
        String serviceName,
        String logPath,
        int sinceMinutes,
        int maxLines) {
}
