package com.zhiwei.superemployee.core.gateway;

import com.zhiwei.superemployee.core.domain.IncidentTask;

/**
 * 故障处置模型网关：对任务上下文做根因分析并拟定补丁方案。
 * <p>
 * 与 {@link IncidentToolGateway} 协作——本接口产出 {@link AnalysisResult}/{@link PatchPlan}，
 * 工具侧负责预览、验证与部署。默认内存实现见 {@link InMemoryIncidentModelGateway}。
 * @author yangdb
 */
public interface IncidentModelGateway {

    /**
     * 对事件上下文进行根因分析。
     *
     * @param task 含上下文（如日志范围）的故障任务
     * @return 根因、证据与摘要
     */
    AnalysisResult analyze(IncidentTask task);

    /**
     * 根据根因分析结果拟定补丁方案。
     *
     * @param task           故障任务
     * @param analysisResult 上游分析结果
     * @return 含标题与 diff 预览的补丁方案
     */
    PatchPlan draftPatch(IncidentTask task, AnalysisResult analysisResult);

    /**
     * 模型根因分析的结构化结果。
     */
    final class AnalysisResult {
        /** 判定的根因标签或简述。 */
        private final String rootCause;
        /** 支撑结论的证据说明。 */
        private final String evidence;
        /** 面向时间线的人类可读摘要。 */
        private final String summary;

        /**
         * @param rootCause 根因
         * @param evidence  证据
         * @param summary   摘要
         */
        public AnalysisResult(String rootCause, String evidence, String summary) {
            this.rootCause = rootCause;
            this.evidence = evidence;
            this.summary = summary;
        }

        /**
         * @return 根因
         */
        public String getRootCause() {
            return rootCause;
        }

        /**
         * @return 证据说明
         */
        public String getEvidence() {
            return evidence;
        }

        /**
         * @return 摘要
         */
        public String getSummary() {
            return summary;
        }
    }

    /**
     * 模型拟定的补丁方案（含可审阅的 diff 预览）。
     */
    final class PatchPlan {
        /** 补丁标题或标识性短名。 */
        private final String title;
        /** 统一 diff 或等价预览文本。 */
        private final String diffPreview;
        /** 方案摘要。 */
        private final String summary;

        /**
         * @param title       补丁标题
         * @param diffPreview diff 预览
         * @param summary     摘要
         */
        public PatchPlan(String title, String diffPreview, String summary) {
            this.title = title;
            this.diffPreview = diffPreview;
            this.summary = summary;
        }

        /**
         * @return 补丁标题
         */
        public String getTitle() {
            return title;
        }

        /**
         * @return diff 预览文本
         */
        public String getDiffPreview() {
            return diffPreview;
        }

        /**
         * @return 方案摘要
         */
        public String getSummary() {
            return summary;
        }
    }
}
