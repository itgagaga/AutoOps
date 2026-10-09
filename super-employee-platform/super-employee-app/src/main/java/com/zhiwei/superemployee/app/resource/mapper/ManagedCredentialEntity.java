package com.zhiwei.superemployee.app.resource.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 受管凭据表 {@code managed_credential} 的 MyBatis-Plus 持久化实体。
 * <p>
 * {@code secretValue} 存加密后的密文，明文仅在运行时经
 * {@link com.zhiwei.superemployee.app.resource.CredentialSecretService} 解密使用。
 * 与领域模型 {@link com.zhiwei.superemployee.app.resource.ManagedCredential} 通过仓储层互相转换。
 * </p>
 * @author yangdb
 */
@TableName("managed_credential")
public class ManagedCredentialEntity {
    /** 主键，由业务侧生成后以 INPUT 策略写入。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 凭据编码，全局唯一。 */
    private String credentialCode;
    /** 凭据显示名称。 */
    private String credentialName;
    /** 凭据类型，如 SSH。 */
    private String credentialType;
    /** 登录用户名。 */
    private String loginUsername;
    /** 加密后的密钥密文（密码或私钥）。 */
    private String secretValue;
    /** 描述说明。 */
    private String description;
    /** 启用标志：1 启用，0 停用。 */
    private Integer enabled;
    /** 创建时间，ISO-8601 字符串。 */
    private String createdAt;
    /** 最后更新时间，ISO-8601 字符串。 */
    private String updatedAt;

    /** @return 主键 ID */
    public String getId() { return id; }
    /**
     * 设置主键 ID。
     *
     * @param id 主键 ID
     */
    public void setId(String id) { this.id = id; }
    /** @return 凭据编码 */
    public String getCredentialCode() { return credentialCode; }
    /**
     * 设置凭据编码。
     *
     * @param credentialCode 凭据编码
     */
    public void setCredentialCode(String credentialCode) { this.credentialCode = credentialCode; }
    /** @return 凭据名称 */
    public String getCredentialName() { return credentialName; }
    /**
     * 设置凭据名称。
     *
     * @param credentialName 凭据名称
     */
    public void setCredentialName(String credentialName) { this.credentialName = credentialName; }
    /** @return 凭据类型 */
    public String getCredentialType() { return credentialType; }
    /**
     * 设置凭据类型。
     *
     * @param credentialType 凭据类型
     */
    public void setCredentialType(String credentialType) { this.credentialType = credentialType; }
    /** @return 登录用户名 */
    public String getLoginUsername() { return loginUsername; }
    /**
     * 设置登录用户名。
     *
     * @param loginUsername 登录用户名
     */
    public void setLoginUsername(String loginUsername) { this.loginUsername = loginUsername; }
    /** @return 密文/密钥材料 */
    public String getSecretValue() { return secretValue; }
    /**
     * 设置密文/密钥材料。
     *
     * @param secretValue 密文/密钥材料
     */
    public void setSecretValue(String secretValue) { this.secretValue = secretValue; }
    /** @return 描述说明 */
    public String getDescription() { return description; }
    /**
     * 设置描述说明。
     *
     * @param description 描述说明
     */
    public void setDescription(String description) { this.description = description; }
    /** @return 启用标志（1/0） */
    public Integer getEnabled() { return enabled; }
    /**
     * 设置启用标志。
     *
     * @param enabled 1 启用，0 停用
     */
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    /** @return 创建时间（ISO-8601 或 Instant） */
    public String getCreatedAt() { return createdAt; }
    /**
     * 设置创建时间。
     *
     * @param createdAt 创建时间（ISO-8601 或 Instant）
     */
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    /** @return 更新时间 */
    public String getUpdatedAt() { return updatedAt; }
    /**
     * 设置更新时间。
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
