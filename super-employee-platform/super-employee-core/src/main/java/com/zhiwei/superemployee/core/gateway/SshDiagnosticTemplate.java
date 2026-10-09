package com.zhiwei.superemployee.core.gateway;

/**
 * 仅允许执行的服务器只读诊断模板。
 * <p>
 * 与 {@link SshDiagnosticRequest} 组合使用，限制远程侧只能跑预置的健康检查与日志查看类命令，
 * 避免任意 shell 注入。
 * @author yangdb
 */
public enum SshDiagnosticTemplate {
    /** 主机基础健康（负载、内存等）。 */
    HOST_HEALTH,
    /** 指定服务的运行状态。 */
    SERVICE_STATUS,
    /** journald / 系统日志片段。 */
    JOURNAL_LOGS,
    /** 磁盘占用情况。 */
    DISK_USAGE,
    /** Java 应用相关日志。 */
    JAVA_APPLICATION_LOGS,
    /** 指定应用日志文件尾部。 */
    APP_LOG_FILE
}
