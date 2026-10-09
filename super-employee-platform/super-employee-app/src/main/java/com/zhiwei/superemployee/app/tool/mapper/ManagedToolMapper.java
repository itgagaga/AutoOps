package com.zhiwei.superemployee.app.tool.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 受管工具 MyBatis Mapper。
 * <p>映射表 {@code managed_tool}，继承 {@link BaseMapper} 提供标准 CRUD，无额外自定义 SQL。</p>
 * @author yangdb
 */
@Mapper
public interface ManagedToolMapper extends BaseMapper<ManagedToolEntity> {
}
