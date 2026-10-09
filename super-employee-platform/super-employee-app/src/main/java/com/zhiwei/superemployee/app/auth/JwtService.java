package com.zhiwei.superemployee.app.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与解析服务。
 * <p>
 * 使用 HMAC-SHA 密钥对登录用户签发访问令牌，并在过滤器中解析校验签名与过期时间。
 * 令牌 subject 为用户名，额外声明展示名与角色编码。
 * </p>
 * @author yangdb
 */
@Service
public class JwtService {
    /** 用于签名与验签的对称密钥。 */
    private final SecretKey key;
    /** 令牌有效期（秒），构造时钳制在 [300, 86400]。 */
    private final long validitySeconds;

    /**
     * 根据配置初始化 JWT 密钥与有效期。
     *
     * @param secret           JWT 密钥原文，UTF-8 至少 32 字节
     * @param validitySeconds  配置的有效期秒数，实际钳制到 5 分钟～24 小时
     * @throws IllegalStateException 密钥为空或长度不足 32 字节时
     */
    public JwtService(@Value("${super-employee.security.jwt-secret}") String secret,
                      @Value("${super-employee.security.token-validity-seconds:28800}") long validitySeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT 密钥至少需要 32 字节，请在本地 application-private.yml 中配置 super-employee.security.jwt-secret");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.validitySeconds = Math.max(300, Math.min(validitySeconds, 86400));
    }

    /**
     * 为已认证用户签发 JWT 访问令牌。
     * <p>声明包含 subject（用户名）、displayName、role，以及签发时间与过期时间。</p>
     *
     * @param user 已通过认证的用户领域对象
     * @return 紧凑序列化后的 JWT 字符串
     */
    public String createToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("displayName", user.getDisplayName())
                .claim("role", user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(validitySeconds)))
                .signWith(key)
                .compact();
    }

    /**
     * 验签并解析 JWT，返回载荷声明。
     *
     * @param token 客户端提交的 JWT 字符串
     * @return 解析后的 Claims（含 subject、自定义 claim 与时间字段）
     * @throws io.jsonwebtoken.JwtException 签名无效、格式错误或已过期等解析失败时
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    /**
     * 获取当前服务配置的令牌有效期（秒）。
     *
     * @return 有效期秒数
     */
    public long getValiditySeconds() { return validitySeconds; }
}
