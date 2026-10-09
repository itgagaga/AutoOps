package com.zhiwei.superemployee.app.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 受管应用表 {@code managed_application} 的 MyBatis Mapper。
 * <p>
 * 继承 {@link BaseMapper} 提供基础 CRUD；另提供与主机表联查主机名的自定义查询，
 * 供资源中心应用列表与详情展示使用（SQL 定义于对应 XML）。
 * </p>
 * @author yangdb
 */
@Mapper
public interface ManagedApplicationMapper extends BaseMapper<ManagedApplicationEntity> {

    /**
     * 查询全部应用并 LEFT JOIN 主机名，按业务约定排序。
     *
     * @return 含 {@code hostName} 的应用实体列表；无数据时返回空列表
     */
    List<ManagedApplicationEntity> selectAllWithHostName();

    /**
     * 按主键查询应用并 LEFT JOIN 主机名。
     *
     * @param id 应用主键
     * @return 含主机名的实体；不存在时返回 {@code null}
     */
    ManagedApplicationEntity selectByIdWithHostName(@Param("id") String id);
}
