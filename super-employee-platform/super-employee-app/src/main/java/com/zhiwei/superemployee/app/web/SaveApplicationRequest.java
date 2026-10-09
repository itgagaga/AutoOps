package com.zhiwei.superemployee.app.web;
import jakarta.validation.constraints.*;

/**
 * 创建或更新应用资源的请求参数。
 * <p>
 * 对应 {@code ResourceController} 的 {@code POST /api/resources/applications}
 * 与 {@code PUT /api/resources/applications/{id}}，登记可被运维任务关联的应用元数据。
 * </p>
 * @author yangdb
 */
public class SaveApplicationRequest {
    /** 应用编码，大写字母开头，仅含大写字母、数字与下划线。 */
    @NotBlank @Pattern(regexp="^[A-Z][A-Z0-9_]{1,63}$") private String code;
    /** 应用展示名称，必填，最长 128。 */
    @NotBlank @Size(max=128) private String name;
    /** 应用技术类型：JAVA、NODE、PYTHON、GO、DOTNET、OTHER。 */
    @NotBlank @Pattern(regexp="^(JAVA|NODE|PYTHON|GO|DOTNET|OTHER)$") private String type;
    /** 代码仓库地址，最长 512。 */
    @Size(max=512) private String repositoryUrl;
    /** 目标主机上的部署路径，最长 512。 */
    @Size(max=512) private String deployPath;
    /** 关联主机资源编号，最长 64。 */
    @Size(max=64) private String hostId;
    /** 应用负责人，最长 128。 */
    @Size(max=128) private String ownerName;
    /** 备注说明，最长 500。 */
    @Size(max=500) private String description;
    /** 是否启用该应用。 */
    private Boolean enabled;
    /**
     * 获取应用编码。
     *
     * @return 编码
     */
    public String getCode(){return code;}
    /**
     * 设置应用编码。
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
     * 获取应用技术类型。
     *
     * @return 类型枚举字符串
     */
    public String getType(){return type;}
    /**
     * 设置应用技术类型。
     *
     * @param v JAVA/NODE/PYTHON/GO/DOTNET/OTHER
     */
    public void setType(String v){type=v;}
    /**
     * 获取代码仓库地址。
     *
     * @return 仓库 URL
     */
    public String getRepositoryUrl(){return repositoryUrl;}
    /**
     * 设置代码仓库地址。
     *
     * @param v 仓库 URL
     */
    public void setRepositoryUrl(String v){repositoryUrl=v;}
    /**
     * 获取部署路径。
     *
     * @return 远端部署路径
     */
    public String getDeployPath(){return deployPath;}
    /**
     * 设置部署路径。
     *
     * @param v 远端部署路径
     */
    public void setDeployPath(String v){deployPath=v;}
    /**
     * 获取关联主机编号。
     *
     * @return 主机 id
     */
    public String getHostId(){return hostId;}
    /**
     * 设置关联主机编号。
     *
     * @param v 主机 id
     */
    public void setHostId(String v){hostId=v;}
    /**
     * 获取负责人。
     *
     * @return 负责人
     */
    public String getOwnerName(){return ownerName;}
    /**
     * 设置负责人。
     *
     * @param v 负责人
     */
    public void setOwnerName(String v){ownerName=v;}
    /**
     * 获取备注说明。
     *
     * @return 描述
     */
    public String getDescription(){return description;}
    /**
     * 设置备注说明。
     *
     * @param v 描述
     */
    public void setDescription(String v){description=v;}
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
