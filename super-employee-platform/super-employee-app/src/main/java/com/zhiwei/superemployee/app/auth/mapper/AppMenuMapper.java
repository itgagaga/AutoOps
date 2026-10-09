package com.zhiwei.superemployee.app.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统菜单 MyBatis Mapper。
 * <p>继承 {@link BaseMapper} 提供基础 CRUD，并扩展按角色查询已启用菜单。</p>
 * @author yangdb
 */
@Mapper
public interface AppMenuMapper extends BaseMapper<AppMenuEntity> {

    /**
     * 查询指定角色下已启用且已授权的菜单列表。
     *
     * @param roleCode 角色编码
     * @return 菜单实体列表；无授权时为空列表
     */
    List<AppMenuEntity> selectEnabledByRoleCode(@Param("roleCode") String roleCode);
}
