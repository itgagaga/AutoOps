package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.agent.api.AgentCodes;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 协调智能体可 spawn 的 Harness 子智能体 ID 与管理台 {@code managed_agent.code} 的对照表。
 * <p>协调器通过 {@code agent_spawn(agent_id=...)} 识别并调用个体智能体；管理页状态依赖
 * {@code agent_session.agent_code} 统计，因此两侧命名必须可映射。</p>
 */
public final class CollaborativeAgentRegistry {

    /** 日志/证据提取 */
    public static final String LOG_EXTRACTION = AgentCodes.LOG_EXTRACTION;
    /** 根因分析 */
    public static final String ROOT_CAUSE_ANALYSIS = AgentCodes.ROOT_CAUSE_ANALYSIS;
    /** 修复规划（非直接改代码；落地改码走 CODEX_LOCAL_REPAIR 工具） */
    public static final String CODE_REPAIR = AgentCodes.CODE_REPAIR;
    /** 验证方案 */
    public static final String TEST_VERIFICATION = AgentCodes.TEST_VERIFICATION;
    /** 运维协调 */
    public static final String OPS_REACT = AgentCodes.OPS_REACT;

    private static final Map<String, String> HARNESS_TO_MANAGED = new LinkedHashMap<>();

    static {
        alias(LOG_EXTRACTION, "evidence-collector", "log-extraction");
        alias(ROOT_CAUSE_ANALYSIS, "root-cause-analyst", "root-cause-analysis");
        alias(CODE_REPAIR, "repair-planner", "code-repair");
        alias(TEST_VERIFICATION, "verification-specialist", "test-verification");
        alias(OPS_REACT, "ops-coordinator");
    }

    private CollaborativeAgentRegistry() {
    }

    private static void alias(String managedCode, String... harnessIds) {
        HARNESS_TO_MANAGED.put(normalize(managedCode), managedCode);
        for (String id : harnessIds) {
            HARNESS_TO_MANAGED.put(normalize(id), managedCode);
        }
    }

    /**
     * 将 Harness {@code agent_id} 解析为管理台 Agent 编码。
     *
     * @param harnessAgentId {@code agent_spawn} 的 agent_id
     * @return 管理台 code；无法识别时 empty
     */
    public static Optional<String> toManagedCode(String harnessAgentId) {
        if (harnessAgentId == null || harnessAgentId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(HARNESS_TO_MANAGED.get(normalize(harnessAgentId)));
    }

    /**
     * 协调器应向模型暴露的标准个体 Agent ID（与管理台 code 一致）。
     */
    public static Map<String, String> specialistCatalog() {
        Map<String, String> catalog = new LinkedHashMap<>();
        catalog.put(LOG_EXTRACTION, "从事件事实与本地/远程日志中提取并核验故障证据");
        catalog.put(ROOT_CAUSE_ANALYSIS, "基于证据给出按可能性排序的根因假设");
        catalog.put(CODE_REPAIR, "设计影响最小、可回滚的修复方案与补丁建议");
        catalog.put(TEST_VERIFICATION, "审查修复建议并制定验证通过标准");
        return catalog;
    }

    static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
