package com.zhiwei.superemployee.app.web;
import jakarta.validation.constraints.*;

/**
 * 创建或更新凭据中心条目的请求参数。
 * <p>
 * 对应 {@code ResourceController} 的 {@code POST /api/resources/credentials}
 * 与 {@code PUT /api/resources/credentials/{id}}，登记 SSH/数据库/Git 等敏感凭据。
 * </p>
 * @author yangdb
 */
public class SaveCredentialRequest {
    /** 凭据编码，大写字母开头，仅含大写字母、数字与下划线。 */
    @NotBlank @Pattern(regexp="^[A-Z][A-Z0-9_]{1,63}$") private String code;
    /** 凭据展示名称，必填，最长 128。 */
    @NotBlank @Size(max=128) private String name;
    /** 凭据类型：SSH、DATABASE、GIT、CICD、API_KEY。 */
    @NotBlank @Pattern(regexp="^(SSH|DATABASE|GIT|CICD|API_KEY)$") private String type;
    /** 用户名/访问账号，最长 128。 */
    @Size(max=128) private String username;
    /** 明文密钥/密码；服务端加密落库，最长 1000。 */
    @Size(max=1000) private String secret;
    /** 备注说明，最长 500。 */
    @Size(max=500) private String description;
    /** 是否启用该凭据。 */
    private Boolean enabled;
    /**
     * 获取凭据编码。
     *
     * @return 编码
     */
    public String getCode(){return code;}
    /**
     * 设置凭据编码。
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
     * 获取凭据类型。
     *
     * @return 类型枚举字符串
     */
    public String getType(){return type;}
    /**
     * 设置凭据类型。
     *
     * @param v SSH/DATABASE/GIT/CICD/API_KEY
     */
    public void setType(String v){type=v;}
    /**
     * 获取用户名。
     *
     * @return 用户名
     */
    public String getUsername(){return username;}
    /**
     * 设置用户名。
     *
     * @param v 用户名
     */
    public void setUsername(String v){username=v;}
    /**
     * 获取明文密钥。
     *
     * @return 密钥/密码
     */
    public String getSecret(){return secret;}
    /**
     * 设置明文密钥。
     *
     * @param v 密钥/密码
     */
    public void setSecret(String v){secret=v;}
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
