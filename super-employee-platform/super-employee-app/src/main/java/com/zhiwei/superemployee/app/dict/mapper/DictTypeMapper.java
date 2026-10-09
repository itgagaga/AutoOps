package com.zhiwei.superemployee.app.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典类型 MyBatis-Plus Mapper。
 * <p>继承 {@link BaseMapper}，提供对 {@link DictTypeEntity}（表 {@code app_dict_type}）的通用 CRUD。</p>
 * @author yangdb
 */
@Mapper
public interface DictTypeMapper extends BaseMapper<DictTypeEntity> {
}
