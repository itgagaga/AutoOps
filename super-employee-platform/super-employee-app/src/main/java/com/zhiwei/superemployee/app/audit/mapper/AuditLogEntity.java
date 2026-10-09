package com.zhiwei.superemployee.app.audit.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 操作审计日志表实体，映射 {@code audit_log}。
 * <p>由 MyBatis-Plus 读写；主键由业务侧显式传入（{@link IdType#INPUT}）。</p>
 * @author yangdb
 */
@TableName("audit_log")
public class AuditLogEntity {
    /** 审计记录主键（UUID 等业务生成值）。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    /** 事件发生时间，ISO-8601 字符串。 */
    private String eventTime;
    /** 操作人用户名；无认证时多为 {@code system}。 */
    private String username;
    /** 动作名称（如 CREATE_USER、APPROVE）。 */
    private String actionName;
    /** 资源类型（如 USER、AGENT、APPROVAL）。 */
    private String resourceType;
    /** 资源标识（主键或业务编码），长度受写入侧约束。 */
    private String resourceId;
    /** 结果状态（如 SUCCESS、FAILED）。 */
    private String resultStatus;
    /** 详情摘要文本。 */
    private String detail;

    /**
     * 获取审计记录主键。
     *
     * @return 主键 ID
     */
    public String getId() { return id; }

    /**
     * 设置审计记录主键。
     *
     * @param id 主键 ID
     */
    public void setId(String id) { this.id = id; }

    /**
     * 获取事件发生时间。
     *
     * @return ISO-8601 时间字符串
     */
    public String getEventTime() { return eventTime; }

    /**
     * 设置事件发生时间。
     *
     * @param eventTime ISO-8601 时间字符串
     */
    public void setEventTime(String eventTime) { this.eventTime = eventTime; }

    /**
     * 获取操作人用户名。
     *
     * @return 用户名
     */
    public String getUsername() { return username; }

    /**
     * 设置操作人用户名。
     *
     * @param username 用户名
     */
    public void setUsername(String username) { this.username = username; }

    /**
     * 获取动作名称。
     *
     * @return 动作名
     */
    public String getActionName() { return actionName; }

    /**
     * 设置动作名称。
     *
     * @param actionName 动作名
     */
    public void setActionName(String actionName) { this.actionName = actionName; }

    /**
     * 获取资源类型。
     *
     * @return 资源类型
     */
    public String getResourceType() { return resourceType; }

    /**
     * 设置资源类型。
     *
     * @param resourceType 资源类型
     */
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    /**
     * 获取资源标识。
     *
     * @return 资源 ID 或业务编码
     */
    public String getResourceId() { return resourceId; }

    /**
     * 设置资源标识。
     *
     * @param resourceId 资源 ID 或业务编码
     */
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    /**
     * 获取结果状态。
     *
     * @return 结果状态字符串
     */
    public String getResultStatus() { return resultStatus; }

    /**
     * 设置结果状态。
     *
     * @param resultStatus 结果状态字符串
     */
    public void setResultStatus(String resultStatus) { this.resultStatus = resultStatus; }

    /**
     * 获取详情摘要。
     *
     * @return 详情文本
     */
    public String getDetail() { return detail; }

    /**
     * 设置详情摘要。
     *
     * @param detail 详情文本
     */
    public void setDetail(String detail) { this.detail = detail; }
}
