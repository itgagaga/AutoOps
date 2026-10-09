package com.zhiwei.superemployee.agent.starter;

import com.zhiwei.superemployee.agent.api.AgentHeartbeatPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 定时向平台上报心跳。
 */
@Component
@ConditionalOnProperty(prefix = "zhiwei.agent", name = "heartbeat-enabled", havingValue = "true", matchIfMissing = true)
public class HeartbeatReporter {
    private static final Logger log = LoggerFactory.getLogger(HeartbeatReporter.class);

    private final SpecialistAgentProperties properties;
    private final SpecialistRuntimeStats stats;
    private final RestTemplate restTemplate = new RestTemplate();

    public HeartbeatReporter(SpecialistAgentProperties properties, SpecialistRuntimeStats stats) {
        this.properties = properties;
        this.stats = stats;
    }

    @Scheduled(fixedDelayString = "${zhiwei.agent.heartbeat-interval-ms:15000}")
    public void report() {
        if (properties.getId() == null || properties.getId().isBlank()) {
            log.warn("未配置 zhiwei.agent.id，跳过心跳上报");
            return;
        }
        String base = properties.getPlatformBaseUrl() == null ? "" : properties.getPlatformBaseUrl().replaceAll("/$", "");
        String url = base + "/api/agents/" + properties.getId() + "/heartbeat";
        AgentHeartbeatPayload payload = AgentHeartbeatPayload.of(
                stats.runtimeStatus(), stats.totalTasks(), stats.successTasks(), stats.failedTasks());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Agent-Token", properties.getSharedToken());
        try {
            restTemplate.postForEntity(url, new HttpEntity<>(payload, headers), MapResponse.class);
            log.debug("心跳已上报 agentId={} status={}", properties.getId(), payload.getRuntimeStatus());
        }
        catch (RestClientException ex) {
            log.warn("心跳上报失败 agentId={} url={}：{}", properties.getId(), url, ex.getMessage());
        }
    }

    /** 仅用于反序列化忽略正文。 */
    private static final class MapResponse {
    }
}
