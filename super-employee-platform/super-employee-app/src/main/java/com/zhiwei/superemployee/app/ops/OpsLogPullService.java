package com.zhiwei.superemployee.app.ops;

import com.zhiwei.superemployee.app.resource.ManagedHost;
import com.zhiwei.superemployee.app.resource.ResourceRepository;
import com.zhiwei.superemployee.app.runtime.LocalGitRepositoryGateway;
import com.zhiwei.superemployee.app.runtime.AgentRuntimeService;
import com.zhiwei.superemployee.app.runtime.AgentSession;
import com.zhiwei.superemployee.app.runtime.AgentSessionStatus;
import com.zhiwei.superemployee.app.runtime.RepairEngineLabels;
import com.zhiwei.superemployee.core.domain.IncidentStatus;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.gateway.SshDiagnosticGateway;
import com.zhiwei.superemployee.core.gateway.SshDiagnosticRequest;
import com.zhiwei.superemployee.core.gateway.SshDiagnosticResult;
import com.zhiwei.superemployee.core.gateway.SshDiagnosticTemplate;
import com.zhiwei.superemployee.core.repository.IncidentRepository;
import com.zhiwei.superemployee.core.workflow.IncidentWorkflow;
import com.zhiwei.superemployee.core.workspace.BackendWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.ClientWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.ProjectBackendKind;
import com.zhiwei.superemployee.core.workspace.ProjectClientKind;
import com.zhiwei.superemployee.core.workspace.RepairWorkspacePaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PreDestroy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * 周期拉日志：SSH 拉取 → 本地短窗落盘 → 错误关键字对比 → 异常时派生新 incident 分析。
 * @author yangdb
 */
@Service
public class OpsLogPullService {
    private static final Logger log = LoggerFactory.getLogger(OpsLogPullService.class);
    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "(?i)\\b(error|exception|fatal|severe|panic|oom)\\b");
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneId.systemDefault());

    private final OpsTaskRepository opsTasks;
    private final SshDiagnosticGateway diagnostics;
    private final IncidentWorkflow workflow;
    private final IncidentRepository incidents;
    private final ObjectProvider<ResourceRepository> resources;
    private final LocalGitRepositoryGateway gitRepository;
    private final ObjectProvider<AgentRuntimeService> agentRuntime;
    private final int maxLines;
    private final int maxFilesPerTask;
    private final int anomalyMinErrors;
    private final double anomalyGrowthRatio;
    private final Set<String> inFlightTasks = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingRestarts = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Object> taskGuards = new ConcurrentHashMap<>();
    private final ExecutorService taskExecutor;
    private final String pullOwnerId = "ops-pull-" + UUID.randomUUID();
    private final Duration pullLeaseDuration;

    /**
     * 构造周期拉日志服务。
     *
     * @param opsTasks            运维任务仓储
     * @param diagnostics         SSH 诊断网关
     * @param workflow            故障工作流
     * @param incidents           故障仓储
     * @param resources           资源中心（可选）
     * @param gitRepository       本地 Git 网关
     * @param agentRuntime        Agent 运行时（可选）
     * @param maxLines            本地落盘最大行数
     * @param sshMaxLines         SSH 诊断侧最大行数上限
     * @param maxFilesPerTask     单任务保留日志文件数上限
     * @param anomalyMinErrors    判定异常的最小错误关键字数
     * @param anomalyGrowthRatio  相对上次错误数的增长倍率阈值
     * @param workerThreads       拉取工作线程数
     */
    public OpsLogPullService(OpsTaskRepository opsTasks,
                             SshDiagnosticGateway diagnostics,
                             IncidentWorkflow workflow,
                             IncidentRepository incidents,
                             ObjectProvider<ResourceRepository> resources,
                             LocalGitRepositoryGateway gitRepository,
                             ObjectProvider<AgentRuntimeService> agentRuntime,
                             @Value("${super-employee.ops-log.max-lines:500}") int maxLines,
                             @Value("${super-employee.ssh.max-log-lines:200}") int sshMaxLines,
                             @Value("${super-employee.ops-log.max-files-per-task:500}") int maxFilesPerTask,
                             @Value("${super-employee.ops-log.anomaly-min-errors:3}") int anomalyMinErrors,
                             @Value("${super-employee.ops-log.anomaly-growth-ratio:1.5}") double anomalyGrowthRatio,
                             @Value("${super-employee.ops-log.worker-threads:4}") int workerThreads,
                             @Value("${super-employee.ops-log.pull-lease-minutes:30}") int pullLeaseMinutes) {
        this.opsTasks = opsTasks;
        this.diagnostics = diagnostics;
        this.workflow = workflow;
        this.incidents = incidents;
        this.resources = resources;
        this.gitRepository = gitRepository;
        this.agentRuntime = agentRuntime;
        this.maxLines = Math.max(50, Math.min(maxLines, Math.max(1, sshMaxLines)));
        this.maxFilesPerTask = Math.max(3, maxFilesPerTask);
        this.anomalyMinErrors = Math.max(1, anomalyMinErrors);
        this.anomalyGrowthRatio = Math.max(1.0, anomalyGrowthRatio);
        this.pullLeaseDuration = Duration.ofMinutes(Math.max(5, Math.min(240, pullLeaseMinutes)));
        this.taskExecutor = Executors.newFixedThreadPool(Math.max(1, Math.min(16, workerThreads)), runnable -> {
            Thread thread = new Thread(runnable, "ops-log-worker");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 扫描到期的周期任务并拉取。 */
    public void pullDueTasks() {
        Instant now = Instant.now();
        for (OpsTask task : opsTasks.findAll()) {
            if (!task.isEnabled() || !task.isScheduleEnabled()
                    || hasActiveAnalysis(task) || !due(task, now)) {
                continue;
            }
            submit(task, "周期扫描", false, false);
        }
    }

    /**
     * 应用启动完成后接管数据库中仍处于启用状态的周期任务。
     *
     * @return 应用启动完成后接管数据库中仍处于启用状态的周期任务
     */
    public int resumeEnabledTasks() {
        int submitted = 0;
        for (OpsTask task : opsTasks.findAll()) {
            if (!task.isEnabled() || !task.isScheduleEnabled()) {
                continue;
            }
            try {
                prepareNewAnalysis(task, "应用重启，上一轮分析已结束", true);
                if (submit(task, "应用启动恢复", true, true)) {
                    submitted++;
                }
            }
            catch (RuntimeException ex) {
                log.error("应用启动恢复运维任务 {} 失败", task.getId(), ex);
                task.setStatus(OpsTaskStatus.FAILED);
                task.setLatestSummary("应用启动恢复失败：" + truncate(ex.getMessage(), 400));
                opsTasks.update(task);
            }
        }
        log.info("应用启动恢复已重新提交 {} 个启用的定时运维任务", submitted);
        return submitted;
    }

    /**
     * 人工重新启动分析：结束上一轮非终态会话，立即重新拉取最新时间窗口日志，
     *
     * @param taskId 任务 ID
     */
    public void restartAnalysis(String taskId) {
        synchronized (guardFor(taskId)) {
            OpsTask task = opsTasks.findById(taskId)
                    .orElseThrow(() -> new IllegalArgumentException("运维任务不存在"));
            if (!task.isEnabled()) {
                throw new IllegalStateException("运维任务尚未启用，不能重新启动分析");
            }
            if (inFlightTasks.contains(taskId)) {
                pendingRestarts.add(taskId);
                task.setLatestSummary("配置已更新；当前拉取结束后将自动重新启动分析");
                opsTasks.update(task);
                log.info("运维任务 {} 正在执行，已合并为一次待处理重启", taskId);
                return;
            }
            prepareNewAnalysis(task, "运维任务已重新启动，上一轮分析已结束", false);
            if (!submit(task, "人工重新启动分析", true, true)) {
                pendingRestarts.add(taskId);
            }
        }
    }

    /**
     * 同一任务的配置变更、启停和调度提交串行执行；不同任务仍可并行。
     *
     * @param taskId 任务 ID
     * @param operation 受保护的操作
     * @return 同一任务的配置变更、启停和调度提交串行执行；不同任务仍可并行
     */
    public <T> T coordinate(String taskId, Supplier<T> operation) {
        synchronized (guardFor(taskId)) {
            return operation.get();
        }
    }

    /**
     * 判断指定运维任务是否正在执行拉取/分析。
     *
     * @param taskId 任务 ID
     * @return {@code true} 表示在飞行队列中
     */
    public boolean isTaskRunning(String taskId) {
        synchronized (guardFor(taskId)) {
            return inFlightTasks.contains(taskId);
        }
    }

    /**
     * 获取（或创建）任务级互斥锁对象。
     *
     * @param taskId 任务 ID
     * @return 用于 synchronized 的锁对象
     */
    private Object guardFor(String taskId) {
        return taskGuards.computeIfAbsent(taskId, ignored -> new Object());
    }

    /**
     * 结束上一轮分析引用并重置任务为等待新一轮拉取。
     *
     * @param task                运维任务
     * @param reason              取消上一轮会话的原因
     * @param applicationRestart  是否因应用重启触发取消
     */
    private void prepareNewAnalysis(OpsTask task, String reason, boolean applicationRestart) {
        if (task.getIncidentId() != null) {
            AgentRuntimeService runtime = agentRuntime.getIfAvailable();
            if (runtime != null) {
                runtime.cancelLatestByIncident(task.getIncidentId(), reason, applicationRestart);
            }
        }
        task.setIncidentId(null);
        task.setStatus(OpsTaskStatus.CREATED);
        task.setLatestSummary("正在结束上一轮分析并拉取最新日志");
        opsTasks.update(task);
    }

    /**
     * 将任务提交到工作线程池执行拉取。
     *
     * @param task                运维任务
     * @param source              提交来源说明（用于日志）
     * @param forceAnomalyRecheck 是否按绝对错误阈值判定异常
     * @param forceAnalyze        是否强制进入分析
     * @return {@code true} 表示成功入队；已在执行则为 {@code false}
     */
    private boolean submit(OpsTask task, String source,
                           boolean forceAnomalyRecheck, boolean forceAnalyze) {
        synchronized (guardFor(task.getId())) {
            if (!inFlightTasks.add(task.getId())) {
                log.debug("{}跳过已在执行队列中的运维任务 {}", source, task.getId());
                return false;
            }
            if (!opsTasks.tryClaimPullLease(task.getId(), pullOwnerId, Instant.now(),
                    pullLeaseDuration, forceAnalyze)) {
                inFlightTasks.remove(task.getId());
                log.debug("{}未抢到拉日志租约，跳过运维任务 {}", source, task.getId());
                return false;
            }
            try {
                taskExecutor.execute(() -> executeScheduledPull(task, forceAnomalyRecheck, forceAnalyze));
                log.info("{}已提交运维任务 {}", source, task.getId());
                return true;
            }
            catch (RuntimeException ex) {
                inFlightTasks.remove(task.getId());
                throw ex;
            }
        }
    }

    /**
     * 独立工作线程执行单个任务，避免长时间 Agent 分析阻塞全局调度扫描。
     *
     * @param task 运维/故障任务
     * @param forceAnomalyRecheck 是否强制按绝对阈值判异常
     * @param forceAnalyze 是否强制分析
     */
    private void executeScheduledPull(OpsTask task, boolean forceAnomalyRecheck, boolean forceAnalyze) {
        boolean pullStarted = false;
        try {
            OpsTask latest = opsTasks.findById(task.getId()).orElse(null);
            if (latest == null || !latest.isEnabled()
                    || (!latest.isScheduleEnabled() && !forceAnalyze)) {
                log.info("运维任务 {} 执行前状态已变化，跳过本次拉取", task.getId());
                return;
            }
            if (!forceAnalyze && hasActiveAnalysis(latest)) {
                log.info("运维任务 {} 上一轮 Agent 分析尚未结束，跳过本周期拉取", task.getId());
                return;
            }
            latest.setStatus(OpsTaskStatus.CREATED);
            latest.setLatestSummary("正在连接目标主机并拉取最近 "
                    + formatRange(latest.getLogRangeMinutes()) + " 的日志");
            opsTasks.update(latest);
            log.info("运维任务 {} 开始拉取日志 hostId={} template={} forceAnomalyRecheck={} forceAnalyze={}",
                    latest.getId(), latest.getHostId(), latest.getLogTemplate(),
                    forceAnomalyRecheck, forceAnalyze);
            pullStarted = true;
            pullOnce(latest, forceAnomalyRecheck, forceAnalyze);
        }
        catch (Exception ex) {
            pullStarted = true;
            log.warn("运维任务 {} 拉日志或分析失败: {}", task.getId(), ex.getMessage());
            opsTasks.findById(task.getId()).ifPresent(latest -> {
                latest.setLastPullAt(Instant.now());
                latest.setLastPullSummary("拉日志或分析失败：" + truncate(ex.getMessage(), 400));
                latest.setLatestSummary("日志拉取失败：" + truncate(ex.getMessage(), 400));
                latest.setStatus(OpsTaskStatus.FAILED);
                opsTasks.update(latest);
            });
        }
        finally {
            if (!pullStarted) {
                opsTasks.releasePullLease(task.getId(), pullOwnerId);
            }
            synchronized (guardFor(task.getId())) {
                inFlightTasks.remove(task.getId());
                if (pendingRestarts.remove(task.getId())) {
                    opsTasks.findById(task.getId()).ifPresent(latest -> {
                        if (!latest.isEnabled()) {
                            return;
                        }
                        prepareNewAnalysis(latest, "配置变更后的延迟重启，上一轮分析已结束", false);
                        submit(latest, "并发合并后的自动重启", true, true);
                    });
                }
            }
        }
    }

    /**
     * 容器销毁时立即关闭拉取工作线程池。
     */
    @PreDestroy
    public void shutdown() {
        taskExecutor.shutdownNow();
    }

    /**
     * 执行一次日志拉取（默认不强制异常复核、不强制分析）。
     *
     * @param task 运维任务
     * @throws IOException 落盘失败
     * @throws IllegalStateException 缺少主机或工作区
     */
    public void pullOnce(OpsTask task) throws IOException {
        pullOnce(task, false, false);
    }

    /**
     * 执行一次日志拉取、落盘、异常判定，并按策略触发分析。
     *
     * @param task                运维任务
     * @param forceAnomalyRecheck 是否按绝对错误阈值判定异常
     * @param forceAnalyze        是否在异常时强制分析
     * @throws IOException 落盘失败
     * @throws IllegalStateException 缺少主机或工作区
     */
    private void pullOnce(OpsTask task, boolean forceAnomalyRecheck, boolean forceAnalyze) throws IOException {
        if (task.getHostId() == null || task.getHostId().isBlank()) {
            throw new IllegalStateException("周期拉日志需要目标主机");
        }
        if (task.getWorkspacePath() == null || task.getWorkspacePath().isBlank()) {
            throw new IllegalStateException("周期拉日志需要代码工作区");
        }
        SshDiagnosticTemplate template = resolveTemplate(task.getLogTemplate());
        int since = Math.max(1, Math.min(1440,
                task.getLogRangeMinutes() <= 0 ? 30 : task.getLogRangeMinutes()));
        int lines = maxLines;
        SshDiagnosticResult result = diagnostics.execute(new SshDiagnosticRequest(
                task.getHostId(), template, blankToNull(task.getLogServiceName()),
                blankToNull(task.getLogPath()), since, lines));
        String output = result.output() == null ? "" : result.output();
        Path file = writeLocal(task, output, result.success(), result.summary());
        int errorCount = countErrors(output);
        int previous = task.getLastErrorCount();
        boolean anomaly = forceAnomalyRecheck
                ? errorCount >= anomalyMinErrors
                : isAnomaly(previous, errorCount);

        task.setLastPullAt(Instant.now());
        task.setLastPullFile(file.toString());
        task.setLastErrorCount(errorCount);
        String summary = String.format(Locale.ROOT,
                "%s · 行约%d · 错误关键字%d%s · 文件 %s",
                result.success() ? "拉取成功" : "拉取异常",
                output.isBlank() ? 0 : output.split("\\R", -1).length,
                errorCount,
                anomaly ? "（疑似异常）" : "",
                file.getFileName());
        task.setLastPullSummary(truncate(summary, 1800));
        if (!anomaly) {
            task.setLatestSummary(truncate("定时拉日志：" + summary, 1000));
        }
        opsTasks.update(task);
        rotate(file.getParent());
        log.info("运维任务 {} 日志拉取完成 success={} lines={} previousErrors={} currentErrors={} anomaly={} localFile={}",
                task.getId(), result.success(), output.isBlank() ? 0 : output.split("\\R", -1).length,
                previous, errorCount, anomaly, file);

        // 健康巡检只负责采集与落盘；只有确认异常后才启动 Agent，避免无故消耗模型资源。
        boolean shouldAnalyze = anomaly && (forceAnalyze || task.isAutoAnalyze());
        if (shouldAnalyze) {
            log.info("运维任务 {} 准备触发 Agent 分析 forceAnalyze={} autoAnalyze={} anomaly={}",
                    task.getId(), forceAnalyze, task.isAutoAnalyze(), anomaly);
            triggerAnalyze(task, file, errorCount, previous, output, result.success(), anomaly);
        }
        else {
            log.info("运维任务 {} 本次不触发 Agent 分析 forceAnalyze={} autoAnalyze={} anomaly={}",
                    task.getId(), forceAnalyze, task.isAutoAnalyze(), anomaly);
            task.setIncidentId(null);
            task.setStatus(OpsTaskStatus.COMPLETED);
            task.setLatestSummary(anomaly
                    ? "日志拉取完成并检测到异常；自动分析已关闭"
                    : "健康巡检完成，未检测到异常，无需 Agent 分析");
            opsTasks.update(task);
        }
    }

    /**
     * 将分钟窗口格式化为中文描述。
     *
     * @param minutes 分钟数
     * @return 面向用户的时间窗口文案
     */
    private String formatRange(int minutes) {
        return switch (minutes) {
            case 30 -> "半小时";
            case 60 -> "1 小时";
            case 120 -> "2 小时";
            case 360 -> "6 小时";
            case 720 -> "12 小时";
            case 1440 -> "24 小时";
            default -> Math.max(1, minutes) + " 分钟";
        };
    }

    /**
     * 判断任务当前是否到期应拉取。
     *
     * @param task 运维任务
     * @param now  当前时间
     * @return {@code true} 表示应拉取
     */
    private boolean due(OpsTask task, Instant now) {
        if (!task.isEnabled() || !task.isScheduleEnabled()) {
            return false;
        }
        if (task.getStatus() == OpsTaskStatus.ANALYZING
                || task.getStatus() == OpsTaskStatus.WAITING_APPROVAL) {
            return false;
        }
        if (task.getHostId() == null || task.getHostId().isBlank()) {
            return false;
        }
        int interval = Math.max(1, task.getPullIntervalMinutes());
        if (task.getLastPullAt() == null) {
            return true;
        }
        return !task.getLastPullAt().plusSeconds(interval * 60L).isAfter(now);
    }

    /**
     * 将拉取结果写入工作区 .zhiwei-logs 目录。
     *
     * @param task          运维任务
     * @param output        日志正文
     * @param success       远端拉取是否成功
     * @param resultSummary 诊断摘要
     * @return 落盘文件路径
     * @throws IOException 写文件失败
     */
    private Path writeLocal(OpsTask task, String output, boolean success, String resultSummary) throws IOException {
        Path dir = Path.of(task.getWorkspacePath()).toAbsolutePath().normalize()
                .resolve(".zhiwei-logs")
                .resolve(safeName(task.getId()));
        Files.createDirectories(dir);
        Path file = dir.resolve(FILE_TS.format(Instant.now()) + ".log");
        String header = "# opsTaskId=" + task.getId()
                + "\n# hostId=" + task.getHostId()
                + "\n# template=" + task.getLogTemplate()
                + "\n# pulledAt=" + Instant.now()
                + "\n# success=" + success
                + "\n# note=" + (resultSummary == null ? "" : resultSummary.replace('\n', ' '))
                + "\n\n";
        Files.writeString(file, header + (output == null ? "" : output), StandardCharsets.UTF_8);
        return file;
    }

    /**
     * 按单任务文件数上限删除过旧的日志文件。
     *
     * @param dir 任务日志目录
     * @throws IOException 列举或删除失败
     */
    private void rotate(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.log")) {
            for (Path path : stream) {
                files.add(path);
            }
        }
        files.sort(Comparator.comparing(Path::getFileName).reversed());
        for (int i = maxFilesPerTask; i < files.size(); i++) {
            Files.deleteIfExists(files.get(i));
        }
    }

    /**
     * 基于落盘日志派生故障任务并启动 Agent 分析。
     *
     * @param task          运维任务
     * @param logFile       本地日志文件
     * @param errorCount    本次错误关键字数
     * @param previous      上次错误关键字数
     * @param sample        日志样本文本
     * @param pullSucceeded 拉取是否成功
     * @param anomaly       是否判定为异常
     */
    private void triggerAnalyze(OpsTask task, Path logFile, int errorCount, int previous,
                                String sample, boolean pullSucceeded, boolean anomaly) {
        if (hasActiveAnalysis(task)) {
            log.info("运维任务 {} 已有进行中的分析，跳过异常触发", task.getId());
            return;
        }
        ManagedHost host = null;
        ResourceRepository repository = resources.getIfAvailable();
        if (repository != null) {
            host = repository.findHost(task.getHostId()).orElse(null);
        }
        String title = "[定时巡检] " + task.getTitle();
        String description = buildAnomalyDescription(
                task, host, logFile, errorCount, previous, sample, pullSucceeded, anomaly);
        // 同一运维任务复用稳定分支；完成合并后由 Git 网关回收，避免巡检次数越多分支越多。
        // 使用固定叶子节点以兼容旧版 ops/{任务ID}/{随机值} 分支；不能直接创建其父引用。
        String opsBranch = "ops/" + safeName(task.getId()) + "/active";
        LocalGitRepositoryGateway.Result branchResult;
        if (!anomaly) {
            branchResult = new LocalGitRepositoryGateway.Result(
                    true, "本周期未检测到异常，无需准备运维修复分支", task.getWorkspacePath());
        }
        else {
            try {
                branchResult = gitRepository.ensureOperationalBranch(task.getWorkspacePath(), opsBranch);
            }
            catch (RuntimeException ex) {
                branchResult = new LocalGitRepositoryGateway.Result(
                        false, "准备运维分支失败：" + ex.getMessage(), task.getWorkspacePath());
            }
        }
        IncidentTask incident = workflow.create(title, description, task.getPriority());
        incident.setCreatedBy(task.getCreatedBy());
        incident.getContext().put("source", "OPS_TASK_SCHEDULE");
        incident.getContext().put("opsTaskId", task.getId());
        incident.getContext().put("workspacePath", task.getWorkspacePath());
        try {
            ProjectBackendKind backendKind = ProjectBackendKind.from(
                    OpsTaskService.normalizeBackendKind(task.getBackendKind()));
            ProjectClientKind clientKind = ProjectClientKind.from(
                    OpsTaskService.normalizeClientKind(task.getClientKind(), task.getFrontendWorkspacePath()));
            Path backendModule = BackendWorkspacePaths.requireBackend(backendKind, task.getWorkspacePath());
            Path backend = Path.of(task.getWorkspacePath()).toAbsolutePath().normalize();
            Path client = ClientWorkspacePaths.requireClient(clientKind, task.getFrontendWorkspacePath());
            Path repairRoot = RepairWorkspacePaths.resolveRepairRoot(backend, client);
            incident.getContext().put("repairRoot", repairRoot.toString());
            incident.getContext().put("backendModulePath", backendModule.toString());
            incident.getContext().put("backendKind", backendKind.name());
            incident.getContext().put("clientKind", clientKind.name());
            incident.getContext().put("applicationType", backendKind.applicationType());
            if (task.getBackendVerifyCommand() != null && !task.getBackendVerifyCommand().isBlank()) {
                incident.getContext().put("backendVerifyCommand", task.getBackendVerifyCommand());
            } else {
                incident.getContext().put("backendVerifyCommand", backendKind.defaultVerifyCommand());
            }
            if (client != null) {
                incident.getContext().put("frontendWorkspacePath", client.toString());
            }
            String verify = task.getClientVerifyCommand();
            if (verify == null || verify.isBlank()) {
                verify = clientKind.defaultVerifyCommand();
            }
            if (verify != null && !verify.isBlank()) {
                incident.getContext().put("clientVerifyCommand", verify);
            }
            OpsTaskService.putHybridDeployContext(incident, task);
        }
        catch (RuntimeException ignored) {
            // 上下文补充失败时仍保留后端工作区，避免阻断巡检建故障
        }
        incident.getContext().put("repairEnginePreference",
                OpsTaskService.normalizeRepairEngine(task.getRepairEngine()));
        incident.getContext().put("applicationCode", task.getApplicationCode());
        if (!incident.getContext().containsKey("applicationType")) {
            incident.getContext().put("applicationType",
                    ProjectBackendKind.from(task.getBackendKind()).applicationType());
        }
        incident.getContext().put("repositoryUrl", task.getRepositoryUrl());
        incident.getContext().put("ownerName", task.getOwnerName());
        incident.getContext().put("autoDeploy", task.isAutoDeploy());
        incident.getContext().put("deploymentPath", task.getDeploymentPath());
        incident.getContext().put("deploymentPaths", task.getDeploymentPaths());
        incident.getContext().put("deploymentJar", task.getDeploymentJar());
        if (task.getDeploymentTargets() != null && !task.getDeploymentTargets().isEmpty()) {
            incident.getContext().put("deploymentTargets", task.getDeploymentTargets().stream()
                    .map(item -> {
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("path", item.path());
                        row.put("jar", item.jar() == null ? "" : item.jar());
                        return row;
                    })
                    .toList());
        }
        incident.getContext().put("localLogFile", logFile.toString());
        incident.getContext().put("logTemplate", task.getLogTemplate());
        incident.getContext().put("logPath", task.getLogPath());
        incident.getContext().put("pulledLogLines", sample.isBlank() ? 0 : sample.split("\\R", -1).length);
        incident.getContext().put("pullSucceeded", pullSucceeded);
        incident.getContext().put("opsBranch", opsBranch);
        incident.getContext().put("opsBranchReady", branchResult.success());
        incident.getContext().put("opsBranchResult", branchResult.output());
        // 周期日志始终分析；仅在检测到异常时，CodeX 修复才是必经步骤。
        // 分支准备失败必须明确终止，不能静默降级为只给调查建议。
        incident.getContext().put("codexRequired", anomaly);
        incident.getContext().put("anomalyDetected", anomaly);
        incident.getContext().put("preferredHostId", task.getHostId());
        if (host != null) {
            incident.getContext().put("preferredHostName", host.getName());
            incident.getContext().put("preferredHostIp", host.getIpAddress());
        }
        incidents.save(incident);

        task.setIncidentId(incident.getId());
        task.setStatus(OpsTaskStatus.ANALYZING);
        task.setLatestSummary(anomaly
                ? "定时拉日志发现异常，已启动 Agent 分析（错误关键字 " + errorCount + "）"
                : "定时日志周期分析已启动（本周期未检测到错误关键字）");
        opsTasks.update(task);
        log.info("运维任务 {} 已创建故障事件 {}，运维分支={} branchReady={}，开始 Agent 分析",
                task.getId(), incident.getId(), opsBranch, branchResult.success());

        try {
            workflow.run(incident.getId());
            log.info("运维任务 {} Agent 分析已启动 incidentId={}", task.getId(), incident.getId());
        }
        catch (RuntimeException ex) {
            log.error("运维任务 {} 启动 Agent 分析失败 incidentId={}",
                    task.getId(), incident.getId(), ex);
            task.setStatus(OpsTaskStatus.FAILED);
            task.setLatestSummary("异常触发分析失败：" + truncate(ex.getMessage(), 400));
            opsTasks.update(task);
            throw ex;
        }
    }

    /**
     * 兼容升级前遗留数据：以最新 Agent 会话终态纠正仍显示“分析中”的运维任务。
     *
     * @param task 运维/故障任务
     */
    private void reconcileFinishedAnalysis(OpsTask task) {
        if ((task.getStatus() != OpsTaskStatus.ANALYZING
                && task.getStatus() != OpsTaskStatus.WAITING_APPROVAL)
                || task.getIncidentId() == null) {
            return;
        }
        AgentRuntimeService runtime = agentRuntime.getIfAvailable();
        if (runtime == null) {
            return;
        }
        AgentSession session = runtime.getLatestByIncident(task.getIncidentId()).orElse(null);
        if (session == null) {
            return;
        }
        if (session.getStatus() == AgentSessionStatus.SUCCEEDED) {
            task.setStatus(OpsTaskStatus.COMPLETED);
            task.setLatestSummary(session.getFinalSummary());
            opsTasks.update(task);
        }
        else if (session.getStatus() == AgentSessionStatus.FAILED
                || session.getStatus() == AgentSessionStatus.CANCELLED
                || session.getStatus() == AgentSessionStatus.TIMEOUT) {
            task.setStatus(OpsTaskStatus.FAILED);
            task.setLatestSummary(session.getErrorMessage());
            opsTasks.update(task);
        }
    }

    /**
     * 同一运维任务只允许存在一轮活动分析。除正常状态外，再校验关联 incident，
     *
     * @param task 运维/故障任务
     * @return 是否满足条件
     */
    private boolean hasActiveAnalysis(OpsTask task) {
        if (task.getStatus() == OpsTaskStatus.ANALYZING
                || task.getStatus() == OpsTaskStatus.WAITING_APPROVAL) {
            reconcileFinishedAnalysis(task);
            if (task.getStatus() == OpsTaskStatus.ANALYZING
                    || task.getStatus() == OpsTaskStatus.WAITING_APPROVAL) {
                return true;
            }
        }
        if (task.getStatus() != OpsTaskStatus.CREATED || task.getIncidentId() == null) {
            return false;
        }
        IncidentTask incident = workflow.get(task.getIncidentId());
        if (incident == null) {
            return false;
        }
        if (incident.getStatus() == IncidentStatus.RUNNING
                || incident.getStatus() == IncidentStatus.APPROVAL_PENDING) {
            task.setStatus(incident.getStatus() == IncidentStatus.APPROVAL_PENDING
                    ? OpsTaskStatus.WAITING_APPROVAL : OpsTaskStatus.ANALYZING);
            opsTasks.update(task);
            return true;
        }
        return false;
    }

    /**
     * 构建写入故障描述的巡检/异常说明与日志短预览。
     *
     * @param task          运维任务
     * @param host          目标主机，可为 null
     * @param logFile       本地日志路径
     * @param errorCount    本次错误数
     * @param previous      上次错误数
     * @param sample        日志原文样本
     * @param pullSucceeded 拉取是否成功
     * @param anomaly       是否异常巡检
     * @return 故障描述文本
     */
    private String buildAnomalyDescription(OpsTask task, ManagedHost host, Path logFile,
                                           int errorCount, int previous, String sample,
                                           boolean pullSucceeded, boolean anomaly) {
        StringBuilder sb = new StringBuilder();
        sb.append(anomaly ? "【定时日志巡检异常】\n" : "【定时日志健康巡检】\n");
        sb.append("运维任务：").append(task.getTitle()).append('\n');
        if (anomaly) {
            sb.append("错误关键字：上次 ").append(previous).append(" → 本次 ").append(errorCount).append('\n');
        }
        else {
            sb.append("检测结果：未发现达到阈值的 ERROR/EXCEPTION（上次 ")
                    .append(previous).append(" → 本次 ").append(errorCount).append("）\n");
            sb.append("本周期只需分析日志并给出健康摘要与风险观察；禁止生成补丁、调用 ")
                    .append(RepairEngineLabels.cliName(task.getRepairEngine()))
                    .append("/测试工具，也不要派生修复规划或验证子智能体。统计计数不能替代日志原文证据。\n");
        }
        sb.append("本地日志文件：").append(logFile).append('\n');
        if (anomaly) {
            sb.append(pullSucceeded
                    ? "该日志已从目标主机成功拉取，是本次分析的第一证据；请先根据日志中的异常堆栈、错误码和上下文定位根因。\n"
                    : "本次日志拉取返回异常；请先区分诊断错误信息与应用日志，再补充只读证据。\n");
        }
        else {
            sb.append(pullSucceeded
                    ? "该日志已从目标主机成功拉取，请直接基于日志内容完成健康分析。\n"
                    : "本次日志拉取返回异常，请仅说明采集状态和可验证事实，不进入代码修复流程。\n");
        }
        sb.append("日志来源：").append(task.getLogTemplate());
        if (task.getLogPath() != null && !task.getLogPath().isBlank()) {
            sb.append("，远端路径：").append(task.getLogPath());
        }
        sb.append('\n');
        if (host != null) {
            sb.append("\n目标主机：").append(host.getName())
                    .append("（ID=").append(host.getId())
                    .append("，IP=").append(host.getIpAddress()).append("）\n");
        }
        sb.append("\n--- 日志片段 ---\n");
        // incident_task.description 在现有数据库中为 VARCHAR(4000)；
        // 完整采集日志由 INSPECT_INCIDENT 从 localLogFile 安全读取。
        // 完整日志通过 localLogFile/INSPECT_INCIDENT 读取，事件描述仅保留短预览，避免达梦行记录超长。
        sb.append(truncate(sample, 600));
        return sb.toString();
    }

    /**
     * 按最小错误数与相对增长倍率判断是否异常。
     *
     * @param previous 上次错误关键字数
     * @param current  本次错误关键字数
     * @return {@code true} 表示疑似异常
     */
    private boolean isAnomaly(int previous, int current) {
        if (current < anomalyMinErrors) {
            return false;
        }
        if (previous <= 0) {
            return current >= anomalyMinErrors;
        }
        return current >= previous * anomalyGrowthRatio + 1;
    }

    /**
     * 统计文本中匹配错误关键字的行数。
     *
     * @param output 日志文本
     * @return 命中行数
     */
    private static int countErrors(String output) {
        if (output == null || output.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String line : output.split("\\R")) {
            if (ERROR_PATTERN.matcher(line).find()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 将字符串日志模板解析为枚举。
     *
     * @param raw 原始模板名
     * @return 诊断模板枚举
     * @throws IllegalArgumentException 不支持的模板
     */
    private static SshDiagnosticTemplate resolveTemplate(String raw) {
        String value = raw == null || raw.isBlank() ? "JOURNAL_LOGS" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "APP_LOG_FILE", "JAVA_APPLICATION_LOGS", "JOURNAL_LOGS" -> SshDiagnosticTemplate.valueOf(value);
            default -> throw new IllegalArgumentException("不支持的日志模板：" + value);
        };
    }

    /**
     * 将 ID 转为安全目录名片段。
     *
     * @param id 原始 ID
     * @return 仅含安全字符的名称
     */
    private static String safeName(String id) {
        return id == null ? "unknown" : id.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * 空白字符串转为 null。
     *
     * @param value 原始字符串
     * @return 去空白后的值或 null
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 截断过长文本并追加省略号。
     *
     * @param value 原始文本
     * @param max   最大保留字符数
     * @return 截断后的文本
     */
    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }
}
