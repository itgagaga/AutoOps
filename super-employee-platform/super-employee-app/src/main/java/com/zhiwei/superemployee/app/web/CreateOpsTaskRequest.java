package com.zhiwei.superemployee.app.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 创建或更新应用运维任务的请求参数。
 * <p>
 * 同时承载应用身份、代码工作区、Java 部署策略和日志巡检策略。控制器完成基础格式校验，
 * 部署路径与自动部署之间的条件约束由 {@code OpsTaskService} 进行二次校验。
 * </p>
 * @author yangdb
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateOpsTaskRequest {
    /** 工作台展示名称，也是派生故障任务的标题。 */
    @NotBlank
    @Size(max = 512)
    private String title;

    /** 运维目标、已知现象和期望修复结果。 */
    @NotBlank
    @Size(max = 4000)
    private String description;

    /** 运维优先级，取值由 OPS_PRIORITY 字典约束。 */
    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,15}$")
    private String priority = "P2";

    /** 资源中心中已启用且已绑定 SSH 凭据的主机编号。 */
    @Size(max = 64)
    private String hostId;

    /** 后端服务器本地 Maven 工作区，供本地修复引擎与制品构建使用。 */
    @NotBlank
    @Size(max = 1024)
    private String workspacePath;

    /** 可选端侧/前端工程目录；须与后端工作区形成安全公共父目录。 */
    @Size(max = 1024)
    private String frontendWorkspacePath;

    /** 后端工程画像。 */
    @Pattern(regexp = "(?i)^(MAVEN|JAVA_WAR|GRADLE|NODE|GO|PYTHON|DOCKER)?$")
    @Size(max = 32)
    private String backendKind = "MAVEN";

    /** 端侧工程画像：NONE / WEB_NPM / UNI_APP / MP_WEIXIN_NATIVE。 */
    @Pattern(regexp = "(?i)^(NONE|WEB_NPM|UNI_APP|MP_WEIXIN_NATIVE)?$")
    @Size(max = 32)
    private String clientKind = "NONE";

    /** 端侧验证命令覆盖（如 npm run build:h5）；空则按端侧类型默认。 */
    @Size(max = 256)
    private String clientVerifyCommand;

    /** 后端验证命令覆盖；空则按后端类型默认。 */
    @Size(max = 256)
    private String backendVerifyCommand;

    /** Web 静态远端部署目录；空则不自动部署端侧。 */
    @Size(max = 1024)
    @Pattern(regexp = "^(/[^\\r\\n]*)?$")
    private String clientDeployPath;

    /** 本地端侧产物目录，默认 dist。 */
    @Size(max = 256)
    private String clientDistDir;

    /** 相对工作区的 compose 文件。 */
    @Size(max = 256)
    private String dockerComposeFile;

    /** Docker 镜像名；空则取 applicationCode。 */
    @Size(max = 256)
    private String dockerImageName;

    /** 本地修复引擎：auto / codex / cursor / claude；默认 auto（失败自动切换）。 */
    @Pattern(regexp = "(?i)^(auto|codex|cursor|claude)?$")
    @Size(max = 32)
    private String repairEngine = "auto";

    /** 应用唯一编码，同时用于部署目录下的 JAR、PID 和日志文件名。 */
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{1,63}$")
    private String applicationCode;

    /** 可选代码仓库地址，仅用于应用信息归档和分析上下文。 */
    @Size(max = 1024)
    private String repositoryUrl;

    /** 应用负责人，用于故障上下文和后续人工协同。 */
    @Size(max = 128)
    private String ownerName;

    /** 验证通过后是否通过受管 SSH 自动备份、替换并启动 Java 应用。 */
    private Boolean autoDeploy = Boolean.FALSE;

    /** 目标 Linux 主机绝对路径；开启自动部署时必填。 */
    @Size(max = 1024)
    @Pattern(regexp = "^(/[^\\r\\n]*)?$")
    private String deploymentPath;

    /** 供平台自动识别实际部署位置的远端候选目录。 */
    @Size(max = 20)
    private List<@Size(max = 512) @Pattern(regexp = "^/[^\\r\\n]*$") String> deploymentPaths;

    /** 歧义识别后绑定的远端 JAR 相对路径，例如 {@code current/app.jar}。 */
    @Size(max = 512)
    @Pattern(regexp = "^([A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*)?$")
    private String deploymentJar;

    /** 可多选的自动部署目标；优先于单 path/jar，自动部署时依次部署到全部目标。 */
    @Size(max = 20)
    private List<@Valid DeploymentTargetRequest> deploymentTargets;

    /** 是否按固定周期从目标主机拉取日志。 */
    private Boolean scheduleEnabled = Boolean.FALSE;

    /** 日志拉取调度间隔，单位为分钟。 */
    @Min(5)
    @Max(60)
    private Integer pullIntervalMinutes = 30;

    /** 每次查询向前覆盖的日志时间范围，单位为分钟。 */
    @Min(30)
    @Max(1440)
    private Integer logRangeMinutes = 30;

    /** 受控日志查询模板，禁止提交任意远程命令。 */
    @Pattern(regexp = "(?i)^(JOURNAL_LOGS|APP_LOG_FILE|JAVA_APPLICATION_LOGS)?$")
    private String logTemplate = "JOURNAL_LOGS";

    /** APP_LOG_FILE 模板使用的远程日志白名单路径。 */
    @Size(max = 1024)
    private String logPath;

    /** JAVA_APPLICATION_LOGS 模板使用的 systemd 服务名称。 */
    @Size(max = 128)
    private String logServiceName;

    /** 检测到异常后是否自动创建故障任务并调度 Agent。 */
    private Boolean autoAnalyze = Boolean.TRUE;

    /**
     * 单个自动部署绑定目标。
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class DeploymentTargetRequest {
        /** 远端部署绝对路径。 */
        @NotBlank
        @Size(max = 512)
        @Pattern(regexp = "^/[^\\r\\n]*$")
        private String path;

        /** 相对部署目录的制品路径（兼容旧字段 jar）。 */
        @Size(max = 512)
        @Pattern(regexp = "^([A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*)?$")
        private String jar;

        /** 相对部署目录的制品路径；优先于 jar。 */
        @Size(max = 512)
        @Pattern(regexp = "^([A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*)?$")
        private String artifact;
    }

}
