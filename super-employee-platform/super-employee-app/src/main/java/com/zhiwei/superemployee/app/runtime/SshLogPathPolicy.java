package com.zhiwei.superemployee.app.runtime;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 主机应用日志路径白名单与注入防护。
 * <p>白名单既可配置单个日志文件，也可配置日志目录（兼容按日滚动文件）。</p>
 * @author yangdb
 */
public final class SshLogPathPolicy {
    /** 工具类禁止实例化。 */
    private SshLogPathPolicy() {
    }

    /**
     * 解析主机上配置的允许路径列表。
     *
     * @param raw 原始字符串
     * @return 结果列表
     */
    public static List<String> parseAllowed(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(SshLogPathPolicy::normalize)
                .distinct()
                .toList();
    }

    /**
     * 校验并规范化单个绝对路径（去掉末尾多余 /）。
     *
     * @param value 取值
     * @return 校验并规范化单个绝对路径（去掉末尾多余 /）
     */
    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("日志路径不能为空");
        }
        String path = value.trim();
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith("/") || path.contains("..") || !path.matches("/[A-Za-z0-9_./-]+")) {
            throw new IllegalArgumentException("日志路径必须是安全的绝对路径");
        }
        return path;
    }

    /**
     * 路径最后一段不含扩展名时视为目录（如 {@code /opt/app/logs}）；
     *
     * @param path 路径
     * @return 是否满足条件
     */
    public static boolean looksLikeDirectory(String path) {
        String normalized = normalize(path);
        String name = normalized.substring(normalized.lastIndexOf('/') + 1);
        return !name.contains(".");
    }

    /**
     * 要求请求路径命中主机白名单：精确匹配，或位于白名单目录之下。
     *
     * @param requested requested
     * @param allowedRaw allowedRaw
     * @return 要求请求路径命中主机白名单：精确匹配，或位于白名单目录之下
     */
    public static String requireAllowed(String requested, String allowedRaw) {
        String path = normalize(requested);
        Set<String> allowed = new LinkedHashSet<>(parseAllowed(allowedRaw));
        if (allowed.isEmpty()) {
            throw new IllegalStateException("主机未配置允许读取的日志路径白名单");
        }
        if (allowed.contains(path)) {
            return path;
        }
        for (String entry : allowed) {
            if (path.startsWith(entry + "/")) {
                return path;
            }
        }
        throw new IllegalArgumentException("日志路径不在主机白名单内：" + path
                + "；允许：" + allowed.stream().collect(Collectors.joining(", ")));
    }

    /**
     * 为 APP_LOG_FILE 生成只读取数命令。
     *
     * @param requested requested
     * @param allowedRaw allowedRaw
     * @param sinceMinutes sinceMinutes
     * @param lines lines
     * @return 为 APP_LOG_FILE 生成只读取数命令
     */
    public static String buildReadCommand(String requested, String allowedRaw, int sinceMinutes, int lines) {
        String path = requireAllowed(requested, allowedRaw);
        Set<String> allowed = new LinkedHashSet<>(parseAllowed(allowedRaw));
        boolean directoryTarget = allowed.contains(path) && looksLikeDirectory(path);
        if (!directoryTarget) {
            return buildTimeWindowRead(path, sinceMinutes, lines);
        }
        return "sh -lc " + singleQuote(
                "d=" + path + "; "
                        + "cd -- \"$d\" || exit 1; "
                        + "today=$(date +%F); "
                        + "f=$(ls -1t -- *\"$today\"* 2>/dev/null | head -n 1); "
                        + "[ -n \"$f\" ] || f=$(ls -1t -- *.log *.log.* *.out 2>/dev/null | head -n 1); "
                        + "if [ -z \"$f\" ] || [ ! -r \"$f\" ]; then "
                        + "echo '未找到可读的分日/滚动日志文件: '\"$d\" >&2; exit 1; "
                        + "fi; "
                        + "printf '=== %s/%s ===\\n' \"$d\" \"$f\"; "
                        + timeWindowScript("\"$f\"", sinceMinutes, lines));
    }

    /**
     * 兼容原有调用方：未显式指定时间窗口时按最近 10 分钟读取。
     *
     * @param requested requested
     * @param allowedRaw allowedRaw
     * @param lines lines
     * @return 兼容原有调用方：未显式指定时间窗口时按最近 10 分钟读取
     */
    public static String buildReadCommand(String requested, String allowedRaw, int lines) {
        return buildReadCommand(requested, allowedRaw, 10, lines);
    }

    /**
     * 针对单个日志文件生成带时间窗口过滤的 {@code sh -lc} 读命令。
     *
     * @param path         已校验的绝对路径
     * @param sinceMinutes 回溯分钟数
     * @param lines        无时间戳时回退的 tail 行数
     * @return 可直接经 SSH 执行的 shell 命令
     */
    private static String buildTimeWindowRead(String path, int sinceMinutes, int lines) {
        return "sh -lc " + singleQuote(
                "f=" + path + "; "
                        + "printf '=== %s ===\\n' \"$f\"; "
                        + timeWindowScript("\"$f\"", sinceMinutes, lines));
    }

    /**
     * 生成按时间戳过滤日志的 awk/tail 脚本片段。
     * <p>只扫描文件尾部探针窗口，避免对超大日志全量 {@code awk}/{@code grep} 导致 SSH 命令超时。
     * 若尾部含标准时间戳则按 {@code sinceMinutes} 过滤；否则回退 {@code tail -n}。</p>
     *
     * @param fileExpression Shell 中的文件表达式（可为带引号的变量）
     * @param sinceMinutes   回溯分钟数，限制在 1～1440
     * @param fallbackLines  无时间戳时的尾部行数
     * @return 嵌入 {@code sh -lc} 的脚本片段
     */
    private static String timeWindowScript(String fileExpression, int sinceMinutes, int fallbackLines) {
        int minutes = Math.max(1, Math.min(1440, sinceMinutes));
        int lines = Math.max(1, fallbackLines);
        // 只读尾部：按时间窗与行数估算探针行数，上限 10 万行，避免扫完整日/滚动巨文件。
        int probeLines = Math.min(100_000, Math.max(Math.max(lines * 50, 2_000), minutes * 40));
        String timestampPattern = "^[0-9]{4}-[0-9]{2}-[0-9]{2}[ T][0-9]{2}:[0-9]{2}:[0-9]{2}";
        String awkPattern = "^[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9][ T]"
                + "[0-9][0-9]:[0-9][0-9]:[0-9][0-9]";
        return "start=$(date -d '-" + minutes + " minutes' '+%Y-%m-%d %H:%M:%S') || exit 1; "
                + "if tail -n " + probeLines + " -- " + fileExpression
                + " | grep -Eqm1 " + singleQuote(timestampPattern) + "; then "
                + "tail -n " + probeLines + " -- " + fileExpression
                + " | awk -v start=\"$start\" " + singleQuote(
                        "$0 ~ /" + awkPattern + "/ { ts=substr($0,1,19); keep=(ts >= start) } "
                                + "keep { print }")
                + " | tail -n " + lines + "; "
                + "else tail -n " + lines + " -- " + fileExpression + "; fi";
    }

    /**
     * 连通性探测：文件测可读，目录测存在且可读。
     *
     * @param path 路径
     * @return 连通性探测：文件测可读，目录测存在且可读
     */
    public static String buildProbeCommand(String path) {
        String normalized = normalize(path);
        if (looksLikeDirectory(normalized)) {
            return "test -d " + normalized + " && test -r " + normalized;
        }
        return "test -r " + normalized;
    }

    /**
     * 将字符串包成 POSIX 单引号字面量，内部单引号按 {@code '\''} 方式转义。
     *
     * @param value 原始字符串
     * @return 可安全嵌入 shell 的单引号字面量
     */
    private static String singleQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    /**
     * 供提示文案使用的路径类型说明。
     *
     * @param allowedRaw allowedRaw
     * @return 供提示文案使用的路径类型说明
     */
    public static String describeAllowed(String allowedRaw) {
        return parseAllowed(allowedRaw).stream()
                .map(item -> item + (looksLikeDirectory(item) ? "（目录）" : "（文件）"))
                .collect(Collectors.joining(", "));
    }
}
