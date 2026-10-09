package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建或更新受管工具的请求参数。
 * <p>
 * 对应 {@code ToolController} 的 {@code POST /api/tools} 与 {@code PUT /api/tools/{id}}，
 * 定义 Agent 可调用的 SSH/Git/日志等工具及其风险与超时策略。
 * </p>
 * @author yangdb
 */
public class SaveToolRequest {
    /** 工具编码，大写字母开头，仅含大写字母、数字与下划线。 */
    @NotBlank @Pattern(regexp="^[A-Z][A-Z0-9_]{1,63}$") private String code;
    /** 工具展示名称，必填，最长 128。 */
    @NotBlank @Size(max=128) private String name;
    /** 工具类型：SSH、GIT、LOG、CICD、HTTP、SCRIPT。 */
    @NotBlank @Pattern(regexp="^(SSH|GIT|LOG|CICD|HTTP|SCRIPT)$") private String type;
    /** 工具说明，最长 500。 */
    @Size(max=500) private String description;
    /** 命令/请求模板，最长 1000。 */
    @Size(max=1000) private String commandTemplate;
    /** 风险等级：LOW、MEDIUM、HIGH、CRITICAL。 */
    @NotBlank @Pattern(regexp="^(LOW|MEDIUM|HIGH|CRITICAL)$") private String riskLevel;
    /** 单次执行超时秒数，范围 1～3600。 */
    @Min(1) @Max(3600) private Integer timeoutSeconds;
    /** 是否启用该工具。 */
    private Boolean enabled;
    /**
     * 获取工具编码。
     *
     * @return 编码
     */
    public String getCode(){return code;}
    /**
     * 设置工具编码。
     *
     * @param v 大写编码
     */
    public void setCode(String v){code=v;}
    /**
     * 获取展示名称。
     *
     * @return 名称
     */
    public String getName(){return name;}
    /**
     * 设置展示名称。
     *
     * @param v 名称
     */
    public void setName(String v){name=v;}
    /**
     * 获取工具类型。
     *
     * @return 类型枚举字符串
     */
    public String getType(){return type;}
    /**
     * 设置工具类型。
     *
     * @param v SSH/GIT/LOG/CICD/HTTP/SCRIPT
     */
    public void setType(String v){type=v;}
    /**
     * 获取工具说明。
     *
     * @return 描述
     */
    public String getDescription(){return description;}
    /**
     * 设置工具说明。
     *
     * @param v 描述
     */
    public void setDescription(String v){description=v;}
    /**
     * 获取命令模板。
     *
     * @return 命令模板
     */
    public String getCommandTemplate(){return commandTemplate;}
    /**
     * 设置命令模板。
     *
     * @param v 命令模板
     */
    public void setCommandTemplate(String v){commandTemplate=v;}
    /**
     * 获取风险等级。
     *
     * @return 风险等级
     */
    public String getRiskLevel(){return riskLevel;}
    /**
     * 设置风险等级。
     *
     * @param v LOW/MEDIUM/HIGH/CRITICAL
     */
    public void setRiskLevel(String v){riskLevel=v;}
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
