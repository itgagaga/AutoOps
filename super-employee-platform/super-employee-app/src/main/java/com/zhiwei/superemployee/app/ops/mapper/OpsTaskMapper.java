package com.zhiwei.superemployee.app.ops.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运维任务表 {@code ops_task} 的 MyBatis-Plus Mapper。
 * <p>继承 {@link BaseMapper} 提供基础 CRUD，业务仓储在其之上做领域映射。</p>
 * @author yangdb
 */
@Mapper
public interface OpsTaskMapper extends BaseMapper<OpsTaskEntity> {
}
