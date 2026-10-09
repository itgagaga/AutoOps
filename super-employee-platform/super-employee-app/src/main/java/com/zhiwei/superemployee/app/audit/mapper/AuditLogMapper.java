package com.zhiwei.superemployee.app.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作审计日志 MyBatis-Plus Mapper。
 * <p>
 * 继承 {@link BaseMapper}，提供对 {@link AuditLogEntity}（表 {@code audit_log}）的
 * 通用增删改查；业务仓储通过本接口完成写入与列表查询。
 * </p>
 * @author yangdb
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {
}
