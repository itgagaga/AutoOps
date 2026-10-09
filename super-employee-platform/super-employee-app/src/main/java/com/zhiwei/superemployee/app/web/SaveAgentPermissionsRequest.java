package com.zhiwei.superemployee.app.web;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import java.util.*;

/**
 * 全量保存 Agent 工具授权、资源授权与审批策略的请求参数。
 * <p>
 * 对应 {@code AgentPermissionController} 的 {@code PUT /api/agent-permissions/{agentId}}，
 * 一次性替换该 Agent 的权限配置。
 * </p>
 * @author yangdb
 */
public class SaveAgentPermissionsRequest {
    /** 工具授权列表，不可为 {@code null}；每项须通过嵌套校验。 */
    @Valid @NotNull private List<ToolGrant> tools=new ArrayList<>();
    /** 资源授权列表，不可为 {@code null}；每项须通过嵌套校验。 */
    @Valid @NotNull private List<ResourceGrant> resources=new ArrayList<>();
    /** 审批与自动放行策略，不可为 {@code null}。 */
    @Valid @NotNull private Policy policy=new Policy();
    /**
     * 获取工具授权列表。
     *
     * @return 工具授权
     */
    public List<ToolGrant> getTools(){return tools;}
    /**
     * 设置工具授权列表。
     *
     * @param v 工具授权
     */
    public void setTools(List<ToolGrant> v){tools=v;}
    /**
     * 获取资源授权列表。
     *
     * @return 资源授权
     */
    public List<ResourceGrant> getResources(){return resources;}
    /**
     * 设置资源授权列表。
     *
     * @param v 资源授权
     */
    public void setResources(List<ResourceGrant> v){resources=v;}
    /**
     * 获取审批策略。
     *
     * @return 策略对象
     */
    public Policy getPolicy(){return policy;}
    /**
     * 设置审批策略。
     *
     * @param v 策略对象
     */
    public void setPolicy(Policy v){policy=v;}

    /**
     * 单条工具授权项。
     */
    public static class ToolGrant {
        /** 受管工具 id，必填。 */
        @NotBlank private String toolId;
        /** 覆盖默认超时秒数，范围 1～3600；为空则沿用工具自身超时。 */
        @Min(1) @Max(3600) private Integer timeoutSeconds;
        /** 每日调用上限，范围 1～100000；为空时服务端默认 100。 */
        @Min(1) @Max(100000) private Integer dailyLimit;
        /** 是否启用该工具授权。 */
        private Boolean enabled;
        /**
         * 获取工具 id。
         *
         * @return 工具 id
         */
        public String getToolId(){return toolId;}
        /**
         * 设置工具 id。
         *
         * @param v 工具 id
         */
        public void setToolId(String v){toolId=v;}
        /**
         * 获取超时秒数。
         *
         * @return 超时秒数
         */
        public Integer getTimeoutSeconds(){return timeoutSeconds;}
        /**
         * 设置超时秒数。
         *
         * @param v 1～3600
         */
        public void setTimeoutSeconds(Integer v){timeoutSeconds=v;}
        /**
         * 获取每日调用上限。
         *
         * @return 日限额
         */
        public Integer getDailyLimit(){return dailyLimit;}
        /**
         * 设置每日调用上限。
         *
         * @param v 1～100000
         */
        public void setDailyLimit(Integer v){dailyLimit=v;}
        /**
         * 获取启用状态。
         *
         * @return 是否启用
         */
        public Boolean getEnabled(){return enabled;}
        /**
         * 设置启用状态。
         *
         * @param v 是否启用
         */
        public void setEnabled(Boolean v){enabled=v;}
    }

    /**
     * 单条资源授权项。
     */
    public static class ResourceGrant {
        /** 资源类型：HOST、APPLICATION、DATABASE。 */
        @NotBlank @Pattern(regexp="^(HOST|APPLICATION|DATABASE)$") private String resourceType;
        /** 资源 id，必填。 */
        @NotBlank private String resourceId;
        /** 访问级别：READ、EXECUTE、ADMIN。 */
        @NotBlank @Pattern(regexp="^(READ|EXECUTE|ADMIN)$") private String accessLevel;
        /** 是否启用该资源授权。 */
        private Boolean enabled;
        /**
         * 获取资源类型。
         *
         * @return 资源类型
         */
        public String getResourceType(){return resourceType;}
        /**
         * 设置资源类型。
         *
         * @param v HOST/APPLICATION/DATABASE
         */
        public void setResourceType(String v){resourceType=v;}
        /**
         * 获取资源 id。
         *
         * @return 资源 id
         */
        public String getResourceId(){return resourceId;}
        /**
         * 设置资源 id。
         *
         * @param v 资源 id
         */
        public void setResourceId(String v){resourceId=v;}
        /**
         * 获取访问级别。
         *
         * @return 访问级别
         */
        public String getAccessLevel(){return accessLevel;}
        /**
         * 设置访问级别。
         *
         * @param v READ/EXECUTE/ADMIN
         */
        public void setAccessLevel(String v){accessLevel=v;}
        /**
         * 获取启用状态。
         *
         * @return 是否启用
         */
        public Boolean getEnabled(){return enabled;}
        /**
         * 设置启用状态。
         *
         * @param v 是否启用
         */
        public void setEnabled(Boolean v){enabled=v;}
    }

    /**
     * Agent 审批与自动放行策略。
     */
    public static class Policy {
        /** 可自动放行的最高风险等级：NONE、LOW、MEDIUM；默认 LOW。 */
        @NotBlank @Pattern(regexp="^(NONE|LOW|MEDIUM)$") private String autoApproveMaxRisk="LOW";
        /** 生产环境操作是否必须人工审批；默认 {@code true}。 */
        private Boolean productionRequiresApproval=true;
        /** 是否允许 Agent 访问凭据；默认 {@code false}。 */
        private Boolean credentialAccessEnabled=false;
        /**
         * 获取自动放行最高风险等级。
         *
         * @return NONE/LOW/MEDIUM
         */
        public String getAutoApproveMaxRisk(){return autoApproveMaxRisk;}
        /**
         * 设置自动放行最高风险等级。
         *
         * @param v NONE/LOW/MEDIUM
         */
        public void setAutoApproveMaxRisk(String v){autoApproveMaxRisk=v;}
        /**
         * 获取生产环境是否必须审批。
         *
         * @return 是否必须审批
         */
        public Boolean getProductionRequiresApproval(){return productionRequiresApproval;}
        /**
         * 设置生产环境是否必须审批。
         *
         * @param v 是否必须审批
         */
        public void setProductionRequiresApproval(Boolean v){productionRequiresApproval=v;}
        /**
         * 获取是否允许访问凭据。
         *
         * @return 是否允许
         */
        public Boolean getCredentialAccessEnabled(){return credentialAccessEnabled;}
        /**
         * 设置是否允许访问凭据。
         *
         * @param v 是否允许
         */
        public void setCredentialAccessEnabled(Boolean v){credentialAccessEnabled=v;}
    }
}
