package com.zhiwei.superemployee.app.runtime;

import java.io.IOException;
import java.util.List;

/**
 * 本地代码修复网关：在已批准的 Maven 工作区内调用 CLI（CodeX / Cursor 等）完成最小修复，
 * 并支持基于 Git 快照的接受/撤销审阅。
 * @author yangdb
 */
public interface LocalRepairGateway {

    /** 引擎标识，如 {@code codex}、{@code cursor}。 */
    String engineId();

    /** 面向用户的引擎显示名。 */
    default String engineDisplayName() {
        return engineId();
    }

    /**
     * 执行本地修复；优先使用运维任务绑定的工作区覆盖路径。
     *
     * @param approvedPrompt    已批准的修复提示
     * @param allowedFiles      允许修改的相对路径；空表示整个工作区
     * @param workspaceOverride 任务级工作区路径，可为空
     * @return 修复执行结果
     */
    Result repair(String approvedPrompt, List<String> allowedFiles, String workspaceOverride) throws IOException;

    /**
     * 审阅并接受或撤销一次修复快照。
     *
     * @param workspacePath 任务工作区路径
     * @param repairId      修复快照 UUID
     * @param accept        {@code true} 接受保留变更；{@code false} 回滚
     * @return 审阅结果
     */
    ReviewResult review(String workspacePath, String repairId, boolean accept) throws IOException;

    /**
     * 本地修复执行结果。
     *
     * @param success      进程成功且变更在批准范围内
     * @param output       CLI 合并输出（可能截断）
     * @param diff         修复前后文本对比
     * @param changedFiles 变更相对路径列表
     * @param withinScope  变更是否均在允许范围内
     * @param repairId     快照 ID；启动失败时可能为 {@code null}
     */
    record Result(boolean success, String output, String diff,
                  List<String> changedFiles, boolean withinScope, String repairId) {
        /**
         * 根据输出内容生成面向用户的失败摘要。
         */
        public String failureMessage() {
            return failureMessage("本地修复引擎");
        }

        /**
         * 带引擎名的失败摘要。
         */
        public String failureMessage(String engineName) {
            String detail = output == null ? "" : output.trim();
            String lower = detail.toLowerCase();
            String label = engineName == null || engineName.isBlank() ? "本地修复引擎" : engineName;
            String reason;
            if (lower.contains("usage limit") || lower.contains("usage_limit_exceeded")
                    || lower.contains("rate limit") || lower.contains("quota")
                    || detail.contains("额度已耗尽")) {
                reason = label + " 使用额度已耗尽，请补充额度或等待额度恢复后重试";
            }
            else if (detail.contains("执行超时") || lower.contains("timed out") || lower.contains("timeout")) {
                reason = label + " 执行超时";
            }
            else if (detail.contains("无法启动") || detail.contains("另一个程序正在使用")
                    || lower.contains("cannot access") || lower.contains("being used by another")) {
                reason = label + " 启动或清理临时文件失败";
            }
            else if (!withinScope) {
                reason = label + " 修改超出批准范围";
            }
            else if (detail.isBlank()) {
                reason = label + " 执行失败且未返回错误详情";
            }
            else {
                reason = label + " 执行失败";
            }
            if (detail.isBlank()) {
                return reason;
            }
            int limit = Math.min(detail.length(), 4000);
            return reason + "：\n" + detail.substring(0, limit)
                    + (detail.length() > limit ? "\n…错误输出已截断" : "");
        }
    }

    /**
     * 修复审阅（接受/撤销）结果。
     *
     * @param success 本次操作是否成功
     * @param status  决策状态，如 ACCEPTED / REVERTED / PENDING
     * @param message 人类可读说明
     */
    record ReviewResult(boolean success, String status, String message) { }
}
