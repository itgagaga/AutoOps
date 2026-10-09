package com.zhiwei.superemployee.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiwei.superemployee.app.auth.mapper.AppMenuEntity;
import com.zhiwei.superemployee.app.auth.mapper.AppMenuMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 系统菜单仓储。
 * <p>维护 {@code app_menu} 树形菜单定义，并按角色查询已启用菜单供前端侧栏渲染。</p>
 * @author yangdb
 */
@Repository
public class AppMenuRepository {
    private final AppMenuMapper mapper;

    /**
     * 注入菜单 Mapper。
     *
     * @param mapper MyBatis 菜单 Mapper
     */
    public AppMenuRepository(AppMenuMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 查询全部菜单，按排序号、菜单编码升序。
     *
     * @return 菜单列表（扁平，含父子关系字段）
     */
    public List<AppMenu> findAll() {
        return mapper.selectList(Wrappers.<AppMenuEntity>lambdaQuery()
                        .orderByAsc(AppMenuEntity::getSortOrder, AppMenuEntity::getMenuCode))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 按主键查询菜单。
     *
     * @param id 菜单 ID
     * @return 菜单；不存在则为 empty
     */
    public Optional<AppMenu> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    /**
     * 按菜单编码查询。
     *
     * @param code 菜单编码
     * @return 菜单；不存在则为 empty
     */
    public Optional<AppMenu> findByCode(String code) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<AppMenuEntity>lambdaQuery()
                        .eq(AppMenuEntity::getMenuCode, code)))
                .map(this::toDomain);
    }

    /**
     * 查询指定角色下已启用且已授权的菜单（JOIN 角色菜单表）。
     *
     * @param roleCode 角色编码
     * @return 可展示菜单列表
     */
    public List<AppMenu> findEnabledByRole(String roleCode) {
        return mapper.selectEnabledByRoleCode(roleCode).stream().map(this::toDomain).toList();
    }

    /**
     * 新增菜单项。
     *
     * @param menu 待插入菜单（含已分配 ID）
     * @return 原样返回入参
     */
    public AppMenu save(AppMenu menu) {
        mapper.insert(toEntity(menu));
        return menu;
    }

    /**
     * 更新菜单展示字段（父级、名称、路由、图标、排序、启用），不修改 menu_code。
     *
     * @param menu 含最新配置的菜单对象
     * @return 原样返回入参
     */
    public AppMenu update(AppMenu menu) {
        AppMenuEntity entity = toEntity(menu);
        mapper.update(null, Wrappers.<AppMenuEntity>lambdaUpdate()
                .set(AppMenuEntity::getParentId, entity.getParentId())
                .set(AppMenuEntity::getMenuName, entity.getMenuName())
                .set(AppMenuEntity::getRoutePath, entity.getRoutePath())
                .set(AppMenuEntity::getIconName, entity.getIconName())
                .set(AppMenuEntity::getSortOrder, entity.getSortOrder())
                .set(AppMenuEntity::getEnabled, entity.getEnabled())
                .eq(AppMenuEntity::getId, entity.getId()));
        return menu;
    }

    /**
     * 领域对象转表实体。
     *
     * @param menu 菜单领域对象
     * @return 菜单表实体
     */
    private AppMenuEntity toEntity(AppMenu menu) {
        AppMenuEntity entity = new AppMenuEntity();
        entity.setId(menu.getId());
        entity.setParentId(menu.getParentId());
        entity.setMenuCode(menu.getCode());
        entity.setMenuName(menu.getName());
        entity.setRoutePath(menu.getPath());
        entity.setIconName(menu.getIcon());
        entity.setSortOrder(menu.getSortOrder());
        entity.setEnabled(menu.isEnabled() ? 1 : 0);
        return entity;
    }

    /**
     * 表实体转领域对象；sortOrder 空则按 0，enabled 以 1/0 表示。
     *
     * @param entity 菜单表实体
     * @return 领域菜单对象
     */
    private AppMenu toDomain(AppMenuEntity entity) {
        return new AppMenu(entity.getId(), entity.getParentId(), entity.getMenuCode(), entity.getMenuName(),
                entity.getRoutePath(), entity.getIconName(),
                entity.getSortOrder() == null ? 0 : entity.getSortOrder(),
                entity.getEnabled() != null && entity.getEnabled() == 1);
    }
}
