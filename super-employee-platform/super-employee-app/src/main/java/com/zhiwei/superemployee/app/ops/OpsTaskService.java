package com.zhiwei.superemployee.app.ops;

import com.zhiwei.superemployee.app.resource.ManagedHost;
import com.zhiwei.superemployee.app.resource.ResourceRepository;
import com.zhiwei.superemployee.app.runtime.RepairEngineLabels;
import com.zhiwei.superemployee.app.security.OpsDataPermissionService;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.repository.IncidentRepository;
import com.zhiwei.superemployee.core.workflow.IncidentWorkflow;
import com.zhiwei.superemployee.core.workspace.BackendWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.ClientWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.MavenWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.ProjectBackendKind;
import com.zhiwei.superemployee.core.workspace.ProjectClientKind;
import com.zhiwei.superemployee.core.workspace.RepairWorkspacePaths;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 应用运维任务的业务编排服务。
 * <p>
 * 负责应用配置与部署策略校验、任务持久化、并发协调、故障任务派生以及运维状态同步。
 * 创建操作只保存配置；启用或重启任务时才会拉取日志并调度 Agent，避免配置阶段产生副作用。
 * </p>
 * @author yangdb
 */
@Service
@RequiredArgsConstructor
public class OpsTaskService {
    private static final Set<Integer> ALLOWED_PULL_INTERVALS = Set.of(5, 10, 30, 60);
    private static final Set<Integer> ALLOWED_LOG_RANGES = Set.of(30, 60, 120, 360, 720, 1440);
    private static final long DUPLICATE_CREATE_GUARD_MILLIS = 5000L;
    private final Map<String, Long> recentCreates = new ConcurrentHashMap<>();
    private final OpsTaskRepository opsTasks;
    private final IncidentWorkflow workflow;
    private final IncidentRepository incidents;
    private final ObjectProvider<ResourceRepository> resources;
    private final OpsLogPullService logPullService;
    private final DeploymentPathDiscoveryService deploymentPathDiscovery;
    private final OpsDataPermissionService dataPermission;

    /**
     * 结束上一轮分析并基于最新日志创建新一轮分析。
     *
     * @param id 主键 ID
     * @return 响应数据 Map
     */
    public Map<String, Object> restartAnalysis(String id) {
        dataPermission.requireOpsTask(id);
        logPullService.restartAnalysis(id);
        return get(id);
    }

    /**
     * 创建运维任务（不启动分析）；须配置含 Maven 项目的代码工作区。
     *
     * @param title 标题
     * @param description 描述
     * @param priority 优先级
     * @param hostId 主机 ID
     * @param workspacePath 工作区路径
     * @param applicationCode 应用编码
     * @param repositoryUrl 仓库地址
     * @param ownerName 负责人
     * @param autoDeploy 是否自动部署
     * @param deploymentPath 部署路径
     * @param deploymentPaths 候选部署目录
     * @param scheduleEnabled 是否开启定时拉取
     * @param pullIntervalMinutes 拉取间隔分钟
     * @param logRangeMinutes 日志窗口分钟
     * @param logTemplate 日志模板
     * @param logPath 远端日志路径
     * @param logServiceName 日志服务名
     * @param autoAnalyze 是否自动分析
     * @return 响应数据 Map
     */
    public Map<String, Object> create(String title, String description, String priority,
                                      String hostId, String workspacePath, String frontendWorkspacePath,
                                      String backendKind, String clientKind, String clientVerifyCommand,
                                      String backendVerifyCommand, String clientDeployPath,
                                      String clientDistDir, String dockerComposeFile, String dockerImageName,
                                      String repairEngine,
                                      String applicationCode, String repositoryUrl, String ownerName,
                                      boolean autoDeploy, String deploymentPath, List<String> deploymentPaths,
                                      String deploymentJar, List<DeploymentTarget> deploymentTargets,
                                      boolean scheduleEnabled, int pullIntervalMinutes,
                                      int logRangeMinutes,
                                      String logTemplate, String logPath, String logServiceName,
                                      boolean autoAnalyze) {
        String trimmedHost = blankToNull(hostId);
        ManagedHost host = resolveHost(trimmedHost);
        ProjectProfile profile = resolveProjectProfile(backendKind, clientKind, clientVerifyCommand,
                backendVerifyCommand, workspacePath, frontendWorkspacePath);
        Path workspace = profile.backendWorkspace();
        int normalizedInterval = pullIntervalMinutes <= 0 ? 30 : pullIntervalMinutes;
        if (!ALLOWED_PULL_INTERVALS.contains(normalizedInterval)) {
            throw new IllegalArgumentException("日志拉取周期仅支持5分钟、10分钟、30分钟或1小时");
        }
        int normalizedRange = logRangeMinutes <= 0 ? 30 : logRangeMinutes;
        if (!ALLOWED_LOG_RANGES.contains(normalizedRange)) {
            throw new IllegalArgumentException("日志拉取范围仅支持半小时、1小时、2小时、6小时、12小时或24小时");
        }

        OpsTask ops = new OpsTask(title.trim(), description.trim(), priority, trimmedHost, workspace.toString());
        ops.setCreatedBy(dataPermission.currentUsername());
        if (host != null) {
            ops.setHostName(host.getName());
        }
        applyProjectProfile(ops, profile);
        applyHybridDeployFields(ops, clientDeployPath, clientDistDir, dockerComposeFile, dockerImageName);
        ops.setRepairEngine(normalizeRepairEngine(repairEngine));
        ops.setScheduleEnabled(scheduleEnabled);
        ops.setPullIntervalMinutes(normalizedInterval);
        ops.setLogRangeMinutes(normalizedRange);
        ops.setLogTemplate(normalizeTemplate(logTemplate));
        ops.setLogPath(blankToNull(logPath));
        ops.setLogServiceName(blankToNull(logServiceName));
        ops.setAutoAnalyze(autoAnalyze);
        applyApplicationConfig(
                ops, applicationCode, repositoryUrl, ownerName, autoDeploy, deploymentPath, deploymentPaths,
                deploymentJar, deploymentTargets);
        validateLogConfig(ops);
        guardDuplicateCreate(title, trimmedHost, workspace);
        ops.setLatestSummary(scheduleEnabled
                ? "已创建；启用后启动分析，并按间隔定时拉日志落盘"
                : "已创建，启用后将启动 Agent 分析");
        opsTasks.save(ops);
        return response(ops, null);
    }

    /**
     * 使用标题、主机和工作区指纹阻止浏览器重复提交产生相同任务。
     *
     * @param title 标题
     * @param hostId 主机 ID
     * @param workspace 工作区
     */
    private void guardDuplicateCreate(String title, String hostId, Path workspace) {
        long now = System.currentTimeMillis();
        String fingerprint = title.trim() + '\n' + String.valueOf(hostId) + '\n' + workspace;
        Long previous = recentCreates.put(fingerprint, now);
        if (previous != null && now - previous < DUPLICATE_CREATE_GUARD_MILLIS) {
            throw new IllegalStateException("任务创建过于频繁，请稍后再试");
        }
        recentCreates.entrySet().removeIf(entry -> now - entry.getValue() > DUPLICATE_CREATE_GUARD_MILLIS);
    }

    /**
     * 更新任务配置，并立即结束上一轮、按新配置重新拉取日志和启动分析。
     *
     * @param id 主键 ID
     * @param title 标题
     * @param description 描述
     * @param priority 优先级
     * @param hostId 主机 ID
     * @param workspacePath 工作区路径
     * @param applicationCode 应用编码
     * @param repositoryUrl 仓库地址
     * @param ownerName 负责人
     * @param autoDeploy 是否自动部署
     * @param deploymentPath 部署路径
     * @param deploymentPaths 候选部署目录
     * @param scheduleEnabled 是否开启定时拉取
     * @param pullIntervalMinutes 拉取间隔分钟
     * @param logRangeMinutes 日志窗口分钟
     * @param logTemplate 日志模板
     * @param logPath 远端日志路径
     * @param logServiceName 日志服务名
     * @param autoAnalyze 是否自动分析
     * @return 响应数据 Map
     */
    public Map<String, Object> updateAndRestart(
            String id, String title, String description, String priority,
            String hostId, String workspacePath, String frontendWorkspacePath,
            String backendKind, String clientKind, String clientVerifyCommand,
            String backendVerifyCommand, String clientDeployPath,
            String clientDistDir, String dockerComposeFile, String dockerImageName,
            String repairEngine,
            String applicationCode, String repositoryUrl, String ownerName,
            boolean autoDeploy, String deploymentPath, List<String> deploymentPaths, String deploymentJar,
            List<DeploymentTarget> deploymentTargets,
            boolean scheduleEnabled,
            int pullIntervalMinutes, int logRangeMinutes, String logTemplate, String logPath,
            String logServiceName, boolean autoAnalyze) {
        return logPullService.coordinate(id, () -> updateAndRestartLocked(
                id, title, description, priority, hostId, workspacePath, frontendWorkspacePath,
                backendKind, clientKind, clientVerifyCommand,
                backendVerifyCommand, clientDeployPath, clientDistDir, dockerComposeFile, dockerImageName,
                repairEngine,
                applicationCode, repositoryUrl, ownerName, autoDeploy, deploymentPath, deploymentPaths,
                deploymentJar, deploymentTargets, scheduleEnabled,
                pullIntervalMinutes, logRangeMinutes, logTemplate, logPath, logServiceName, autoAnalyze));
    }

    /**
     * 在任务级互斥锁内更新完整配置，并基于新配置重新启动分析。
     *
     * @param id 主键 ID
     * @param title 标题
     * @param description 描述
     * @param priority 优先级
     * @param hostId 主机 ID
     * @param workspacePath 工作区路径
     * @param applicationCode 应用编码
     * @param repositoryUrl 仓库地址
     * @param ownerName 负责人
     * @param autoDeploy 是否自动部署
     * @param deploymentPath 部署路径
     * @param deploymentPaths 候选部署目录
     * @param deploymentJar 绑定的远端 JAR 相对路径
     * @param deploymentTargets 可多选的自动部署目标
     * @param scheduleEnabled 是否开启定时拉取
     * @param pullIntervalMinutes 拉取间隔分钟
     * @param logRangeMinutes 日志窗口分钟
     * @param logTemplate 日志模板
     * @param logPath 远端日志路径
     * @param logServiceName 日志服务名
     * @param autoAnalyze 是否自动分析
     * @return 响应数据 Map
     */
    private Map<String, Object> updateAndRestartLocked(
            String id, String title, String description, String priority,
            String hostId, String workspacePath, String frontendWorkspacePath,
            String backendKind, String clientKind, String clientVerifyCommand,
            String backendVerifyCommand, String clientDeployPath,
            String clientDistDir, String dockerComposeFile, String dockerImageName,
            String repairEngine,
            String applicationCode, String repositoryUrl, String ownerName,
            boolean autoDeploy, String deploymentPath, List<String> deploymentPaths, String deploymentJar,
            List<DeploymentTarget> deploymentTargets,
            boolean scheduleEnabled,
            int pullIntervalMinutes, int logRangeMinutes, String logTemplate, String logPath,
            String logServiceName, boolean autoAnalyze) {
        OpsTask ops = dataPermission.requireOpsTask(id);
        String trimmedHost = blankToNull(hostId);
        ManagedHost host = resolveHost(trimmedHost);
        ProjectProfile profile = resolveProjectProfile(backendKind, clientKind, clientVerifyCommand,
                backendVerifyCommand, workspacePath, frontendWorkspacePath);
        Path workspace = profile.backendWorkspace();
        int normalizedInterval = pullIntervalMinutes <= 0 ? 30 : pullIntervalMinutes;
        if (!ALLOWED_PULL_INTERVALS.contains(normalizedInterval)) {
            throw new IllegalArgumentException("日志拉取周期仅支持5分钟、10分钟、30分钟或1小时");
        }
        int normalizedRange = logRangeMinutes <= 0 ? 30 : logRangeMinutes;
        if (!ALLOWED_LOG_RANGES.contains(normalizedRange)) {
            throw new IllegalArgumentException("日志拉取范围仅支持半小时、1小时、2小时、6小时、12小时或24小时");
        }

        ops.setTitle(title.trim());
        ops.setDescription(description.trim());
        ops.setPriority(priority);
        ops.setHostId(trimmedHost);
        ops.setHostName(host == null ? null : host.getName());
        ops.setWorkspacePath(workspace.toString());
        applyProjectProfile(ops, profile);
        applyHybridDeployFields(ops, clientDeployPath, clientDistDir, dockerComposeFile, dockerImageName);
        ops.setRepairEngine(normalizeRepairEngine(repairEngine));
        applyApplicationConfig(
                ops, applicationCode, repositoryUrl, ownerName, autoDeploy, deploymentPath, deploymentPaths,
                deploymentJar, deploymentTargets);
        ops.setScheduleEnabled(scheduleEnabled);
        ops.setPullIntervalMinutes(normalizedInterval);
        ops.setLogRangeMinutes(normalizedRange);
        ops.setLogTemplate(normalizeTemplate(logTemplate));
        ops.setLogPath(blankToNull(logPath));
        ops.setLogServiceName(blankToNull(logServiceName));
        ops.setAutoAnalyze(autoAnalyze);
        ops.setEnabled(true);
        validateLogConfig(ops);
        opsTasks.update(ops);

        logPullService.restartAnalysis(id);
        return get(id);
    }

    /**
     * 启用或停用；首次启用时派生故障任务并启动分析。
     *
     * @param id 主键 ID
     * @param enabled 是否启用
     * @return 响应数据 Map
     */
    public Map<String, Object> setEnabled(String id, boolean enabled) {
        return logPullService.coordinate(id, () -> setEnabledLocked(id, enabled));
    }

    /**
     * 在任务级互斥锁内切换启停状态，首次启用时按调度策略派生故障任务。
     *
     * @param id 主键 ID
     * @param enabled 是否启用
     * @return 响应数据 Map
     */
    private Map<String, Object> setEnabledLocked(String id, boolean enabled) {
        OpsTask ops = dataPermission.requireOpsTask(id);
        if (!enabled) {
            ops.setEnabled(false);
            if (ops.getIncidentId() == null) {
                ops.setLatestSummary("已停用，启用后将启动 Agent 分析");
            }
            opsTasks.update(ops);
            IncidentTask incident = ops.getIncidentId() == null ? null : workflow.get(ops.getIncidentId());
            return response(ops, incident);
        }

        resolveProjectProfile(ops.getBackendKind(), ops.getClientKind(), ops.getClientVerifyCommand(),
                ops.getBackendVerifyCommand(), ops.getWorkspacePath(), ops.getFrontendWorkspacePath());
        resolveHost(ops.getHostId());
        validateLogConfig(ops);
        ops.setEnabled(true);
        opsTasks.update(ops);

        if (ops.getIncidentId() == null) {
            if (ops.isScheduleEnabled()) {
                ops.setStatus(OpsTaskStatus.CREATED);
                ops.setLatestSummary("已启用，等待首次定时拉取日志；检测到异常后将创建运维分支并调用 "
                        + RepairEngineLabels.cliName(ops.getRepairEngine()));
                opsTasks.update(ops);
                return response(ops, null);
            }
            return dispatch(ops);
        }
        IncidentTask incident = workflow.get(ops.getIncidentId());
        if (incident != null) {
            syncOpsFromIncident(ops, incident);
        }
        return response(ops, incident);
    }

    /**
     * 查询当前用户可见的运维任务列表（不含故障大字段）。
     *
     * @return 任务响应 Map 列表
     */
    public List<Map<String, Object>> list() {
        // 运维状态在 Agent/审批/日志流程中已实时回写。列表不再对每行执行一次故障 CLOB 查询，
        // 避免典型 N+1 查询；完整故障仅在详情接口按需读取。
        return dataPermission.visibleOpsTasks().stream()
                .map(task -> response(task, null, false))
                .toList();
    }

    /**
     * 查询单个运维任务详情，并同步关联故障状态。
     *
     * @param id 任务 ID
     * @return 含故障详情的任务响应
     * @throws IllegalArgumentException 任务不存在或无权访问
     */
    public Map<String, Object> get(String id) {
        OpsTask task = dataPermission.requireOpsTask(id);
        IncidentTask incident = task.getIncidentId() == null ? null : workflow.get(task.getIncidentId());
        if (incident != null) {
            syncOpsFromIncident(task, incident);
        }
        return response(task, incident, true);
    }

    /**
     * 删除运维任务；关联故障任务保留在故障库中，工作台不再展示。
     *
     * @param id 主键 ID
     */
    public void delete(String id) {
        logPullService.coordinate(id, () -> {
            OpsTask task = dataPermission.requireOpsTask(id);
            if (logPullService.isTaskRunning(id)) {
                throw new IllegalStateException("任务正在拉取日志，暂时不能删除，请稍后重试");
            }
            task.setEnabled(false);
            opsTasks.update(task);
            if (!opsTasks.deleteById(task.getId())) {
                throw new IllegalArgumentException("运维任务不存在");
            }
            return null;
        });
    }

    /**
     * 将运维任务配置复制到故障上下文，并启动一次 Agent 分析会话。
     *
     * @param ops 运维任务
     * @return 响应数据 Map
     */
    private Map<String, Object> dispatch(OpsTask ops) {
        ManagedHost host = resolveHost(ops.getHostId());
        ProjectProfile profile = resolveProjectProfile(ops.getBackendKind(), ops.getClientKind(),
                ops.getClientVerifyCommand(), ops.getBackendVerifyCommand(),
                ops.getWorkspacePath(), ops.getFrontendWorkspacePath());

        IncidentTask incident = workflow.create(ops.getTitle(), buildIncidentDescription(ops, host), ops.getPriority());
        incident.setCreatedBy(ops.getCreatedBy());
        incident.getContext().put("source", "OPS_TASK");
        incident.getContext().put("opsTaskId", ops.getId());
        putProjectProfileContext(incident, profile);
        putHybridDeployContext(incident, ops);
        incident.getContext().put("repairEnginePreference",
                normalizeRepairEngine(ops.getRepairEngine()));
        incident.getContext().put("applicationCode", ops.getApplicationCode());
        incident.getContext().put("applicationType", profile.backendKind().applicationType());
        incident.getContext().put("repositoryUrl", ops.getRepositoryUrl());
        incident.getContext().put("ownerName", ops.getOwnerName());
        incident.getContext().put("autoDeploy", ops.isAutoDeploy());
        incident.getContext().put("deploymentPath", ops.getDeploymentPath());
        incident.getContext().put("deploymentPaths", ops.getDeploymentPaths());
        incident.getContext().put("deploymentJar", ops.getDeploymentJar());
        incident.getContext().put("deploymentTargets", toTargetMaps(ops.getDeploymentTargets()));
        if (ops.getHostId() != null) {
            incident.getContext().put("preferredHostId", ops.getHostId());
            if (host != null) {
                incident.getContext().put("preferredHostName", host.getName());
                incident.getContext().put("preferredHostIp", host.getIpAddress());
            }
        }
        incidents.save(incident);

        ops.setIncidentId(incident.getId());
        ops.setStatus(OpsTaskStatus.ANALYZING);
        ops.setLatestSummary("已启用，正在调度 Agent 分析");
        opsTasks.update(ops);

        try {
            IncidentTask running = workflow.run(incident.getId());
            syncOpsFromIncident(ops, running);
        }
        catch (RuntimeException ex) {
            ops.setStatus(OpsTaskStatus.FAILED);
            ops.setLatestSummary("启动分析失败：" + ex.getMessage());
            opsTasks.update(ops);
            throw ex;
        }

        return response(ops, workflow.get(incident.getId()));
    }

    /**
     * 从资源中心解析已启用主机，避免任务引用不存在或停用的资源。
     *
     * @param hostId 主机 ID
     * @return 从资源中心解析已启用主机，避免任务引用不存在或停用的资源
     */
    private ManagedHost resolveHost(String hostId) {
        if (hostId == null) {
            return null;
        }
        ResourceRepository repository = resources.getIfAvailable();
        if (repository == null) {
            throw new IllegalStateException("资源中心未启用，无法关联主机");
        }
        ManagedHost host = repository.findHost(hostId)
                .orElseThrow(() -> new IllegalArgumentException("目标主机不存在：" + hostId));
        if (!host.isEnabled()) {
            throw new IllegalArgumentException("目标主机未启用，请先在资源中心启用主机：" + host.getName());
        }
        return host;
    }

    /**
     * 校验日志模板所需的主机、白名单路径和 systemd 服务参数。
     *
     * @param ops 运维任务
     */
    private void validateLogConfig(OpsTask ops) {
        if (!ops.isScheduleEnabled()) {
            return;
        }
        if (ops.getHostId() == null || ops.getHostId().isBlank()) {
            throw new IllegalArgumentException("开启定时拉日志时必须指定目标主机");
        }
        String template = normalizeTemplate(ops.getLogTemplate());
        ops.setLogTemplate(template);
        if ("APP_LOG_FILE".equals(template) && (ops.getLogPath() == null || ops.getLogPath().isBlank())) {
            throw new IllegalArgumentException("APP_LOG_FILE 需要填写远端日志路径（主机白名单内）");
        }
        if ("JAVA_APPLICATION_LOGS".equals(template)
                && (ops.getLogServiceName() == null || ops.getLogServiceName().isBlank())) {
            throw new IllegalArgumentException("JAVA_APPLICATION_LOGS 需要填写服务名");
        }
    }

    /**
     * 将故障状态和最新摘要幂等同步回运维任务列表。
     *
     * @param ops 运维任务
     * @param incident 故障任务
     */
    private void syncOpsFromIncident(OpsTask ops, IncidentTask incident) {
        if (incident == null) {
            return;
        }
        OpsTaskStatus expectedStatus = switch (incident.getStatus()) {
            case APPROVAL_PENDING -> OpsTaskStatus.WAITING_APPROVAL;
            case RESOLVED -> OpsTaskStatus.COMPLETED;
            case FAILED, REJECTED, CANCELLED, TIMEOUT -> OpsTaskStatus.FAILED;
            default -> OpsTaskStatus.ANALYZING;
        };
        String expectedSummary = incident.getLatestSummary();
        if (ops.getStatus() == expectedStatus
                && java.util.Objects.equals(ops.getLatestSummary(), expectedSummary)) {
            return;
        }
        ops.setLatestSummary(expectedSummary);
        ops.setStatus(expectedStatus);
        opsTasks.update(ops);
    }

    /**
     * 构建提供给 Agent 的应用、部署、日志和主机约束说明。
     *
     * @param ops 运维任务
     * @param host 主机
     * @return 构建提供给 Agent 的应用、部署、日志和主机约束说明
     */
    private String buildIncidentDescription(OpsTask ops, ManagedHost host) {
        StringBuilder sb = new StringBuilder();
        sb.append("【运维任务】\n");
        sb.append(ops.getDescription());
        if (ops.getApplicationCode() != null) {
            sb.append("\n\n应用：").append(ops.getApplicationCode())
                    .append("（").append(ProjectBackendKind.from(ops.getBackendKind()).displayName()).append("）");
            if (ops.getOwnerName() != null) sb.append("，负责人：").append(ops.getOwnerName());
        }
        if (ops.getWorkspacePath() != null && !ops.getWorkspacePath().isBlank()) {
            sb.append("\n\n代码工作区：").append(ops.getWorkspacePath());
        }
        sb.append("\n补丁部署：").append(ops.isAutoDeploy()
                ? "验证通过后从候选目录 " + ops.getDeploymentPaths()
                    + " 中重新识别，并部署到 " + ops.getDeploymentPath()
                : "生成备份、启动和一键部署脚本，由人工部署验证");
        if (ops.isScheduleEnabled()) {
            sb.append("\n\n已开启定时拉日志：每 ").append(ops.getPullIntervalMinutes())
                    .append(" 分钟拉取最近 ").append(formatMinutes(ops.getLogRangeMinutes()))
                    .append(" 的日志，模板 ").append(ops.getLogTemplate())
                    .append("；日志落盘于工作区 .zhiwei-logs/ 目录。");
        }
        if (host != null) {
            sb.append("\n\n目标主机：").append(host.getName())
                    .append("（ID=").append(host.getId())
                    .append("，IP=").append(host.getIpAddress()).append("）")
                    .append("\n请优先对该主机执行只读诊断，禁止虚构主机或资源。");
        }
        else {
            sb.append("\n\n未指定目标主机：若证据不足请明确说明，不得虚构主机别名或配置。");
        }
        return sb.toString();
    }

    /**
     * 构建包含完整故障详情的任务响应。
     *
     * @param task 运维/故障任务
     * @param incident 故障任务
     * @return 响应数据 Map
     */
    private Map<String, Object> response(OpsTask task, IncidentTask incident) {
        return response(task, incident, true);
    }

    /**
     * 构建任务响应；列表查询可关闭故障大字段以避免 N+1 与大对象传输。
     *
     * @param task 运维/故障任务
     * @param incident 故障任务
     * @param includeIncident includeIncident
     * @return 响应数据 Map
     */
    private Map<String, Object> response(OpsTask task, IncidentTask incident, boolean includeIncident) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", task.getId());
        result.put("createdAt", task.getCreatedAt().toString());
        result.put("createdBy", task.getCreatedBy());
        result.put("title", task.getTitle());
        result.put("description", task.getDescription());
        result.put("priority", task.getPriority());
        result.put("hostId", task.getHostId());
        result.put("hostName", task.getHostName());
        result.put("workspacePath", task.getWorkspacePath());
        result.put("frontendWorkspacePath", task.getFrontendWorkspacePath());
        result.put("backendKind", normalizeBackendKind(task.getBackendKind()));
        result.put("clientKind", normalizeClientKind(task.getClientKind(), task.getFrontendWorkspacePath()));
        result.put("clientVerifyCommand", task.getClientVerifyCommand());
        result.put("backendVerifyCommand", task.getBackendVerifyCommand());
        result.put("clientDeployPath", task.getClientDeployPath());
        result.put("clientDistDir", task.getClientDistDir() == null || task.getClientDistDir().isBlank()
                ? "dist" : task.getClientDistDir());
        result.put("dockerComposeFile", task.getDockerComposeFile());
        result.put("dockerImageName", task.getDockerImageName());
        result.put("repairEngine", normalizeRepairEngine(task.getRepairEngine()));
        result.put("applicationCode", task.getApplicationCode());
        result.put("applicationType", ProjectBackendKind.from(task.getBackendKind()).applicationType());
        result.put("repositoryUrl", task.getRepositoryUrl());
        result.put("ownerName", task.getOwnerName());
        result.put("autoDeploy", task.isAutoDeploy());
        result.put("deploymentPath", task.getDeploymentPath());
        result.put("deploymentPaths", task.getDeploymentPaths());
        result.put("deploymentJar", task.getDeploymentJar());
        result.put("deploymentTargets", toTargetMaps(task.getDeploymentTargets()));
        result.put("enabled", task.isEnabled());
        result.put("status", task.getStatus().name());
        result.put("incidentId", task.getIncidentId());
        result.put("latestSummary", task.getLatestSummary());
        result.put("scheduleEnabled", task.isScheduleEnabled());
        result.put("pullIntervalMinutes", task.getPullIntervalMinutes());
        result.put("logRangeMinutes", task.getLogRangeMinutes());
        result.put("logTemplate", task.getLogTemplate());
        result.put("logPath", task.getLogPath());
        result.put("logServiceName", task.getLogServiceName());
        result.put("autoAnalyze", task.isAutoAnalyze());
        result.put("lastPullAt", task.getLastPullAt() == null ? null : task.getLastPullAt().toString());
        result.put("lastPullFile", task.getLastPullFile());
        result.put("lastErrorCount", task.getLastErrorCount());
        result.put("lastPullSummary", task.getLastPullSummary());
        if (incident != null) {
            result.put("incidentStatus", incident.getStatus().name());
            if (includeIncident) {
                result.put("incident", incident);
            }
        }
        else if (task.getIncidentId() != null) {
            result.put("incidentStatus", switch (task.getStatus()) {
                case ANALYZING -> "RUNNING";
                case WAITING_APPROVAL -> "APPROVAL_PENDING";
                case COMPLETED -> "RESOLVED";
                case FAILED -> "FAILED";
                default -> null;
            });
        }
        return result;
    }

    /**
     * 将日志分钟范围转换为面向用户的中文描述。
     *
     * @param minutes 分钟数
     * @return 将日志分钟范围转换为面向用户的中文描述
     */
    private String formatMinutes(int minutes) {
        if (minutes == 30) return "半小时";
        if (minutes % 60 == 0) return (minutes / 60) + "小时";
        return minutes + "分钟";
    }

    /**
     * 规范化并限制日志模板，禁止任意命令模板进入 SSH 诊断网关。
     *
     * @param raw 原始字符串
     * @return 规范化并限制日志模板，禁止任意命令模板进入 SSH 诊断网关
     */
    private String normalizeTemplate(String raw) {
        String value = raw == null || raw.isBlank() ? "JOURNAL_LOGS" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "JOURNAL_LOGS", "APP_LOG_FILE", "JAVA_APPLICATION_LOGS" -> value;
            default -> throw new IllegalArgumentException("不支持的日志模板：" + value);
        };
    }

    /**
     * 规范化本地修复引擎偏好；空值视为 auto。
     */
    static String normalizeRepairEngine(String raw) {
        if (raw == null || raw.isBlank()) {
            return "auto";
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "auto", "codex", "cursor", "claude" -> value;
            default -> throw new IllegalArgumentException("修复引擎仅支持 auto、codex、cursor 或 claude");
        };
    }

    /**
     * 将空白文本统一为 null，减少持久化后的歧义。
     *
     * @param value 取值
     * @return 将空白文本统一为 null，减少持久化后的歧义
     */
    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 校验并应用任务内的应用身份和 Java 部署配置。
     *
     * @param task 运维/故障任务
     * @param applicationCode 应用编码
     * @param repositoryUrl 仓库地址
     * @param ownerName 负责人
     * @param autoDeploy 是否自动部署
     * @param deploymentPath 部署路径
     * @param deploymentPaths 候选部署目录
     * @param deploymentJar 单目标 JAR（兼容旧请求）
     * @param deploymentTargets 多目标绑定列表
     */
    private void applyApplicationConfig(OpsTask task, String applicationCode, String repositoryUrl,
                                        String ownerName, boolean autoDeploy, String deploymentPath,
                                        List<String> deploymentPaths, String deploymentJar,
                                        List<DeploymentTarget> deploymentTargets) {
        String code = blankToNull(applicationCode);
        if (code == null || !code.matches("[A-Za-z0-9][A-Za-z0-9._-]{1,63}")) {
            throw new IllegalArgumentException("应用编码仅支持2至64位字母、数字、点、下划线和短横线");
        }
        ProjectBackendKind backendKind = ProjectBackendKind.from(task.getBackendKind());
        List<DeploymentTarget> requested = normalizeTargets(
                deploymentTargets, deploymentPath, deploymentJar, backendKind);
        List<String> rawPaths = deploymentPaths == null || deploymentPaths.isEmpty()
                ? requested.stream().map(DeploymentTarget::path).distinct().toList()
                : deploymentPaths;
        if (rawPaths.isEmpty() && blankToNull(deploymentPath) != null) {
            rawPaths = List.of(deploymentPath);
        }
        List<String> candidates = deploymentPathDiscovery.normalizeCandidates(rawPaths);
        if (candidates.stream().anyMatch("/"::equals)) {
            throw new IllegalArgumentException("不能将服务器根目录作为部署候选目录");
        }
        int serializedLength = candidates.stream().mapToInt(String::length).sum() + candidates.size() * 4;
        if (serializedLength > 3900) {
            throw new IllegalArgumentException("候选部署目录总长度过长，请减少目录数量");
        }
        List<DeploymentTarget> bound = new ArrayList<>();
        for (DeploymentTarget item : requested) {
            if (!candidates.contains(item.path())) {
                throw new IllegalArgumentException("绑定的部署路径不在候选目录中：" + item.path());
            }
            bound.add(item);
        }
        if (bound.size() > 20) {
            throw new IllegalArgumentException("最多绑定 20 个自动部署目标");
        }
        int targetJsonLength = bound.stream()
                .mapToInt(item -> item.path().length() + (item.jar() == null ? 0 : item.jar().length()) + 24)
                .sum();
        if (targetJsonLength > 3900) {
            throw new IllegalArgumentException("部署目标总长度过长，请减少绑定数量");
        }

        if (autoDeploy) {
            if (task.getHostId() == null || task.getHostId().isBlank()) {
                throw new IllegalArgumentException("开启自动部署时必须选择目标主机");
            }
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("开启自动部署时必须选择候选部署目录");
            }
            if (bound.isEmpty()) {
                if (backendKind.isJavaPackaged()) {
                    DeploymentPathDiscoveryService.DetectionResult detection =
                            deploymentPathDiscovery.detect(task.getHostId(), code, candidates);
                    if (detection.detected()) {
                        String path = detection.deploymentPath();
                        bound = List.of(new DeploymentTarget(path, preferredArtifact(detection, path, code, backendKind)));
                    } else if (backendKind.isJavaWar()) {
                        bound = List.of(new DeploymentTarget(candidates.get(0), code + ".war"));
                    } else {
                        throw new IllegalArgumentException(detection.message());
                    }
                } else {
                    String artifact = switch (backendKind) {
                        case GO -> code;
                        case NODE, PYTHON -> "release.zip";
                        case DOCKER -> null;
                        default -> code;
                    };
                    bound = List.of(new DeploymentTarget(candidates.get(0), artifact));
                }
            } else {
                LinkedHashSet<String> seen = new LinkedHashSet<>();
                DeploymentPathDiscoveryService.DetectionResult detection = backendKind.isJavaJar()
                        ? deploymentPathDiscovery.detect(task.getHostId(), code, candidates)
                        : null;
                for (DeploymentTarget item : bound) {
                    String key = item.path() + "\n" + item.jar();
                    if (!seen.add(key)) {
                        throw new IllegalArgumentException("重复绑定了相同的部署目标：" + item.path() + " / " + item.jar());
                    }
                    if (backendKind.isJavaJar() && detection != null) {
                        requireJarOnPath(detection, item.path(), item.jar());
                    }
                }
            }
        } else if (!bound.isEmpty() && task.getHostId() != null && !task.getHostId().isBlank()
                && !candidates.isEmpty() && backendKind.isJavaJar()) {
            DeploymentPathDiscoveryService.DetectionResult detection =
                    deploymentPathDiscovery.detect(task.getHostId(), code, candidates);
            for (DeploymentTarget item : bound) {
                requireJarOnPath(detection, item.path(), item.jar());
            }
        }

        DeploymentTarget primary = bound.isEmpty() ? null : bound.get(0);
        task.setApplicationCode(code);
        task.setRepositoryUrl(blankToNull(repositoryUrl));
        task.setOwnerName(blankToNull(ownerName));
        task.setAutoDeploy(autoDeploy);
        task.setDeploymentPath(primary == null ? null : primary.path());
        task.setDeploymentPaths(candidates);
        task.setDeploymentJar(primary == null ? null : primary.jar());
        task.setDeploymentTargets(List.copyOf(bound));
    }

    /**
     * 合并多目标请求与旧版单 path/jar 字段。
     */
    private List<DeploymentTarget> normalizeTargets(
            List<DeploymentTarget> deploymentTargets, String deploymentPath, String deploymentJar) {
        return normalizeTargets(deploymentTargets, deploymentPath, deploymentJar, ProjectBackendKind.MAVEN);
    }

    private List<DeploymentTarget> normalizeTargets(
            List<DeploymentTarget> deploymentTargets, String deploymentPath, String deploymentJar,
            ProjectBackendKind backendKind) {
        ProjectBackendKind backend = backendKind == null ? ProjectBackendKind.MAVEN : backendKind;
        List<DeploymentTarget> result = new ArrayList<>();
        if (deploymentTargets != null) {
            for (DeploymentTarget item : deploymentTargets) {
                if (item == null || blankToNull(item.path()) == null) {
                    continue;
                }
                String path = deploymentPathDiscovery.normalizeCandidates(List.of(item.path())).get(0);
                String artifact = blankToNull(item.jar());
                if (backend.isJavaPackaged() && artifact == null) {
                    throw new IllegalArgumentException(backend.isJavaWar()
                            ? "绑定的部署 WAR 必须是相对路径，例如 app.war"
                            : "绑定的部署 JAR 必须是相对路径，例如 current/app.jar");
                }
                validateArtifact(backend, artifact);
                result.add(new DeploymentTarget(path, artifact));
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        String legacyPath = blankToNull(deploymentPath);
        String boundArtifact = blankToNull(deploymentJar);
        validateArtifact(backend, boundArtifact);
        if (legacyPath == null) {
            return List.of();
        }
        String path = deploymentPathDiscovery.normalizeCandidates(List.of(legacyPath)).get(0);
        if (boundArtifact == null && !backend.isDocker()) {
            return List.of();
        }
        return List.of(new DeploymentTarget(path, boundArtifact));
    }

    private void validateArtifact(ProjectBackendKind backend, String artifact) {
        if (artifact == null) {
            return;
        }
        if (!artifact.matches("[A-Za-z0-9._/-]+") || artifact.contains("..") || artifact.startsWith("/")) {
            throw new IllegalArgumentException("绑定的部署制品必须是相对路径，例如 current/app.jar 或 app.war");
        }
        if (backend.isJavaJar() && !artifact.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("Java JAR 部署制品必须是 .jar 相对路径，例如 current/app.jar");
        }
        if (backend.isJavaWar() && !artifact.toLowerCase(Locale.ROOT).endsWith(".war")) {
            throw new IllegalArgumentException("Java WAR 部署制品必须是 .war 相对路径，例如 app.war");
        }
    }

    /**
     * 将部署目标转为可 JSON 序列化的 Map 列表，写入故障上下文与 API 响应。
     */
    private List<Map<String, String>> toTargetMaps(List<DeploymentTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> result = new ArrayList<>(targets.size());
        for (DeploymentTarget item : targets) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("path", item.path());
            String artifact = item.jar() == null ? "" : item.jar();
            row.put("jar", artifact);
            row.put("artifact", artifact);
            result.add(row);
        }
        return result;
    }

    /**
     * 校验绑定 JAR 属于指定候选路径的探测结果。
     */
    private void requireJarOnPath(
            DeploymentPathDiscoveryService.DetectionResult detection, String path, String jar) {
        DeploymentPathDiscoveryService.CandidateMatch match = detection.candidates().stream()
                .filter(item -> path.equals(item.path()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("绑定的部署路径不在候选目录中"));
        if (jar == null || jar.isBlank()) {
            if (!match.jars().isEmpty()) {
                throw new IllegalArgumentException("绑定部署目标时必须指定 JAR：" + path);
            }
            return;
        }
        if (!match.jars().isEmpty() && !match.jars().contains(jar)) {
            throw new IllegalArgumentException("绑定的 JAR 不在所选部署目录下：" + jar);
        }
    }

    /**
     * 从唯一识别结果中挑选优先制品（JAR 或 WAR）。
     */
    private String preferredArtifact(
            DeploymentPathDiscoveryService.DetectionResult detection, String path, String code,
            ProjectBackendKind backendKind) {
        DeploymentPathDiscoveryService.CandidateMatch match = detection.candidates().stream()
                .filter(item -> path.equals(item.path()))
                .findFirst()
                .orElse(null);
        if (match == null || match.jars().isEmpty()) {
            return backendKind.isJavaWar() ? code + ".war" : null;
        }
        String ext = backendKind.artifactExtension();
        if (backendKind.isJavaWar()) {
            String plain = code + ".war";
            if (match.jars().contains(plain)) {
                return plain;
            }
            return match.jars().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".war"))
                    .filter(name -> name.toLowerCase(Locale.ROOT).contains(code.toLowerCase(Locale.ROOT)))
                    .findFirst()
                    .orElseGet(() -> match.jars().stream()
                            .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".war"))
                            .findFirst()
                            .orElse(plain));
        }
        String standard = "current/" + code + ext;
        if (match.jars().contains(standard)) {
            return standard;
        }
        String plain = code + ext;
        if (match.jars().contains(plain)) {
            return plain;
        }
        String lower = code.toLowerCase(Locale.ROOT);
        return match.jars().stream()
                .filter(name -> name.toLowerCase(Locale.ROOT).contains(lower))
                .findFirst()
                .orElse(match.jars().get(0));
    }

    public static String normalizeBackendKind(String raw) {
        return ProjectBackendKind.from(raw).name();
    }

    public static String normalizeClientKind(String raw, String frontendWorkspacePath) {
        if ((raw == null || raw.isBlank() || "NONE".equalsIgnoreCase(raw.trim()))
                && frontendWorkspacePath != null && !frontendWorkspacePath.isBlank()) {
            return ProjectClientKind.WEB_NPM.name();
        }
        return ProjectClientKind.from(raw).name();
    }

    private ProjectProfile resolveProjectProfile(String backendKind, String clientKind, String clientVerifyCommand,
                                                 String backendVerifyCommand,
                                                 String workspacePath, String frontendWorkspacePath) {
        ProjectBackendKind backend = ProjectBackendKind.from(backendKind);
        ProjectClientKind client = ProjectClientKind.from(
                normalizeClientKind(clientKind, frontendWorkspacePath));
        Path backendModule = BackendWorkspacePaths.requireBackend(backend, workspacePath);
        Path backendWorkspace = Path.of(MavenWorkspacePaths.normalizeExternalPath(workspacePath))
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(backendWorkspace)) {
            backendWorkspace = backendModule;
        }
        Path clientWorkspace = ClientWorkspacePaths.requireClient(client, frontendWorkspacePath);
        Path repairRoot = RepairWorkspacePaths.resolveRepairRoot(backendWorkspace, clientWorkspace);
        String clientVerify = blankToNull(clientVerifyCommand);
        if (clientVerify == null) {
            clientVerify = blankToNull(client.defaultVerifyCommand());
        }
        String backendVerify = blankToNull(backendVerifyCommand);
        if (backendVerify == null) {
            backendVerify = blankToNull(backend.defaultVerifyCommand());
        }
        return new ProjectProfile(backend, client, backendWorkspace, backendModule, clientWorkspace,
                repairRoot, clientVerify, backendVerify);
    }

    private void applyProjectProfile(OpsTask ops, ProjectProfile profile) {
        ops.setBackendKind(profile.backendKind().name());
        ops.setClientKind(profile.clientKind().name());
        ops.setWorkspacePath(profile.backendWorkspace().toString());
        ops.setFrontendWorkspacePath(profile.clientWorkspace() == null ? null : profile.clientWorkspace().toString());
        ops.setClientVerifyCommand(profile.clientVerifyCommand());
        ops.setBackendVerifyCommand(profile.backendVerifyCommand());
    }

    private void applyHybridDeployFields(OpsTask ops, String clientDeployPath, String clientDistDir,
                                         String dockerComposeFile, String dockerImageName) {
        ops.setClientDeployPath(blankToNull(clientDeployPath));
        String dist = blankToNull(clientDistDir);
        ops.setClientDistDir(dist == null ? "dist" : dist);
        String compose = blankToNull(dockerComposeFile);
        ops.setDockerComposeFile(compose == null ? "docker-compose.yml" : compose);
        ops.setDockerImageName(blankToNull(dockerImageName));
    }

    static void putProjectProfileContext(IncidentTask incident, ProjectProfile profile) {
        incident.getContext().put("workspacePath", profile.backendWorkspace().toString());
        incident.getContext().put("backendModulePath", profile.backendModule().toString());
        incident.getContext().put("repairRoot", profile.repairRoot().toString());
        incident.getContext().put("backendKind", profile.backendKind().name());
        incident.getContext().put("clientKind", profile.clientKind().name());
        incident.getContext().put("applicationType", profile.backendKind().applicationType());
        if (profile.backendVerifyCommand() != null) {
            incident.getContext().put("backendVerifyCommand", profile.backendVerifyCommand());
        }
        if (profile.clientWorkspace() != null) {
            incident.getContext().put("frontendWorkspacePath", profile.clientWorkspace().toString());
        }
        if (profile.clientVerifyCommand() != null) {
            incident.getContext().put("clientVerifyCommand", profile.clientVerifyCommand());
        }
    }

    static void putHybridDeployContext(IncidentTask incident, OpsTask ops) {
        if (ops.getClientDeployPath() != null) {
            incident.getContext().put("clientDeployPath", ops.getClientDeployPath());
        }
        incident.getContext().put("clientDistDir",
                ops.getClientDistDir() == null || ops.getClientDistDir().isBlank() ? "dist" : ops.getClientDistDir());
        if (ops.getDockerComposeFile() != null) {
            incident.getContext().put("dockerComposeFile", ops.getDockerComposeFile());
        }
        if (ops.getDockerImageName() != null) {
            incident.getContext().put("dockerImageName", ops.getDockerImageName());
        }
    }

    record ProjectProfile(
            ProjectBackendKind backendKind,
            ProjectClientKind clientKind,
            Path backendWorkspace,
            Path backendModule,
            Path clientWorkspace,
            Path repairRoot,
            String clientVerifyCommand,
            String backendVerifyCommand
    ) {
    }
}
