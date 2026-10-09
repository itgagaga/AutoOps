package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 当前登录用户修改密码的请求参数。
 * <p>
 * 对应 {@code AuthController} 的 {@code PUT /api/auth/password}，
 * 须同时提交原密码与新密码，服务端校验原密码正确后更新凭据。
 * </p>
 * @author yangdb
 */
public class ChangePasswordRequest {
    /** 当前有效密码，用于身份校验，最长 64。 */
    @NotBlank
    @Size(max = 64)
    private String currentPassword;

    /** 新密码，长度 8～64。 */
    @NotBlank
    @Size(min = 8, max = 64)
    private String newPassword;

    /**
     * 获取当前密码。
     *
     * @return 当前密码明文
     */
    public String getCurrentPassword() {
        return currentPassword;
    }

    /**
     * 设置当前密码。
     *
     * @param currentPassword 当前有效密码
     */
    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    /**
     * 获取新密码。
     *
     * @return 新密码明文
     */
    public String getNewPassword() {
        return newPassword;
    }

    /**
     * 设置新密码。
     *
     * @param newPassword 长度 8～64 的新密码
     */
    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
