package com.zhiwei.superemployee.app.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 初始管理员引导器。
 * <p>
 * 应用启动时检查配置的初始管理员用户名是否已存在；若不存在则创建启用状态的 ADMIN 角色用户，
 * 便于首次部署后即可登录控制台。已存在同名用户时不做修改。
 * </p>
 * @author yangdb
 */
@Component
public class InitialAdminInitializer implements ApplicationRunner {
    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    /** 配置的初始管理员登录名。 */
    private final String username;
    /** 配置的初始管理员明文密码（仅用于首次入库前哈希）。 */
    private final String password;
    /** 配置的初始管理员展示名。 */
    private final String displayName;

    /**
     * 注入仓储、密码编码器与初始管理员配置。
     *
     * @param repository      用户仓储
     * @param passwordEncoder 密码哈希编码器
     * @param username        初始管理员用户名
     * @param password        初始管理员明文密码
     * @param displayName     初始管理员展示名
     */
    public InitialAdminInitializer(AppUserRepository repository, PasswordEncoder passwordEncoder,
                                   @Value("${super-employee.security.initial-admin.username}") String username,
                                   @Value("${super-employee.security.initial-admin.password}") String password,
                                   @Value("${super-employee.security.initial-admin.display-name:System Administrator}") String displayName) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
        this.displayName = displayName;
    }

    /**
     * 应用就绪后执行：若初始管理员不存在则创建。
     *
     * @param args Spring Boot 启动参数（本实现未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("请在本地 application-private.yml 中配置非空的初始管理员账号");
        }
        if (!repository.findByUsername(username).isPresent()) {
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("请在本地 application-private.yml 中配置非空的初始管理员账号和密码");
            }
            repository.save(new AppUser(UUID.randomUUID().toString(), username, passwordEncoder.encode(password),
                    displayName, "ADMIN", true));
        }
    }
}
