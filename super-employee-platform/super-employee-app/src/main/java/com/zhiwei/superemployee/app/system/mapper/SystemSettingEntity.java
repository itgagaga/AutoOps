package com.zhiwei.superemployee.app.system.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 系统键值配置表实体，映射 {@code app_system_setting}。
 * <p>供 MyBatis-Plus 读写；业务层通过 {@link com.zhiwei.superemployee.app.system.SystemSettingRepository} 访问。</p>
 * @author yangdb
 */
@TableName("app_system_setting")
public class SystemSettingEntity {
    /** 配置键（主键），如 {@code local.code.workspace}。 */
    @TableId(value = "setting_key", type = IdType.INPUT)
    private String settingKey;
    /** 配置值（字符串形式存储）。 */
    private String settingValue;
    /** 最近更新时间（ISO-8601 字符串）。 */
    private String updatedAt;

    /**
     * @return 配置键
     */
    public String getSettingKey() { return settingKey; }

    /**
     * @param settingKey 配置键
     */
    public void setSettingKey(String settingKey) { this.settingKey = settingKey; }

    /**
     * @return 配置值
     */
    public String getSettingValue() { return settingValue; }

    /**
     * @param settingValue 配置值
     */
    public void setSettingValue(String settingValue) { this.settingValue = settingValue; }

    /**
     * @return 最近更新时间
     */
    public String getUpdatedAt() { return updatedAt; }

    /**
     * @param updatedAt 最近更新时间（ISO-8601）
     */
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
