package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.core.domain.IncidentTask;

import java.util.Locale;
import java.util.Map;

/**
 * 本地修复引擎展示名统一入口，避免各处硬编码 “CodeX CLI”。
 * @author yangdb
 */
public final class RepairEngineLabels {
    private RepairEngineLabels() {
    }

    /**
     * 规范化引擎标识；空白视为 {@code auto}。
     */
    public static String normalize(String engine) {
        if (engine == null || engine.isBlank()) {
            return RoutingLocalRepairGateway.ENGINE_AUTO;
        }
        String value = engine.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case RoutingLocalRepairGateway.ENGINE_AUTO,
                    RoutingLocalRepairGateway.ENGINE_CODEX,
                    RoutingLocalRepairGateway.ENGINE_CURSOR,
                    RoutingLocalRepairGateway.ENGINE_CLAUDE -> value;
            default -> value;
        };
    }

    /**
     * CLI 全称：CodeX CLI / Cursor CLI / Claude Code CLI；auto 为「本地修复引擎（自动切换）」。
     */
    public static String cliName(String engine) {
        return switch (normalize(engine)) {
            case RoutingLocalRepairGateway.ENGINE_CURSOR -> "Cursor CLI";
            case RoutingLocalRepairGateway.ENGINE_CLAUDE -> "Claude Code CLI";
            case RoutingLocalRepairGateway.ENGINE_CODEX -> "CodeX CLI";
            case RoutingLocalRepairGateway.ENGINE_AUTO -> "本地修复引擎（自动切换）";
            default -> "本地修复引擎";
        };
    }

    /**
     * 短名称：CodeX / Cursor / Claude Code；auto 为「本地修复」。
     */
    public static String shortName(String engine) {
        return switch (normalize(engine)) {
            case RoutingLocalRepairGateway.ENGINE_CURSOR -> "Cursor";
            case RoutingLocalRepairGateway.ENGINE_CLAUDE -> "Claude Code";
            case RoutingLocalRepairGateway.ENGINE_CODEX -> "CodeX";
            case RoutingLocalRepairGateway.ENGINE_AUTO -> "本地修复";
            default -> engine == null || engine.isBlank() ? "本地修复" : engine.trim();
        };
    }

    /**
     * 时间线步骤标题，如「CodeX 修复」「本地修复」。
     */
    public static String repairStepTitle(String engine) {
        String id = normalize(engine);
        if (RoutingLocalRepairGateway.ENGINE_AUTO.equals(id)) {
            return "本地修复";
        }
        return shortName(id) + " 修复";
    }

    /**
     * 从故障上下文解析实际/偏好引擎。
     */
    public static String fromTask(IncidentTask task) {
        if (task == null || task.getContext() == null) {
            return RoutingLocalRepairGateway.ENGINE_AUTO;
        }
        Map<String, Object> context = task.getContext();
        Object engine = context.get("repairEngine");
        if (engine == null || String.valueOf(engine).isBlank()) {
            engine = context.get("repairEnginePreference");
        }
        return engine == null ? RoutingLocalRepairGateway.ENGINE_AUTO : String.valueOf(engine);
    }

    /**
     * 故障任务对应的 CLI 展示名。
     */
    public static String cliName(IncidentTask task) {
        return cliName(fromTask(task));
    }

    /**
     * 故障任务对应的短名称。
     */
    public static String shortName(IncidentTask task) {
        return shortName(fromTask(task));
    }
}
