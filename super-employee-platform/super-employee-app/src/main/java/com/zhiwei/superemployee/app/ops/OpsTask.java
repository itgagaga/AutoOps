package com.zhiwei.superemployee.app.ops;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 运维任务：业务入口；启用后派生故障任务；可配置周期拉日志落盘并异常触发分析。 
 * @author yangdb
 */
@Getter
@Setter
@AllArgsConstructor
public class OpsTask {
    /** 不可变任务标识与创建时间，由新建构造方法生成或由持久层还原。 */
    private final String id;
    private final Instant createdAt;
    /** 创建任务的登录用户名，用于行级数据权限隔离。 */
    private String createdBy;

    /** 应用身份、目标主机及本地代码工作区。 */
    private String title;
    private String description;
    private String priority;
    private String hostId;
    private String hostName;
    private String workspacePath;
    /** 可选端侧/前端工程目录；与后端工作区组成一体修复沙箱。 */
    private String frontendWorkspacePath;
    /** 后端工程画像：MAVEN。 */
    private String backendKind;
    /** 端侧工程画像：NONE / WEB_NPM / UNI_APP / MP_WEIXIN_NATIVE。 */
    private String clientKind;
    /** 端侧验证命令覆盖；空则按 clientKind 默认。 */
    private String clientVerifyCommand;
    /** 后端验证命令覆盖；空则按 backendKind 默认。 */
    private String backendVerifyCommand;
    /** Web 静态远端部署目录；空则不自动部署端侧。 */
    private String clientDeployPath;
    /** 本地端侧产物目录，默认 dist。 */
    private String clientDistDir;
    /** 相对工作区的 compose 文件。 */
    private String dockerComposeFile;
    /** Docker 镜像名；空则取 applicationCode。 */
    private String dockerImageName;
    /** 本地修复引擎偏好：auto / codex / cursor / claude；空视为 auto。 */
    private String repairEngine;
    private String applicationCode;
    private String repositoryUrl;
    private String ownerName;

    /** Java 补丁部署策略；自动部署开启时至少绑定一个部署目标。 */
    private boolean autoDeploy;
    /** 主部署路径（兼容旧字段，取自 deploymentTargets 首项）。 */
    private String deploymentPath;
    private List<String> deploymentPaths;
    /** 主部署 JAR（兼容旧字段，取自 deploymentTargets 首项）。 */
    private String deploymentJar;
    /** 可多选的自动部署目标；自动部署时依次打包匹配并部署到全部目标。 */
    private List<DeploymentTarget> deploymentTargets;
    private boolean enabled;
    private OpsTaskStatus status;
    private String incidentId;
    private String latestSummary;

    /** 周期日志拉取及异常自动分析配置。 */
    private boolean scheduleEnabled;
    private int pullIntervalMinutes;
    private int logRangeMinutes;
    private String logTemplate;
    private String logPath;
    private String logServiceName;
    private boolean autoAnalyze;
    private Instant lastPullAt;
    private String lastPullFile;
    private int lastErrorCount;
    private String lastPullSummary;

    /**
     * 创建尚未启用的应用运维任务。
     * <p>初始化日志周期、日志范围、日志模板和自动分析默认值；应用信息及部署策略随后由服务层校验并写入。</p>
     *
     * @param title         应用运维任务标题
     * @param description   运维目标与故障现象
     * @param priority      运维优先级
     * @param hostId        目标主机编号
     * @param workspacePath 后端本地 Maven 工作区
     */
    public OpsTask(String title, String description, String priority, String hostId, String workspacePath) {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.hostId = hostId;
        this.workspacePath = workspacePath;
        this.backendKind = "MAVEN";
        this.clientKind = "NONE";
        this.repairEngine = "auto";
        this.enabled = false;
        this.status = OpsTaskStatus.CREATED;
        this.scheduleEnabled = false;
        this.pullIntervalMinutes = 30;
        this.logRangeMinutes = 30;
        this.logTemplate = "JOURNAL_LOGS";
        this.autoAnalyze = true;
        this.lastErrorCount = 0;
    }

}
