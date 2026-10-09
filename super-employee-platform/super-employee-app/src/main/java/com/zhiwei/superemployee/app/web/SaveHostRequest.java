package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 创建或更新主机资源的请求参数。
 * <p>
 * 对应 {@code ResourceController} 的 {@code POST /api/resources/hosts}
 * 与 {@code PUT /api/resources/hosts/{id}}，登记 SSH 运维目标主机及其安全约束。
 * </p>
 * @author yangdb
 */
public class SaveHostRequest {
    /** 主机编码，大写字母开头，仅含大写字母、数字与下划线。 */
    @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$")
    private String code;
    /** 主机展示名称，必填，最长 128。 */
    @NotBlank @Size(max = 128)
    private String name;
    /** 主机 IP 或主机名，必填，最长 128。 */
    @NotBlank @Size(max = 128)
    private String ipAddress;
    /** SSH 端口，范围 1～65535。 */
    @Min(1) @Max(65535)
    private Integer sshPort;
    /** 操作系统类型：LINUX、WINDOWS、UNIX、OTHER。 */
    @NotBlank @Pattern(regexp = "^(LINUX|WINDOWS|UNIX|OTHER)$")
    private String osType;
    /** 所属环境：DEV、TEST、UAT、PROD、OTHER。 */
    @NotBlank @Pattern(regexp = "^(DEV|TEST|UAT|PROD|OTHER)$")
    private String environment;
    /** 主机负责人，最长 128。 */
    @Size(max = 128)
    private String ownerName;
    /** 兼容旧请求体；服务端忽略，运行状态仅由连通性心跳写入。 */
    @Pattern(regexp = "^(ONLINE|OFFLINE|UNKNOWN|MAINTENANCE)$")
    private String status;
    /** 绑定的 SSH 凭据编号，最长 64。 */
    @Size(max = 64)
    private String credentialId;
    /** 备注说明，最长 500。 */
    @Size(max = 500)
    private String description;
    /** 允许读取的日志绝对路径列表。 */
    @Size(max = 50)
    private List<@Size(max = 512) String> allowedLogPaths = new ArrayList<>();
    /** 主机 SSH 密钥指纹列表，格式如 SHA256:... */
    @Size(max = 20)
    private List<@Size(max = 256) String> hostKeyFingerprints = new ArrayList<>();
    /** 是否启用该主机。 */
    private Boolean enabled;

    /**
     * 获取主机编码。
     *
     * @return 编码
     */
    public String getCode() { return code; }
    /**
     * 设置主机编码。
     *
     * @param v 大写编码
     */
    public void setCode(String v) { code = v; }
    /**
     * 获取展示名称。
     *
     * @return 名称
     */
    public String getName() { return name; }
    /**
     * 设置展示名称。
     *
     * @param v 名称
     */
    public void setName(String v) { name = v; }
    /**
     * 获取主机地址。
     *
     * @return IP 或主机名
     */
    public String getIpAddress() { return ipAddress; }
    /**
     * 设置主机地址。
     *
     * @param v IP 或主机名
     */
    public void setIpAddress(String v) { ipAddress = v; }
    /**
     * 获取 SSH 端口。
     *
     * @return 端口号
     */
    public Integer getSshPort() { return sshPort; }
    /**
     * 设置 SSH 端口。
     *
     * @param v 1～65535
     */
    public void setSshPort(Integer v) { sshPort = v; }
    /**
     * 获取操作系统类型。
     *
     * @return OS 类型
     */
    public String getOsType() { return osType; }
    /**
     * 设置操作系统类型。
     *
     * @param v LINUX/WINDOWS/UNIX/OTHER
     */
    public void setOsType(String v) { osType = v; }
    /**
     * 获取所属环境。
     *
     * @return 环境编码
     */
    public String getEnvironment() { return environment; }
    /**
     * 设置所属环境。
     *
     * @param v DEV/TEST/UAT/PROD/OTHER
     */
    public void setEnvironment(String v) { environment = v; }
    /**
     * 获取负责人。
     *
     * @return 负责人
     */
    public String getOwnerName() { return ownerName; }
    /**
     * 设置负责人。
     *
     * @param v 负责人
     */
    public void setOwnerName(String v) { ownerName = v; }
    /**
     * 获取兼容旧版状态字段。
     *
     * @return 状态字符串，服务端通常忽略
     */
    public String getStatus() { return status; }
    /**
     * 设置兼容旧版状态字段。
     *
     * @param v ONLINE/OFFLINE/UNKNOWN/MAINTENANCE
     */
    public void setStatus(String v) { status = v; }
    /**
     * 获取绑定凭据编号。
     *
     * @return 凭据 id
     */
    public String getCredentialId() { return credentialId; }
    /**
     * 设置绑定凭据编号。
     *
     * @param v 凭据 id
     */
    public void setCredentialId(String v) { credentialId = v; }
    /**
     * 获取备注说明。
     *
     * @return 描述
     */
    public String getDescription() { return description; }
    /**
     * 设置备注说明。
     *
     * @param v 描述
     */
    public void setDescription(String v) { description = v; }
    /**
     * 获取允许读取的日志路径列表。
     *
     * @return 路径列表
     */
    public List<String> getAllowedLogPaths() { return allowedLogPaths; }
    /**
     * 设置允许读取的日志路径列表；{@code null} 时置为空列表。
     *
     * @param v 路径列表
     */
    public void setAllowedLogPaths(List<String> v) { allowedLogPaths = v == null ? new ArrayList<>() : v; }
    /**
     * 获取主机密钥指纹列表。
     *
     * @return 指纹列表
     */
    public List<String> getHostKeyFingerprints() { return hostKeyFingerprints; }
    /**
     * 设置主机密钥指纹列表；{@code null} 时置为空列表。
     *
     * @param v 指纹列表
     */
    public void setHostKeyFingerprints(List<String> v) { hostKeyFingerprints = v == null ? new ArrayList<>() : v; }
    /**
     * 获取启用状态。
     *
     * @return 是否启用
     */
    public Boolean getEnabled() { return enabled; }
    /**
     * 设置启用状态。
     *
     * @param v 是否启用
     */
    public void setEnabled(Boolean v) { enabled = v; }

    /**
     * 将路径列表合并为仓储存储格式。
     *
     * @return 以 {@code |} 分隔的路径串；无有效项时返回 {@code null}
     */
    public String joinedAllowedLogPaths() {
        return join(allowedLogPaths);
    }

    /**
     * 将指纹列表合并为仓储存储格式。
     *
     * @return 以 {@code |} 分隔的指纹串；无有效项时返回 {@code null}
     */
    public String joinedHostKeyFingerprints() {
        return join(hostKeyFingerprints);
    }

    /**
     * 去重、去空白后用竖线拼接列表。
     *
     * @param values 原始列表
     * @return 拼接结果或 {@code null}
     */
    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .collect(Collectors.joining("|"));
        return joined.isBlank() ? null : joined;
    }
}
