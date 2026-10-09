package com.zhiwei.superemployee.agent.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 独立 Agent 进程配置。
 */
@ConfigurationProperties(prefix = "zhiwei.agent")
public class SpecialistAgentProperties {
    /** 管理台 Agent 主键，用于心跳上报。 */
    private String id = "";
    /** Agent 业务编码。 */
    private String code = "";
    /** 平台基址，如 http://127.0.0.1:8080 */
    private String platformBaseUrl = "http://127.0.0.1:8080";
    /** 与平台约定的共享令牌。 */
    private String sharedToken;
    /** 心跳间隔毫秒。 */
    private long heartbeatIntervalMs = 15000;
    /** 是否启用向平台上报心跳。 */
    private boolean heartbeatEnabled = true;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getPlatformBaseUrl() {
        return platformBaseUrl;
    }

    public void setPlatformBaseUrl(String platformBaseUrl) {
        this.platformBaseUrl = platformBaseUrl;
    }

    public String getSharedToken() {
        return sharedToken;
    }

    public void setSharedToken(String sharedToken) {
        this.sharedToken = sharedToken;
    }

    public long getHeartbeatIntervalMs() {
        return heartbeatIntervalMs;
    }

    public void setHeartbeatIntervalMs(long heartbeatIntervalMs) {
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    public boolean isHeartbeatEnabled() {
        return heartbeatEnabled;
    }

    public void setHeartbeatEnabled(boolean heartbeatEnabled) {
        this.heartbeatEnabled = heartbeatEnabled;
    }
}
