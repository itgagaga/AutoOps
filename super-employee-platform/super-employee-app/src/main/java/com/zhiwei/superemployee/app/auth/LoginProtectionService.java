package com.zhiwei.superemployee.app.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败防护服务。
 * <p>
 * 按「客户端 + 用户名」维度统计时间窗口内的失败次数，达到阈值后临时锁定，
 * 防止对同一账号或来源的暴力尝试；登录成功后清除对应计数。
 * </p>
 * @author yangdb
 */
@Service
public class LoginProtectionService {
    /** 失败尝试与锁定状态缓存。 */
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    /** 窗口内允许的最大失败次数。 */
    private final int maxFailures;
    /** 失败计数滑动窗口长度（秒）。 */
    private final long windowSeconds;
    /** 触发锁定后的锁定时长（秒）。 */
    private final long lockSeconds;

    /**
     * 通过配置构造登录防护服务，并对配置值施加下限保护。
     *
     * @param maxFailures   最大失败次数，实际至少 3
     * @param windowSeconds 统计窗口秒数，实际至少 60
     * @param lockSeconds   锁定秒数，实际至少 60
     */
    public LoginProtectionService(
            @Value("${super-employee.security.login.max-failures:5}") int maxFailures,
            @Value("${super-employee.security.login.window-seconds:300}") long windowSeconds,
            @Value("${super-employee.security.login.lock-seconds:600}") long lockSeconds) {
        this.maxFailures = Math.max(3, maxFailures);
        this.windowSeconds = Math.max(60, windowSeconds);
        this.lockSeconds = Math.max(60, lockSeconds);
    }

    /**
     * 检查当前客户端与用户名是否仍允许尝试登录。
     *
     * @param client   客户端标识（如 IP）
     * @param username 登录用户名
     * @throws IllegalStateException 处于锁定期内时
     */
    public void checkAllowed(String client, String username) {
        Attempt attempt = attempts.get(key(client, username));
        if (attempt != null && attempt.lockedUntil() != null
                && Instant.now().isBefore(attempt.lockedUntil())) {
            throw new IllegalStateException("登录尝试过于频繁，请稍后再试");
        }
    }

    /**
     * 记录一次登录失败；达到阈值时写入锁定截止时间。
     *
     * @param client   客户端标识
     * @param username 登录用户名
     */
    public void failure(String client, String username) {
        String key = key(client, username);
        Instant now = Instant.now();
        attempts.compute(key, (ignored, current) -> {
            int failures = current == null || now.isAfter(current.windowStarted().plusSeconds(windowSeconds))
                    ? 1 : current.failures() + 1;
            Instant started = current == null || now.isAfter(current.windowStarted().plusSeconds(windowSeconds))
                    ? now : current.windowStarted();
            Instant lockedUntil = failures >= maxFailures ? now.plusSeconds(lockSeconds) : null;
            return new Attempt(failures, started, lockedUntil);
        });
        cleanup(now);
    }

    /**
     * 登录成功后清除该客户端与用户名的失败计数与锁定状态。
     *
     * @param client   客户端标识
     * @param username 登录用户名
     */
    public void success(String client, String username) {
        attempts.remove(key(client, username));
    }

    /**
     * 生成防护键：客户端 + 小写用户名。
     *
     * @param client   客户端标识
     * @param username 用户名
     * @return 组合键
     */
    private String key(String client, String username) {
        return String.valueOf(client) + ':' + String.valueOf(username).trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 移除已过窗口或已过锁定期的条目。
     *
     * @param now 当前时间
     */
    private void cleanup(Instant now) {
        attempts.entrySet().removeIf(entry -> {
            Attempt value = entry.getValue();
            Instant expiry = value.lockedUntil() == null
                    ? value.windowStarted().plusSeconds(windowSeconds)
                    : value.lockedUntil();
            return now.isAfter(expiry);
        });
    }

    /**
     * 单键的失败尝试状态。
     *
     * @param failures      当前窗口内失败次数
     * @param windowStarted 窗口起始时间
     * @param lockedUntil   锁定截止时间；未锁定时为 null
     */
    private record Attempt(int failures, Instant windowStarted, Instant lockedUntil) {}
}
