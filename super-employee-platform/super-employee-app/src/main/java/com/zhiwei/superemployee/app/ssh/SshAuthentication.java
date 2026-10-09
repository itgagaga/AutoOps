package com.zhiwei.superemployee.app.ssh;

import com.zhiwei.superemployee.app.resource.ManagedCredential;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive;
import net.schmizz.sshj.userauth.method.AuthPassword;
import net.schmizz.sshj.userauth.method.PasswordResponseProvider;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.PasswordUtils;

import java.io.IOException;

/**
 * 统一 SSH 认证工具。
 * <p>
 * 支持私钥公钥认证，以及密码 + keyboard-interactive（兼容多数 Linux sshd）。
 * 不可实例化。
 * </p>
 * @author yangdb
 */
public final class SshAuthentication {
    /**
     * 禁止实例化。
     */
    private SshAuthentication() {
    }

    /**
     * 使用凭据对已连接的 SSH 客户端完成用户认证。
     *
     * @param ssh        已 {@code connect} 但尚未认证的客户端
     * @param credential 含用户名与类型信息的凭据
     * @param secret     明文密码或私钥 PEM 文本
     * @throws IOException              认证失败（包装 {@link UserAuthException}）
     * @throws IllegalStateException    用户名或密钥材料为空
     */
    public static void authenticate(SSHClient ssh, ManagedCredential credential, String secret) throws IOException {
        if (credential.getUsername() == null || credential.getUsername().isBlank()) {
            throw new IllegalStateException("SSH 凭据未配置登录用户");
        }
        String username = credential.getUsername().trim();
        String material = secret == null ? "" : secret.strip();
        if (material.isBlank()) {
            throw new IllegalStateException("SSH 凭据密码/私钥为空，请在凭据管理中重新配置");
        }
        try {
            if (looksLikePrivateKey(material)) {
                PasswordFinder emptyPassphrase = PasswordUtils.createOneOff(new char[0]);
                KeyProvider key = ssh.loadKeys(material, null, emptyPassphrase);
                ssh.authPublickey(username, key);
                return;
            }
            // 许多发行版默认仅开放 keyboard-interactive，单独 authPassword 会报 Exhausted available authentication methods
            // PasswordFinder 使用 createOneOff，每种认证方法各持一份，避免密码只读一次后被清空
            ssh.auth(username,
                    new AuthPassword(PasswordUtils.createOneOff(material.toCharArray())),
                    new AuthKeyboardInteractive(new PasswordResponseProvider(
                            PasswordUtils.createOneOff(material.toCharArray()))));
        }
        catch (UserAuthException ex) {
            throw new IOException("SSH 认证失败：用户名/密码（或私钥）不正确，或服务器未允许 password/keyboard-interactive/publickey。"
                    + " 原始错误：" + ex.getMessage(), ex);
        }
    }

    /**
     * 粗判密钥材料是否为 PEM 私钥。
     *
     * @param value 密钥材料文本
     * @return 看起来像私钥时为 {@code true}
     */
    private static boolean looksLikePrivateKey(String value) {
        String upper = value.toUpperCase();
        return upper.contains("-----BEGIN") && upper.contains("PRIVATE KEY");
    }
}
