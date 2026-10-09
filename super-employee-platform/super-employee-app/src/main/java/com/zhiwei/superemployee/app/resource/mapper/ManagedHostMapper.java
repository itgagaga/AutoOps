package com.zhiwei.superemployee.app.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 受管主机表 {@code managed_host} 的 MyBatis Mapper。
 * <p>
 * 继承 {@link BaseMapper} 提供按主键增删改查与条件构造能力；
 * 复杂查询条件在 {@link com.zhiwei.superemployee.app.resource.ResourceRepository} 中通过 Wrapper 组装。
 * </p>
 * @author yangdb
 */
@Mapper
public interface ManagedHostMapper extends BaseMapper<ManagedHostEntity> {
}
