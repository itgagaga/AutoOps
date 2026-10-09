package com.zhiwei.superemployee.app.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统角色 MyBatis Mapper。
 * <p>
 * 继承 {@link BaseMapper} 提供 {@code app_role} 基础 CRUD，
 * 并扩展角色-菜单关联表的查询与维护方法。
 * </p>
 * @author yangdb
 */
@Mapper
public interface AppRoleMapper extends BaseMapper<AppRoleEntity> {

    /**
     * 按角色编码查询已授权菜单编码列表。
     *
     * @param roleCode 角色编码
     * @return 菜单编码列表
     */
    List<String> selectMenuCodesByRoleCode(@Param("roleCode") String roleCode);

    /**
     * 按角色主键查询已绑定菜单 ID 列表。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 列表
     */
    List<String> selectMenuIdsByRoleId(@Param("roleId") String roleId);

    /**
     * 删除指定角色的全部菜单授权记录。
     *
     * @param roleId 角色 ID
     * @return 删除行数
     */
    int deleteRoleMenus(@Param("roleId") String roleId);

    /**
     * 插入一条角色-菜单授权记录。
     *
     * @param roleId 角色 ID
     * @param menuId 菜单 ID
     * @return 插入行数
     */
    int insertRoleMenu(@Param("roleId") String roleId, @Param("menuId") String menuId);
}
