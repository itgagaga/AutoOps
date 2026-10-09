package com.zhiwei.superemployee.app.resource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 资源中心凭据密文加解密服务。
 * <p>
 * 使用 AES-GCM，密钥由配置串经 SHA-256 派生；密文格式为
 * {@code v1:Base64(iv):Base64(ciphertext)}。
 * 密钥由私有配置项 {@code super-employee.security.credential-encryption-key} 提供。
 * </p>
 * @author yangdb
 */
@Service
public class CredentialSecretService {
    /** AES 密钥（SHA-256 派生后的 256 位）。 */
    private final SecretKeySpec key;
    /** 用于生成 GCM IV 的安全随机数。 */
    private final SecureRandom random = new SecureRandom();

    /**
     * 根据配置密钥材料初始化 AES 密钥。
     *
     * @param value 非空的独立加密密钥材料
     * @throws IllegalStateException 无法初始化 AES 密钥时
     */
    public CredentialSecretService(
            @Value("${super-employee.security.credential-encryption-key}")
            String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("请在本地 application-private.yml 中配置非空的 credential-encryption-key");
        }
        try {
            this.key = new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)),
                    "AES");
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to initialize credential encryption", e);
        }
    }

    /**
     * 加密明文凭据材料。
     *
     * @param value 明文密码或私钥文本
     * @return 带版本前缀的密文字符串
     * @throws IllegalStateException 加密失败
     */
    public String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return "v1:" + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt credential", e);
        }
    }

    /**
     * 解密凭据密文。
     *
     * @param value {@link #encrypt(String)} 产出的密文字符串
     * @return 明文
     * @throws IllegalStateException 解密失败或格式非法
     */
    public String decrypt(String value) {
        try {
            String[] parts = value.split(":", 3);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt credential", e);
        }
    }
}
