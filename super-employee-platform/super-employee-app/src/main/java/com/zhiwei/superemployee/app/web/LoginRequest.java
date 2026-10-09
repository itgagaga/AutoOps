package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录请求参数。
 * <p>
 * 对应 {@code AuthController} 的 {@code POST /api/auth/login}，
 * 须携带用户名、密码以及事先通过验证码接口获取的 captchaId 与用户填写的答案。
 * </p>
 * @author yangdb
 */
public class LoginRequest {
    /** 登录用户名，必填。 */
    @NotBlank
    private String username;
    /** 登录密码，必填。 */
    @NotBlank
    private String password;
    /** 验证码会话标识，须与 {@code GET /api/auth/captcha} 返回的 id 一致。 */
    @NotBlank
    private String captchaId;
    /** 用户填写的验证码答案，必填。 */
    @NotBlank
    private String captchaAnswer;

    /**
     * 获取登录用户名。
     *
     * @return 用户名
     */
    public String getUsername() { return username; }
    /**
     * 设置登录用户名。
     *
     * @param username 登录用户名
     */
    public void setUsername(String username) { this.username = username; }
    /**
     * 获取登录密码。
     *
     * @return 密码明文
     */
    public String getPassword() { return password; }
    /**
     * 设置登录密码。
     *
     * @param password 登录密码
     */
    public void setPassword(String password) { this.password = password; }
    /**
     * 获取验证码会话标识。
     *
     * @return captchaId
     */
    public String getCaptchaId() { return captchaId; }
    /**
     * 设置验证码会话标识。
     *
     * @param captchaId 验证码会话 id
     */
    public void setCaptchaId(String captchaId) { this.captchaId = captchaId; }
    /**
     * 获取验证码答案。
     *
     * @return 用户填写的验证码
     */
    public String getCaptchaAnswer() { return captchaAnswer; }
    /**
     * 设置验证码答案。
     *
     * @param captchaAnswer 验证码答案
     */
    public void setCaptchaAnswer(String captchaAnswer) { this.captchaAnswer = captchaAnswer; }
}
