package com.zhiwei.superemployee.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiwei.superemployee.app.auth.mapper.AppUserEntity;
import com.zhiwei.superemployee.app.auth.mapper.AppUserMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 系统用户仓储。
 * <p>基于 MyBatis-Plus 访问 {@code app_user}，负责登录校验、用户列表与角色/启用状态变更。</p>
 * @author yangdb
 */
@Repository
public class AppUserRepository {
    private final AppUserMapper mapper;

    /**
     * 注入用户 Mapper。
     *
     * @param mapper MyBatis 用户 Mapper
     */
    public AppUserRepository(AppUserMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 按登录名精确查询用户（含密码哈希，仅供认证链路使用）。
     *
     * @param username 登录用户名
     * @return 匹配用户；不存在则为 empty
     */
    public Optional<AppUser> findByUsername(String username) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<AppUserEntity>lambdaQuery()
                        .eq(AppUserEntity::getUsername, username)))
                .map(this::toDomain);
    }

    /**
     * 按主键查询用户。
     *
     * @param id 用户 ID
     * @return 匹配用户；不存在则为 empty
     */
    public Optional<AppUser> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    /**
     * 查询全部用户，按用户名升序。
     *
     * @return 用户列表
     */
    public List<AppUser> findAll() {
        return mapper.selectList(Wrappers.<AppUserEntity>lambdaQuery()
                        .orderByAsc(AppUserEntity::getUsername))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 新增用户。调用方需保证 username 唯一且密码已哈希。
     *
     * @param user 待插入用户（含已分配 ID）
     * @return 原样返回入参用户
     */
    public AppUser save(AppUser user) {
        AppUserEntity entity = new AppUserEntity();
        entity.setId(user.getId());
        entity.setUsername(user.getUsername());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setDisplayName(user.getDisplayName());
        entity.setRoleName(user.getRole());
        entity.setEnabled(user.isEnabled() ? 1 : 0);
        mapper.insert(entity);
        return user;
    }

    /**
     * 更新用户角色编码（写入 {@code role_name} 字段）。
     *
     * @param user 当前用户快照
     * @param role 新角色编码
     * @return 角色已更新的新用户对象
     */
    public AppUser updateRole(AppUser user, String role) {
        mapper.update(null, Wrappers.<AppUserEntity>lambdaUpdate()
                .set(AppUserEntity::getRoleName, role)
                .eq(AppUserEntity::getId, user.getId()));
        return new AppUser(user.getId(), user.getUsername(), user.getPasswordHash(), user.getDisplayName(), role,
                user.isEnabled());
    }

    /**
     * 启用或停用用户；停用后不可登录。
     *
     * @param id      用户 ID
     * @param enabled true 启用，false 停用
     * @return 更新后的用户
     * @throws java.util.NoSuchElementException 用户不存在时
     */
    public AppUser updateEnabled(String id, boolean enabled) {
        mapper.update(null, Wrappers.<AppUserEntity>lambdaUpdate()
                .set(AppUserEntity::getEnabled, enabled ? 1 : 0)
                .eq(AppUserEntity::getId, id));
        return findById(id).orElseThrow();
    }

    /**
     * 更新用户密码哈希。调用方必须先完成原密码校验。
     *
     * @param id           用户 ID
     * @param passwordHash 新密码哈希
     */
    public void updatePassword(String id, String passwordHash) {
        mapper.update(null, Wrappers.<AppUserEntity>lambdaUpdate()
                .set(AppUserEntity::getPasswordHash, passwordHash)
                .eq(AppUserEntity::getId, id));
    }

    /**
     * 表实体转领域对象；{@code enabled} 以 1/0 表示。
     *
     * @param entity 用户表实体
     * @return 领域用户对象
     */
    private AppUser toDomain(AppUserEntity entity) {
        return new AppUser(entity.getId(), entity.getUsername(), entity.getPasswordHash(),
                entity.getDisplayName(), entity.getRoleName(),
                entity.getEnabled() != null && entity.getEnabled() == 1);
    }
}
