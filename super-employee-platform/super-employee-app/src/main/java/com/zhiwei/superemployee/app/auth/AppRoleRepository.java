package com.zhiwei.superemployee.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiwei.superemployee.app.auth.mapper.AppRoleEntity;
import com.zhiwei.superemployee.app.auth.mapper.AppRoleMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 系统角色仓储。
 * <p>维护 {@code app_role} 及角色-菜单关联，供权限管理与登录后菜单裁剪使用。</p>
 * @author yangdb
 */
@Repository
public class AppRoleRepository {
    private final AppRoleMapper mapper;

    /**
     * 注入角色 Mapper。
     *
     * @param mapper MyBatis 角色 Mapper
     */
    public AppRoleRepository(AppRoleMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询全部角色，按角色编码升序。
     *
     * @return 角色列表
     */
    public List<AppRole> findAll() {
        return mapper.selectList(Wrappers.<AppRoleEntity>lambdaQuery()
                        .orderByAsc(AppRoleEntity::getRoleCode))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 按主键查询角色。
     *
     * @param id 角色 ID
     * @return 角色；不存在则为 empty
     */
    public Optional<AppRole> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    /**
     * 按角色编码查询（如 ADMIN、OPERATOR）。
     *
     * @param code 角色编码
     * @return 角色；不存在则为 empty
     */
    public Optional<AppRole> findByCode(String code) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<AppRoleEntity>lambdaQuery()
                        .eq(AppRoleEntity::getRoleCode, code)))
                .map(this::toDomain);
    }

    /**
     * 新增角色。调用方需保证 role_code 唯一。
     *
     * @param role 待插入角色（含已分配 ID）
     * @return 原样返回入参
     */
    public AppRole save(AppRole role) {
        AppRoleEntity entity = new AppRoleEntity();
        entity.setId(role.getId());
        entity.setRoleCode(role.getCode());
        entity.setRoleName(role.getName());
        entity.setDescription(role.getDescription());
        entity.setEnabled(role.isEnabled() ? 1 : 0);
        entity.setBuiltIn(role.isBuiltIn() ? 1 : 0);
        mapper.insert(entity);
        return role;
    }

    /**
     * 更新角色名称、描述与启用状态；不修改 role_code、built_in。
     *
     * @param role 含最新字段的角色对象
     * @return 原样返回入参
     */
    public AppRole update(AppRole role) {
        mapper.update(null, Wrappers.<AppRoleEntity>lambdaUpdate()
                .set(AppRoleEntity::getRoleName, role.getName())
                .set(AppRoleEntity::getDescription, role.getDescription())
                .set(AppRoleEntity::getEnabled, role.isEnabled() ? 1 : 0)
                .eq(AppRoleEntity::getId, role.getId()));
        return role;
    }

    /**
     * 按角色编码查询已授权菜单编码列表。
     *
     * @param roleCode 角色编码
     * @return 菜单编码列表
     */
    public List<String> findMenuCodes(String roleCode) {
        return mapper.selectMenuCodesByRoleCode(roleCode);
    }

    /**
     * 按角色主键查询已绑定菜单 ID 列表（用于权限编辑回填）。
     *
     * @param roleId 角色 ID
     * @return 菜单 ID 列表
     */
    public List<String> findMenuIds(String roleId) {
        return mapper.selectMenuIdsByRoleId(roleId);
    }

    /**
     * 全量替换角色菜单授权：先删后插，同一事务内完成。
     *
     * @param roleId  角色 ID
     * @param menuIds 新的菜单 ID 集合（可为空表示清空）
     */
    @Transactional
    public void replaceMenus(String roleId, List<String> menuIds) {
        mapper.deleteRoleMenus(roleId);
        for (String menuId : menuIds) {
            mapper.insertRoleMenu(roleId, menuId);
        }
    }

    /**
     * 表实体转领域对象；enabled / builtIn 以 1/0 表示。
     *
     * @param entity 角色表实体
     * @return 领域角色对象
     */
    private AppRole toDomain(AppRoleEntity entity) {
        return new AppRole(entity.getId(), entity.getRoleCode(), entity.getRoleName(), entity.getDescription(),
                entity.getEnabled() != null && entity.getEnabled() == 1,
                entity.getBuiltIn() != null && entity.getBuiltIn() == 1);
    }
}
