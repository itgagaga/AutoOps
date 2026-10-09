package com.zhiwei.superemployee.app.dict.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 字典项 MyBatis-Plus Mapper。
 * <p>
 * 继承 {@link BaseMapper} 提供通用 CRUD，并扩展按类型编码查询已启用字典项的方法
 * （通常配合 XML/注解 SQL 做类型表 JOIN）。
 * </p>
 * @author yangdb
 */
@Mapper
public interface DictItemMapper extends BaseMapper<DictItemEntity> {

    /**
     * 按字典类型编码查询已启用的字典项列表。
     *
     * @param typeCode 字典类型编码
     * @return 已启用字典项实体列表；无匹配时返回空列表
     */
    List<DictItemEntity> selectEnabledByTypeCode(@Param("typeCode") String typeCode);
}
