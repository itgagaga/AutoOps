package com.zhiwei.superemployee.app.config;

import com.zhiwei.superemployee.core.workflow.InMemoryIncidentWorkflow;
import com.zhiwei.superemployee.core.workflow.IncidentWorkflow;
import com.zhiwei.superemployee.core.gateway.IncidentModelGateway;
import com.zhiwei.superemployee.core.gateway.IncidentToolGateway;
import com.zhiwei.superemployee.core.gateway.InMemoryIncidentModelGateway;
import com.zhiwei.superemployee.core.gateway.InMemoryIncidentToolGateway;
import com.zhiwei.superemployee.core.repository.InMemoryIncidentRepository;
import com.zhiwei.superemployee.core.repository.IncidentRepository;
import com.zhiwei.superemployee.app.runtime.AgentRuntimeService;
import com.zhiwei.superemployee.app.runtime.ReactIncidentWorkflow;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * 事件（Incident）工作流相关 Bean 配置。
 * <p>
 * 在缺少外部实现时提供内存版仓储、模型网关与工具网关；
 * 并根据 {@code super-employee.react.enabled} 在内存工作流与 ReAct 工作流之间切换。
 * </p>
 * @author yangdb
 */
@Configuration
public class WorkflowConfig {

    /**
     * 当容器中尚无 {@link IncidentRepository} 时，注册内存事件仓储。
     *
     * @return 内存实现的事件仓储
     */
    @Bean
    @ConditionalOnMissingBean(IncidentRepository.class)
    public IncidentRepository incidentRepository() {
        return new InMemoryIncidentRepository();
    }

    /**
     * 当容器中尚无 {@link IncidentModelGateway} 时，注册内存模型网关。
     *
     * @return 内存实现的事件模型网关
     */
    @Bean
    @ConditionalOnMissingBean(IncidentModelGateway.class)
    public IncidentModelGateway incidentModelGateway() {
        return new InMemoryIncidentModelGateway();
    }

    /**
     * 当容器中尚无 {@link IncidentToolGateway} 时，注册内存工具网关。
     *
     * @return 内存实现的事件工具网关
     */
    @Bean
    @ConditionalOnMissingBean(IncidentToolGateway.class)
    public IncidentToolGateway incidentToolGateway() {
        return new InMemoryIncidentToolGateway();
    }

    /**
     * 在 ReAct 未启用（默认）时注册内存事件工作流。
     * <p>条件：{@code super-employee.react.enabled=false}，或未配置该属性。</p>
     *
     * @param incidentRepository    事件仓储
     * @param incidentModelGateway  模型网关
     * @param incidentToolGateway   工具网关
     * @return 内存实现的事件工作流
     */
    @Bean
    @ConditionalOnProperty(prefix = "super-employee.react", name = "enabled", havingValue = "false", matchIfMissing = true)
    public IncidentWorkflow incidentWorkflow(IncidentRepository incidentRepository,
                                             IncidentModelGateway incidentModelGateway,
                                             IncidentToolGateway incidentToolGateway) {
        return new InMemoryIncidentWorkflow(incidentRepository, incidentModelGateway, incidentToolGateway);
    }

    /**
     * 在 ReAct 启用时注册基于 Agent 运行时的事件工作流。
     * <p>条件：{@code super-employee.react.enabled=true}。</p>
     *
     * @param incidentRepository  事件仓储
     * @param agentRuntimeService Agent 运行时服务
     * @return ReAct 事件工作流
     */
    @Bean
    @ConditionalOnProperty(prefix = "super-employee.react", name = "enabled", havingValue = "true")
    public IncidentWorkflow reactIncidentWorkflow(IncidentRepository incidentRepository,
                                                   AgentRuntimeService agentRuntimeService) {
        return new ReactIncidentWorkflow(incidentRepository, agentRuntimeService);
    }
}
