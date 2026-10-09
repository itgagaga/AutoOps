package com.zhiwei.superemployee.app.ops.mapper;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code ops_task} 表的 MyBatis-Plus 持久化实体。
 * <p>
 * 字段驼峰由 MyBatis-Plus {@code map-underscore-to-camel-case} 映射为数据库下划线列；
 * 布尔值使用 {@link Integer} 保存，以兼容达梦、Oracle、MySQL 和 Kingbase。
 * 该实体只负责数据库读写，不承载任务状态转换等业务规则。
 * </p>
 * @author yangdb
 */
@TableName("ops_task")
@Getter
@Setter
@NoArgsConstructor
public class OpsTaskEntity {
    /** 任务基础信息与应用部署配置。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;
    private String createdAt;
    private String createdBy;
    private String title;
    private String description;
    private String priority;
    private String hostId;
    private String hostName;
    private String workspacePath;
    private String frontendWorkspacePath;
    private String backendKind;
    private String clientKind;
    private String clientVerifyCommand;
    private String backendVerifyCommand;
    private String clientDeployPath;
    private String clientDistDir;
    private String dockerComposeFile;
    private String dockerImageName;
    private String repairEngine;
    private String applicationCode;
    private String repositoryUrl;
    private String ownerName;
    private Integer autoDeploy;
    private String deploymentPath;
    private String deploymentPaths;
    private String deploymentJar;
    private String deploymentTargets;

    /** 任务运行状态及当前关联故障。 */
    private Integer enabled;
    private String status;
    private String incidentId;
    private String latestSummary;

    /** 周期日志巡检配置。 */
    private Integer scheduleEnabled;
    private Integer pullIntervalMinutes;
    private Integer logRangeMinutes;
    private String logTemplate;
    private String logPath;
    private String logServiceName;
    private Integer autoAnalyze;

    /** 最近一次日志拉取结果。 */
    private String lastPullAt;
    private String lastPullFile;
    private Integer lastErrorCount;
    private String lastPullSummary;

}
