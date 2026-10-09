package com.demo.opsapp.service;

import com.demo.opsapp.model.User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 故意缺陷：未知用户返回 null，调用方未做空判断时会触发 NPE。
 */
@Service
public class UserService {

    private final Map<Long, User> users = new ConcurrentHashMap<>();

    public UserService() {
        users.put(1L, new User(1L, "alice", "alice@example.com"));
        users.put(2L, new User(2L, "bob", "bob@example.com"));
    }

    public User findById(Long id) {
        // BUG: 应返回 Optional 或抛业务异常，这里直接返回 null
        return users.get(id);
    }

    public String maskEmail(Long id) {
        User user = findById(id);
        // 修复：添加 null 检查，避免用户不存在时 NPE
        if (user == null || user.getEmail() == null) {
            return "***@***.***";
        }
        String email = user.getEmail();
        int at = email.indexOf('@');
        // 修复：邮箱格式异常时保护 substring 不越界
        if (at <= 0) {
            return "***@***.***";
        }
        return email.substring(0, 1) + "***" + email.substring(at);
    }
}
