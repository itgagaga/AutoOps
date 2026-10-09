package com.zhiwei.superemployee.app.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户权限装配服务。
 * <p>
 * 根据用户角色编码查询角色启用状态，并组装 Spring Security 所需的角色权限
 * （{@code ROLE_*}）与菜单编码权限，供 JWT 过滤器写入认证上下文。
 * </p>
 * @author yangdb
 */
@Service
public class UserAuthorityService {
    private final AppRoleRepository roleRepository;

    /**
     * 注入角色仓储。
     *
     * @param roleRepository 角色仓储
     */
    public UserAuthorityService(AppRoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    /**
     * 判断用户所属角色当前是否启用。
     * <p>角色不存在时视为未启用，拒绝建立认证。</p>
     *
     * @param user 待校验用户
     * @return 角色存在且 enabled 为 true 时返回 true
     */
    public boolean isRoleEnabled(AppUser user) {
        return roleRepository.findByCode(user.getRole()).map(AppRole::isEnabled).orElse(false);
    }

    /**
     * 组装用户的授权列表：角色权限 + 已绑定菜单编码权限。
     *
     * @param user 已通过启用校验的用户
     * @return 非空权限列表；至少包含 {@code ROLE_<roleCode>}
     */
    public List<GrantedAuthority> getAuthorities(AppUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
        for (String menuCode : roleRepository.findMenuCodes(user.getRole())) {
            authorities.add(new SimpleGrantedAuthority(menuCode));
        }
        return authorities;
    }
}
