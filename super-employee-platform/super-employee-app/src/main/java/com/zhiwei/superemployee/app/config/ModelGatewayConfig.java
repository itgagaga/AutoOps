package com.zhiwei.superemployee.app.config;

import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.IncidentModelGateway;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.dashscope.DashScopeChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.UUID;

/**
 * AgentScope / DashScope 模型网关配置。
 * <p>
 * 注册聊天模型 Bean；在 {@code super-employee.model.enabled=true} 时，
 * 额外注册基于 AgentScope 的 {@link IncidentModelGateway}，用于事件根因分析与修复方案起草。
 * </p>
 * @author yangdb
 */
@Configuration
public class ModelGatewayConfig {

    /**
     * 构建 DashScope 聊天模型。
     *
     * @param apiKey    DashScope API Key（{@code agentscope.dashscope.api-key}）
     * @param modelName 模型名称，默认 {@code qwen-plus}
     * @param stream    是否流式输出，默认 {@code true}
     * @return AgentScope 可用的 {@link Model} 实例
     */
    @Bean
    public Model agentScopeModel(
            @Value("${agentscope.dashscope.api-key}") String apiKey,
            @Value("${agentscope.dashscope.model-name:qwen-plus}") String modelName,
            @Value("${agentscope.dashscope.stream:true}") boolean stream) {
        return DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .stream(stream)
                .build();
    }

    /**
     * 在模型能力开启时，注册基于 AgentScope 的事件模型网关。
     * <p>条件：{@code super-employee.model.enabled=true}。</p>
     *
     * @param model 已配置的 AgentScope 模型
     * @return 事件模型网关实现
     */
    @Bean
    @ConditionalOnProperty(prefix = "super-employee.model", name = "enabled", havingValue = "true")
    public IncidentModelGateway agentScopeIncidentModelGateway(Model model) {
        return new AgentScopeIncidentModelGateway(model);
    }

    /**
     * 基于 AgentScope ReActAgent 的事件模型网关实现。
     * <p>通过结构化输出完成根因分析与补丁方案起草。</p>
     */
    static final class AgentScopeIncidentModelGateway implements IncidentModelGateway {
        /** 用于运维分析的 ReAct Agent。 */
        private final ReActAgent agent;

        /**
         * 使用给定模型构建内部 ReAct Agent。
         *
         * @param model 聊天模型
         */
        AgentScopeIncidentModelGateway(Model model) {
            this.agent = ReActAgent.builder()
                    .name("incident-model-gateway")
                    .sysPrompt("你是一名严谨的服务器运维分析专家。请仅返回基于事实的结构化结果。")
                    .model(model)
                    .build();
        }

        /**
         * 根据事件任务调用模型，输出根因、证据与摘要。
         *
         * @param task 待分析的事件任务
         * @return 分析结果
         * @throws IllegalStateException 当模型无响应或未返回结构化数据时抛出
         */
        @Override
        public AnalysisResult analyze(IncidentTask task) {
            String prompt = String.format(
                    "请根据提供的证据分析事件根因。标题：%s。描述：%s。上下文：%s",
                    task.getTitle(), task.getDescription(), task.getContext());
            AnalysisOutput output = structured(prompt, AnalysisOutput.class);
            return new AnalysisResult(value(output.rootCause), value(output.evidence), value(output.summary));
        }

        /**
         * 根据事件与分析结果起草安全修复方案。
         *
         * @param task           事件任务
         * @param analysisResult 已有分析结果
         * @return 补丁/修复计划
         * @throws IllegalStateException 当模型无响应或未返回结构化数据时抛出
         */
        @Override
        public PatchPlan draftPatch(IncidentTask task, AnalysisResult analysisResult) {
            String prompt = String.format(
                    "请制定安全的运维修复方案。事件：%s。根因：%s。证据：%s",
                    task.getTitle(), analysisResult.getRootCause(), analysisResult.getEvidence());
            PatchOutput output = structured(prompt, PatchOutput.class);
            return new PatchPlan(value(output.title), value(output.diffPreview), value(output.summary));
        }

        /**
         * 以用户消息调用 Agent，并解析为指定结构化类型。
         *
         * @param prompt     提示词
         * @param outputType 期望的结构化输出类型
         * @param <T>        输出类型参数
         * @return 解析后的结构化对象
         * @throws IllegalStateException 当响应为空或结构化数据为空时抛出
         */
        private <T> T structured(String prompt, Class<T> outputType) {
            RuntimeContext context = RuntimeContext.builder()
                    .sessionId("model-gateway-" + UUID.randomUUID())
                    .build();
            Msg response = agent.call(List.of(new UserMessage(prompt)), outputType, context).block();
            if (response == null) {
                throw new IllegalStateException("AgentScope 模型未返回响应");
            }
            T output = response.getStructuredData(outputType);
            if (output == null) {
                throw new IllegalStateException("AgentScope 模型未返回结构化数据");
            }
            return output;
        }

        /**
         * 将空字符串字段规范为空串，避免下游 NPE。
         *
         * @param value 原始字符串，可为 {@code null}
         * @return 非 null 字符串（null 时返回空串）
         */
        private String value(String value) {
            return value == null ? "" : value;
        }
    }

    /**
     * 模型根因分析结构化输出 DTO。
     */
    public static final class AnalysisOutput {
        /** 根因说明。 */
        public String rootCause;
        /** 支撑证据。 */
        public String evidence;
        /** 分析摘要。 */
        public String summary;
    }

    /**
     * 模型修复方案结构化输出 DTO。
     */
    public static final class PatchOutput {
        /** 方案标题。 */
        public String title;
        /** 变更/差异预览。 */
        public String diffPreview;
        /** 方案摘要。 */
        public String summary;
    }
}
