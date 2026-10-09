package com.zhiwei.superemployee.app.ops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 定时清理工作区中的冗余拉取日志。
 * <p>按保留天数、单任务文件数上限和目录容量三重约束删除；
 * 分析中任务最近一次拉取文件受保护，避免误删正在使用的证据。</p>
 * @author yangdb
 */
@Service
public class OpsLogCleanupService {
    private static final Logger log = LoggerFactory.getLogger(OpsLogCleanupService.class);
    private final OpsTaskRepository opsTasks;
    private final OpsLogPullService pullService;
    private final int retainDays;
    private final int maxFilesPerTask;
    private final long maxBytesPerTask;

    /**
     * @param opsTasks         运维任务仓储
     * @param pullService      用于任务级互斥协调，避免与拉取并发删文件
     * @param retainDays       日志保留天数，最小 1
     * @param maxFilesPerTask  单任务最多保留的 {@code *.log} 文件数
     * @param maxBytesPerTask  单任务日志目录最大字节数，最小 1MiB
     */
    public OpsLogCleanupService(
            OpsTaskRepository opsTasks,
            OpsLogPullService pullService,
            @Value("${super-employee.ops-log.retain-days:7}") int retainDays,
            @Value("${super-employee.ops-log.max-files-per-task:500}") int maxFilesPerTask,
            @Value("${super-employee.ops-log.max-bytes-per-task:1073741824}") long maxBytesPerTask) {
        this.opsTasks = opsTasks;
        this.pullService = pullService;
        this.retainDays = Math.max(1, retainDays);
        this.maxFilesPerTask = Math.max(1, maxFilesPerTask);
        this.maxBytesPerTask = Math.max(1024L * 1024L, maxBytesPerTask);
    }

    /**
     * 清理所有任务的本地日志目录。
     * <p>同一任务正在拉取时跳过，防止删除正在写入的文件。</p>
     *
     * @return 扫描、跳过、删除文件数及释放字节数汇总
     */
    public CleanupResult cleanup() {
        int scanned = 0;
        int skipped = 0;
        int deleted = 0;
        long releasedBytes = 0;
        for (OpsTask task : opsTasks.findAll()) {
            if (task.getWorkspacePath() == null || task.getWorkspacePath().isBlank()) {
                continue;
            }
            scanned++;
            try {
                TaskCleanup result = pullService.coordinate(task.getId(), () -> {
                    if (pullService.isTaskRunning(task.getId())) {
                        return TaskCleanup.skippedResult();
                    }
                    return cleanupTask(task, Instant.now());
                });
                if (result.skipped()) skipped++;
                deleted += result.deleted();
                releasedBytes += result.releasedBytes();
            }
            catch (RuntimeException ex) {
                log.warn("清理运维任务 {} 本地日志失败: {}", task.getId(), ex.getMessage());
            }
        }
        CleanupResult result = new CleanupResult(scanned, skipped, deleted, releasedBytes);
        log.info("本地日志清理完成 scanned={} skipped={} deleted={} releasedBytes={}",
                scanned, skipped, deleted, releasedBytes);
        return result;
    }

    /**
     * 对单个任务的 {@code .zhiwei-logs/{taskId}} 目录执行三重约束清理。
     *
     * @param task 运维任务
     * @param now  清理基准时间
     * @return 该任务的清理明细（是否跳过、删除数、释放字节）
     */
    private TaskCleanup cleanupTask(OpsTask task, Instant now) {
        Path directory = Path.of(task.getWorkspacePath()).toAbsolutePath().normalize()
                .resolve(".zhiwei-logs")
                .resolve(safeName(task.getId()));
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return TaskCleanup.emptyResult();
        }
        Path protectedFile = protectedLogFile(task, directory);
        List<LogFile> files = readLogFiles(directory, protectedFile);
        Instant cutoff = now.minus(retainDays, ChronoUnit.DAYS);
        int deleted = 0;
        long released = 0;

        for (LogFile file : files) {
            if (!file.protectedFile() && file.modifiedAt().isBefore(cutoff)
                    && delete(file.path(), task.getId())) {
                deleted++;
                released += file.size();
            }
        }

        List<LogFile> remaining = files.stream()
                .filter(file -> Files.exists(file.path(), LinkOption.NOFOLLOW_LINKS))
                .sorted(Comparator.comparing(LogFile::modifiedAt))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        long totalBytes = remaining.stream().mapToLong(LogFile::size).sum();
        while (remaining.size() > maxFilesPerTask || totalBytes > maxBytesPerTask) {
            int removableIndex = -1;
            for (int index = 0; index < remaining.size(); index++) {
                if (!remaining.get(index).protectedFile()) {
                    removableIndex = index;
                    break;
                }
            }
            if (removableIndex < 0) break;
            LogFile oldest = remaining.remove(removableIndex);
            if (delete(oldest.path(), task.getId())) {
                deleted++;
                released += oldest.size();
                totalBytes -= oldest.size();
            }
        }
        return new TaskCleanup(false, deleted, released);
    }

    /**
     * 列举目录下普通 {@code *.log} 文件及其修改时间、大小、是否受保护。
     *
     * @param directory     任务日志目录
     * @param protectedFile 分析中需保留的文件路径，可为 {@code null}
     * @return 日志文件元数据列表
     * @throws IllegalStateException 目录扫描失败
     */
    private List<LogFile> readLogFiles(Path directory, Path protectedFile) {
        List<LogFile> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.log")) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                Path normalized = path.toAbsolutePath().normalize();
                try {
                    result.add(new LogFile(
                            normalized,
                            Files.getLastModifiedTime(normalized, LinkOption.NOFOLLOW_LINKS).toInstant(),
                            Files.size(normalized),
                            protectedFile != null && protectedFile.equals(normalized)));
                }
                catch (IOException ex) {
                    log.debug("读取日志文件属性失败 path={} message={}", normalized, ex.getMessage());
                }
            }
        }
        catch (IOException ex) {
            throw new IllegalStateException("扫描日志目录失败：" + ex.getMessage(), ex);
        }
        return result;
    }

    /**
     * 当任务处于分析或待审批时，保护最近一次拉取的日志文件不被清理。
     *
     * @param task      运维任务
     * @param directory 任务日志目录
     * @return 受保护的绝对路径；无需保护时为 {@code null}
     */
    private Path protectedLogFile(OpsTask task, Path directory) {
        if (task.getStatus() != OpsTaskStatus.ANALYZING
                && task.getStatus() != OpsTaskStatus.WAITING_APPROVAL) {
            return null;
        }
        if (task.getLastPullFile() == null || task.getLastPullFile().isBlank()) {
            return null;
        }
        Path candidate = Path.of(task.getLastPullFile()).toAbsolutePath().normalize();
        return candidate.startsWith(directory) ? candidate : null;
    }

    /**
     * 尝试删除日志文件；失败只记警告，不中断整体清理。
     *
     * @param path   待删路径
     * @param taskId 任务 ID（用于日志）
     * @return {@code true} 表示文件已删除
     */
    private boolean delete(Path path, String taskId) {
        try {
            return Files.deleteIfExists(path);
        }
        catch (IOException ex) {
            log.warn("删除运维任务 {} 冗余日志失败 path={} message={}", taskId, path, ex.getMessage());
            return false;
        }
    }

    /**
     * 将任务 ID 转为可安全作为目录名的字符串。
     *
     * @param id 任务 ID，允许 {@code null}
     * @return 仅含安全字符的目录名片段
     */
    private String safeName(String id) {
        return id == null ? "unknown" : id.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /**
     * 一次全量清理的汇总结果。
     *
     * @param scannedTasks  扫描到的有效任务数（具备工作区路径）
     * @param skippedTasks  因正在拉取而跳过的任务数
     * @param deletedFiles  删除的日志文件总数
     * @param releasedBytes 释放的磁盘字节数
     */
    public record CleanupResult(int scannedTasks, int skippedTasks, int deletedFiles, long releasedBytes) { }

    /**
     * 单个日志文件的清理元数据。
     *
     * @param path          文件绝对路径
     * @param modifiedAt    最后修改时间
     * @param size          文件字节大小
     * @param protectedFile 是否为分析中受保护文件
     */
    private record LogFile(Path path, Instant modifiedAt, long size, boolean protectedFile) { }

    /**
     * 单任务清理明细。
     *
     * @param skipped       是否因并发拉取而跳过
     * @param deleted       删除文件数
     * @param releasedBytes 释放字节数
     */
    private record TaskCleanup(boolean skipped, int deleted, long releasedBytes) {
        /** @return 表示跳过清理的空结果 */
        private static TaskCleanup skippedResult() { return new TaskCleanup(true, 0, 0); }

        /** @return 目录不存在或无需清理时的空结果 */
        private static TaskCleanup emptyResult() { return new TaskCleanup(false, 0, 0); }
    }
}
