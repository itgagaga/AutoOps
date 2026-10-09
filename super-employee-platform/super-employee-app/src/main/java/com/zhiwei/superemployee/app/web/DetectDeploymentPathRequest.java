package com.zhiwei.superemployee.app.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 在目标主机上识别应用实际部署路径的请求参数。
 * <p>
 * 对应 {@code OpsTaskController} 的 {@code POST /api/ops-tasks/deployment-paths/detect}，
 * 平台会按候选目录列表探测 JAR/PID/日志等文件，返回最可能的部署位置。
 * </p>
 *
 * @param hostId           资源中心主机编号，必填，最长 64
 * @param applicationCode  应用唯一编码，用于匹配部署目录下的制品与日志文件名
 * @param deploymentPaths  远端候选绝对路径列表，1～20 条，每项须以 {@code /} 开头
 * @author yangdb
 */
public record DetectDeploymentPathRequest(
        @NotBlank @Size(max = 64) String hostId,
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$") String applicationCode,
        @Size(min = 1, max = 20)
        List<@NotBlank @Size(max = 512) @Pattern(regexp = "^/[^\\r\\n]*$") String> deploymentPaths) {
}
