package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.system.SystemSettingRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 按配置路由到 CodeX / Cursor / Claude Code 本地修复网关；支持任务级偏好与 auto 失败切换。
 * @author yangdb
 */
@Service
@Primary
public class RoutingLocalRepairGateway implements LocalRepairGateway {
    public static final String KEY_ENGINE = "local.repair.engine";
    public static final String ENGINE_CODEX = "codex";
    public static final String ENGINE_CURSOR = "cursor";
    public static final String ENGINE_CLAUDE = "claude";
    public static final String ENGINE_AUTO = "auto";
    private static final Set<String> SUPPORTED = Set.of(ENGINE_CODEX, ENGINE_CURSOR, ENGINE_CLAUDE);
    private static final Set<String> PREFERENCES = Set.of(ENGINE_AUTO, ENGINE_CODEX, ENGINE_CURSOR, ENGINE_CLAUDE);
    private static final List<String> FAILOVER_ORDER = List.of(ENGINE_CODEX, ENGINE_CURSOR, ENGINE_CLAUDE);

    private final SystemSettingRepository settings;
    private final String yamlDefault;
    private final ObjectProvider<CodexCliRepairGateway> codex;
    private final ObjectProvider<CursorCliRepairGateway> cursor;
    private final ObjectProvider<ClaudeCodeCliRepairGateway> claude;

    public RoutingLocalRepairGateway(
            SystemSettingRepository settings,
            @Value("${super-employee.repair.engine:codex}") String yamlDefault,
            ObjectProvider<CodexCliRepairGateway> codex,
            ObjectProvider<CursorCliRepairGateway> cursor,
            ObjectProvider<ClaudeCodeCliRepairGateway> claude) {
        this.settings = settings;
        this.yamlDefault = normalizeEngine(yamlDefault);
        this.codex = codex;
        this.cursor = cursor;
        this.claude = claude;
    }

    @Override
    public String engineId() {
        return activeEngineId();
    }

    @Override
    public String engineDisplayName() {
        return active().engineDisplayName();
    }

    @Override
    public Result repair(String approvedPrompt, List<String> allowedFiles, String workspaceOverride) throws IOException {
        return repairWithPreference(approvedPrompt, allowedFiles, workspaceOverride, ENGINE_AUTO).result();
    }

    /**
     * 按任务偏好执行修复：固定引擎直调；{@code auto} 失败可重试时自动切换。
     */
    public PreferencedResult repairWithPreference(String approvedPrompt, List<String> allowedFiles,
                                                  String workspaceOverride, String preference) throws IOException {
        String pref = normalizePreference(preference);
        if (!ENGINE_AUTO.equals(pref)) {
            LocalRepairGateway gateway = resolve(pref);
            if (gateway == null) {
                return PreferencedResult.failed(pref, false,
                        new Result(false, pref + " 引擎未启用或未装配", "", List.of(), true, null),
                        pref + " 不可用");
            }
            Result result = gateway.repair(approvedPrompt, allowedFiles, workspaceOverride);
            if (result.success() || !isCliUnavailable(result)) {
                return new PreferencedResult(result, pref, false, gateway.engineDisplayName() + " 单次尝试");
            }
            PreferencedResult fallback = tryFallbackEngines(pref, gateway.engineDisplayName() + " 不可用",
                    approvedPrompt, allowedFiles, workspaceOverride);
            if (fallback != null) {
                return fallback;
            }
            return new PreferencedResult(result, pref, false,
                    gateway.engineDisplayName() + " 不可用且无备用引擎");
        }

        List<String> candidates = autoCandidates();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("未启用任何本地修复引擎（CodeX / Cursor / Claude Code）");
        }
        return tryCandidates(candidates, approvedPrompt, allowedFiles, workspaceOverride, List.of(), false);
    }

    private PreferencedResult tryFallbackEngines(String failedEngine, String prefix,
                                                 String approvedPrompt, List<String> allowedFiles,
                                                 String workspaceOverride) throws IOException {
        List<String> fallback = autoCandidates().stream()
                .filter(engine -> !engine.equals(failedEngine))
                .toList();
        if (fallback.isEmpty()) {
            return null;
        }
        return tryCandidates(fallback, approvedPrompt, allowedFiles, workspaceOverride,
                List.of(prefix), true);
    }

    private PreferencedResult tryCandidates(List<String> candidates, String approvedPrompt,
                                            List<String> allowedFiles, String workspaceOverride,
                                            List<String> initialLogs, boolean switchedBase) throws IOException {
        List<String> attemptLogs = new ArrayList<>(initialLogs);
        Result last = null;
        String lastEngine = candidates.get(0);
        for (int i = 0; i < candidates.size(); i++) {
            String engine = candidates.get(i);
            LocalRepairGateway gateway = resolve(engine);
            if (gateway == null) {
                continue;
            }
            lastEngine = engine;
            Result result = gateway.repair(approvedPrompt, allowedFiles, workspaceOverride);
            last = result;
            if (result.success()) {
                attemptLogs.add(gateway.engineDisplayName() + " 成功");
                return new PreferencedResult(result, engine, switchedBase || i > 0,
                        String.join(" → ", attemptLogs));
            }
            attemptLogs.add(gateway.engineDisplayName() + " 失败");
            if (!isRetryable(result) || i == candidates.size() - 1) {
                return new PreferencedResult(result, engine, switchedBase || i > 0,
                        String.join(" → ", attemptLogs));
            }
            if (result.repairId() != null && workspaceOverride != null && !workspaceOverride.isBlank()) {
                try {
                    gateway.review(workspaceOverride, result.repairId(), false);
                    attemptLogs.set(attemptLogs.size() - 1,
                            gateway.engineDisplayName() + " 失败已回滚");
                }
                catch (RuntimeException ex) {
                    attemptLogs.set(attemptLogs.size() - 1,
                            gateway.engineDisplayName() + " 失败且回滚异常：" + ex.getMessage());
                    return new PreferencedResult(result, engine, switchedBase || i > 0,
                            String.join(" → ", attemptLogs));
                }
            }
        }
        return PreferencedResult.failed(lastEngine, switchedBase,
                last == null ? new Result(false, "无可用修复引擎", "", List.of(), true, null) : last,
                String.join(" → ", attemptLogs));
    }

    @Override
    public ReviewResult review(String workspacePath, String repairId, boolean accept) throws IOException {
        return active().review(workspacePath, repairId, accept);
    }

    /**
     * 当前生效引擎及可用性视图，供管理页展示。
     */
    public Map<String, Object> status() {
        String active = activeEngineId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("engine", active);
        result.put("yamlDefault", yamlDefault);
        result.put("stored", settings.find(KEY_ENGINE).orElse(null));
        result.put("source", settings.find(KEY_ENGINE).filter(v -> !v.isBlank()).isPresent() ? "DATABASE" : "YAML");
        result.put("codexAvailable", codex.getIfAvailable() != null);
        result.put("cursorAvailable", cursor.getIfAvailable() != null);
        result.put("claudeAvailable", claude.getIfAvailable() != null);
        result.put("activeAvailable", resolve(active) != null);
        result.put("displayName", resolve(active) == null ? active : resolve(active).engineDisplayName());
        return result;
    }

    /**
     * 保存全局默认修复引擎；仅允许 {@code codex} / {@code cursor} / {@code claude}。
     */
    public Map<String, Object> saveEngine(String engine) {
        String normalized = normalizeEngine(engine);
        if (!SUPPORTED.contains(normalized)) {
            throw new IllegalArgumentException("修复引擎仅支持 codex、cursor 或 claude");
        }
        if (resolve(normalized) == null) {
            throw new IllegalStateException(normalized + " 引擎未启用或未装配，请先在配置中开启对应 CLI");
        }
        settings.upsert(KEY_ENGINE, normalized, Instant.now().toString());
        return status();
    }

    /**
     * 带偏好执行结果：实际引擎、是否发生切换、尝试摘要。
     */
    public record PreferencedResult(Result result, String engineId, boolean switched, String attemptsSummary) {
        static PreferencedResult failed(String engineId, boolean switched, Result result, String summary) {
            return new PreferencedResult(result, engineId, switched, summary);
        }
    }

    private List<String> autoCandidates() {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        String primary = activeEngineId();
        if (resolve(primary) != null) {
            ordered.add(primary);
        }
        for (String candidate : FAILOVER_ORDER) {
            if (resolve(candidate) != null) {
                ordered.add(candidate);
            }
        }
        return List.copyOf(ordered);
    }

    static boolean isCliUnavailable(Result result) {
        if (result == null || result.success()) {
            return false;
        }
        String detail = result.output() == null ? "" : result.output();
        String lower = detail.toLowerCase(Locale.ROOT);
        return detail.contains("无法启动") || detail.contains("未找到")
                || lower.contains("not found") || lower.contains("cannot find");
    }

    static boolean isRetryable(Result result) {
        if (result == null || result.success()) {
            return false;
        }
        if (!result.withinScope()) {
            return false;
        }
        String detail = result.output() == null ? "" : result.output().toLowerCase(Locale.ROOT);
        if (detail.contains("usage limit") || detail.contains("usage_limit_exceeded")
                || detail.contains("rate limit") || detail.contains("quota")
                || detail.contains("超时") || detail.contains("无法启动")
                || detail.contains("timeout") || detail.contains("not found")) {
            return true;
        }
        // 进程失败但未越权：允许换引擎重试
        return true;
    }

    private LocalRepairGateway active() {
        String id = activeEngineId();
        LocalRepairGateway gateway = resolve(id);
        if (gateway != null) {
            return gateway;
        }
        for (String candidate : FAILOVER_ORDER) {
            LocalRepairGateway fallback = resolve(candidate);
            if (fallback != null) {
                return fallback;
            }
        }
        throw new IllegalStateException("未启用任何本地修复引擎（CodeX / Cursor / Claude Code）");
    }

    private String activeEngineId() {
        return normalizeEngine(settings.find(KEY_ENGINE).filter(v -> !v.isBlank()).orElse(yamlDefault));
    }

    private LocalRepairGateway resolve(String engine) {
        return switch (engine) {
            case ENGINE_CURSOR -> cursor.getIfAvailable();
            case ENGINE_CLAUDE -> claude.getIfAvailable();
            case ENGINE_CODEX -> codex.getIfAvailable();
            default -> null;
        };
    }

    static String normalizePreference(String value) {
        if (value == null || value.isBlank()) {
            return ENGINE_AUTO;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!PREFERENCES.contains(normalized)) {
            throw new IllegalArgumentException("修复引擎偏好仅支持 auto、codex、cursor 或 claude");
        }
        return normalized;
    }

    private static String normalizeEngine(String value) {
        if (value == null || value.isBlank()) {
            return ENGINE_CODEX;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return SUPPORTED.contains(normalized) ? normalized : ENGINE_CODEX;
    }
}
