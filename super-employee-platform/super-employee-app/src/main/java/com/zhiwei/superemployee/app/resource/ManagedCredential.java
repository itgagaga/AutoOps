package com.zhiwei.superemployee.app.resource;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 资源中心受管凭据领域对象。
 * <p>密钥材料对外 JSON 序列化时忽略，仅通过运行时解密服务使用。</p>
 * @author yangdb
 */
public class ManagedCredential {
    /** 主键 ID。 */
    private final String id;
    /** 凭据编码，全局唯一。 */
    private final String code;
    /** 凭据显示名称。 */
    private final String name;
    /** 凭据类型，如 SSH / DB 等。 */
    private final String type;
    /** 登录用户名。 */
    private final String username;
    /** 加密后的密文，或内存中的解密材料（视调用方而定）。 */
    private final String secretValue;
    /** 描述说明。 */
    private final String description;
    /** 是否启用。 */
    private final boolean enabled;
    /** 创建时间（ISO-8601 或 Instant 字符串）。 */
    private final String createdAt;
    /** 最后更新时间。 */
    private final String updatedAt;

    /**
     * 构造受管凭据只读快照。
     *
     * @param id          主键
     * @param code        凭据编码
     * @param name        凭据名称
     * @param type        凭据类型（如 SSH）
     * @param username    登录用户名
     * @param secretValue 加密后的密文（或内存中的解密材料，视调用方而定）
     * @param description 描述
     * @param enabled     是否启用
     * @param createdAt   创建时间
     * @param updatedAt   更新时间
     */
    public ManagedCredential(String id, String code, String name, String type, String username,
                             String secretValue, String description, boolean enabled,
                             String createdAt, String updatedAt) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.username = username;
        this.secretValue = secretValue;
        this.description = description;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** @return 主键 ID */
    public String getId() { return id; }

    /** @return 凭据编码 */
    public String getCode() { return code; }

    /** @return 凭据名称 */
    public String getName() { return name; }

    /** @return 凭据类型 */
    public String getType() { return type; }

    /** @return 登录用户名 */
    public String getUsername() { return username; }

    /**
     * 获取密钥材料（密码或私钥文本）。
     * <p>序列化到前端时忽略，避免泄露。</p>
     *
     * @return 密文或明文材料
     */
    @JsonIgnore
    public String getSecretValue() { return secretValue; }

    /**
     * 判断是否已配置密钥材料。
     *
     * @return 密文非空时返回 {@code true}
     */
    public boolean isSecretConfigured() { return secretValue != null && !secretValue.isEmpty(); }

    /** @return 描述说明 */
    public String getDescription() { return description; }

    /** @return 是否启用 */
    public boolean isEnabled() { return enabled; }

    /** @return 创建时间 */
    public String getCreatedAt() { return createdAt; }

    /** @return 更新时间 */
    public String getUpdatedAt() { return updatedAt; }
}
