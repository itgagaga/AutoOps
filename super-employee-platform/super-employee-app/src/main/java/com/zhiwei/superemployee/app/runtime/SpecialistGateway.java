package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.agent.api.SpecialistTaskRequest;
import com.zhiwei.superemployee.agent.api.SpecialistTaskResult;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 通过 HTTP 调用独立部署的个体智能体。
 */
@Component
@ConditionalOnProperty(prefix = "super-employee.react", name = "enabled", havingValue = "true")
public class SpecialistGateway {
    private static final Logger log = LoggerFactory.getLogger(SpecialistGateway.class);

    private final SpecialistCatalogService catalog;
    private final RestTemplate restTemplate = new RestTemplate();
    private final String sharedToken;
    private final long timeoutHintSeconds;
    private final Optional<LocalSpecialistRuntime> localRuntime;

    public SpecialistGateway(SpecialistCatalogService catalog,
                             Optional<LocalSpecialistRuntime> localRuntime,
                             @Value("${super-employee.agents.shared-token}") String sharedToken,
                             @Value("${super-employee.agents.invoke-timeout-seconds:120}") long timeoutHintSeconds) {
        this.catalog = catalog;
        this.localRuntime = localRuntime;
        this.sharedToken = sharedToken;
        this.timeoutHintSeconds = timeoutHintSeconds;
    }

    public SpecialistTaskResult invoke(String agentCode, IncidentTask task, String objective) {
        SpecialistDescriptor specialist = catalog.findOnline(agentCode)
                .orElseThrow(() -> new IllegalStateException(
                        "个体智能体不可用或心跳离线：" + agentCode));
        SpecialistTaskRequest request = new SpecialistTaskRequest();
        request.setTaskId(UUID.randomUUID().toString());
        request.setIncidentId(task.getId());
        request.setAgentCode(specialist.code());
        request.setObjective(objective);
        request.setTraceId(task.getId() + ":" + specialist.code() + ":" + request.getTaskId());
        Map<String, Object> context = new LinkedHashMap<>(task.getContext());
        context.put("title", task.getTitle());
        context.put("description", task.getDescription());
        context.put("latestSummary", task.getLatestSummary());
        request.setContext(context);

        if (localRuntime.map(runtime -> runtime.supports(specialist.code())).orElse(false)) {
            log.info("调度本地内置 Agent code={} taskId={}", specialist.code(), request.getTaskId());
            return localRuntime.orElseThrow().invoke(specialist.code(), request);
        }

        String url = specialist.endpointUrl().replaceAll("/$", "") + "/v1/tasks";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Agent-Token", sharedToken);
        headers.setBearerAuth(sharedToken);
        try {
            log.info("调度个体智能体 code={} url={} taskId={} timeoutHint={}s",
                    specialist.code(), url, request.getTaskId(), timeoutHintSeconds);
            ResponseEntity<SpecialistTaskResult> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(request, headers), SpecialistTaskResult.class);
            SpecialistTaskResult body = response.getBody();
            if (body == null) {
                return SpecialistTaskResult.failed(request.getTaskId(), specialist.code(), "空响应");
            }
            return body;
        }
        catch (RestClientException ex) {
            log.warn("调用个体智能体失败 code={}：{}", specialist.code(), ex.getMessage());
            return SpecialistTaskResult.failed(request.getTaskId(), specialist.code(), ex.getMessage());
        }
    }
}
