package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 按主机地址自动拉取 SSH 主机密钥指纹的请求参数。
 * <p>
 * 对应 {@code ResourceController} 的 {@code POST /api/resources/hosts/fetch-fingerprint}，
 * 用于新增/编辑主机前获取远端 host key，便于写入指纹白名单。
 * </p>
 * @author yangdb
 */
public class FetchHostFingerprintRequest {
    /** 目标主机 IP 或主机名，必填，最长 128。 */
    @NotBlank
    @Size(max = 128)
    private String ipAddress;

    /** SSH 端口，范围 1～65535，默认 22。 */
    @Min(1)
    @Max(65535)
    private Integer sshPort = 22;

    /**
     * 获取目标主机地址。
     *
     * @return IP 或主机名
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * 设置目标主机地址。
     *
     * @param ipAddress IP 或主机名
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * 获取 SSH 端口。
     *
     * @return 端口号，默认 22
     */
    public Integer getSshPort() {
        return sshPort;
    }

    /**
     * 设置 SSH 端口。
     *
     * @param sshPort 1～65535 的端口号
     */
    public void setSshPort(Integer sshPort) {
        this.sshPort = sshPort;
    }
}
