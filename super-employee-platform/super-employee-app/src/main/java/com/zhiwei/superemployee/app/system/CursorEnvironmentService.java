package com.zhiwei.superemployee.app.system;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 本机 Cursor CLI（{@code agent}）环境探测与登录。
 * <p>安装请按官方脚本手动或在文档指引下完成；本服务负责状态探测与 {@code agent login}。</p>
 * @author yangdb
 */
@Service
public class CursorEnvironmentService {
    private static final int MAX_OUTPUT_CHARS = 4000;
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(sk-[a-z0-9_-]{12,}|bearer\\s+[a-z0-9._-]{12,}|cursor[_ -]?api[_ -]?key[\"'=:\\s]+[a-z0-9._-]{12,})");

    private final String configuredExecutable;
    private final long loginTimeoutSeconds;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "cursor-environment-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<Operation> operation = new AtomicReference<>(Operation.idle());
    private final AtomicReference<Process> activeProcess = new AtomicReference<>();

    @Autowired
    public CursorEnvironmentService(
            @Value("${super-employee.cursor.executable:agent}") String configuredExecutable,
            @Value("${super-employee.cursor.login-timeout-seconds:600}") long loginTimeoutSeconds) {
        this.configuredExecutable = configuredExecutable == null ? "agent" : configuredExecutable.trim();
        this.loginTimeoutSeconds = Math.max(60, loginTimeoutSeconds);
    }

    public Map<String, Object> status() {
        Path executable = resolveExecutable();
        Probe version = executable == null ? new Probe(false, "") : runProbe(List.of(executable.toString(), "--version"), 15);
        Probe auth = executable == null || !version.success()
                ? new Probe(false, "")
                : runProbe(List.of(executable.toString(), "status"), 20);
        if (!auth.success() && executable != null) {
            auth = runProbe(List.of(executable.toString(), "whoami"), 20);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("installed", executable != null && version.success());
        result.put("version", version.success() ? preferVersionLine(version.output()) : null);
        result.put("executablePath", executable == null ? null : executable.toString());
        result.put("authenticated", auth.success());
        result.put("authSummary", auth.success() ? redact(firstLine(auth.output())) : "未登录或未配置 CURSOR_API_KEY");
        result.put("installHint", isWindows()
                ? "PowerShell: irm 'https://cursor.com/install?win32=true' | iex"
                : "curl https://cursor.com/install -fsS | bash");
        result.put("detectHint", isWindows()
                ? "agent --version  或  cursor-agent --version（官方目录 %LOCALAPPDATA%\\cursor-agent）"
                : "agent --version  或  cursor-agent --version");
        result.put("operation", operation.get().toMap());
        return result;
    }

    public Map<String, Object> currentOperation() {
        return operation.get().toMap();
    }

    public synchronized Map<String, Object> login() {
        Path executable = resolveExecutable();
        if (executable == null) {
            throw new IllegalStateException("尚未安装 Cursor CLI（agent），请先按官方脚本安装");
        }
        if (!runProbe(List.of(executable.toString(), "--version"), 15).success()) {
            throw new IllegalStateException("Cursor CLI 无法运行，请检查安装与 PATH");
        }
        Operation current = operation.get();
        if ("RUNNING".equals(current.state())) {
            throw new IllegalStateException("Cursor 登录正在进行，请勿重复操作");
        }
        Operation started = new Operation(UUID.randomUUID().toString(), "LOGIN", "RUNNING",
                "正在等待完成 Cursor CLI 登录", Instant.now().toString(), null, "");
        operation.set(started);
        Path exe = executable;
        executor.submit(() -> {
            try {
                List<String> command = buildLaunchCommand(exe, List.of("login"));
                ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
                Process process = builder.start();
                activeProcess.set(process);
                String output = readBounded(process.getInputStream());
                boolean finished = process.waitFor(loginTimeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    operation.set(started.finished("FAILED", "Cursor CLI 登录超时", output));
                    return;
                }
                boolean ok = process.exitValue() == 0;
                operation.set(started.finished(ok ? "SUCCEEDED" : "FAILED",
                        ok ? "Cursor CLI 登录完成" : "Cursor CLI 登录失败", output));
            }
            catch (Exception ex) {
                operation.set(started.finished("FAILED", "Cursor CLI 登录异常：" + ex.getMessage(), ""));
            }
            finally {
                activeProcess.set(null);
            }
        });
        return started.toMap();
    }

    public synchronized Map<String, Object> cancel() {
        Process process = activeProcess.getAndSet(null);
        if (process != null) {
            process.destroyForcibly();
        }
        Operation current = operation.get();
        if ("RUNNING".equals(current.state())) {
            Operation cancelled = current.finished("CANCELLED", "已取消 Cursor 环境操作", current.output());
            operation.set(cancelled);
            return cancelled.toMap();
        }
        return current.toMap();
    }

    @PreDestroy
    public void shutdown() {
        cancel();
        executor.shutdownNow();
    }

    private Path resolveExecutable() {
        String configured = configuredExecutable.isBlank() ? "agent" : configuredExecutable;
        if (configured.contains("/") || configured.contains("\\") || Path.of(configured).isAbsolute()) {
            Path path = Path.of(configured).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? path : null;
        }
        List<String> names = List.of(configured, "agent", "cursor-agent");
        List<String> suffixes = isWindows() ? List.of(".cmd", ".exe", ".bat", "") : List.of("");
        for (String name : names) {
            Path found = findOnPath(name, suffixes);
            if (found != null) {
                return found;
            }
        }
        return findInOfficialInstall(names, suffixes);
    }

    private Path findOnPath(String name, List<String> suffixes) {
        String pathValue = System.getenv("PATH");
        if (pathValue == null) {
            return null;
        }
        for (String suffix : suffixes) {
            for (String directory : pathValue.split(Pattern.quote(File.pathSeparator))) {
                if (directory == null || directory.isBlank()) {
                    continue;
                }
                Path candidate = Path.of(directory.trim()).resolve(name + suffix).normalize();
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath();
                }
            }
        }
        return null;
    }

    private Path findInOfficialInstall(List<String> names, List<String> suffixes) {
        Path dir = officialInstallDirectory();
        if (!Files.isDirectory(dir)) {
            return null;
        }
        for (String name : names) {
            for (String suffix : suffixes) {
                Path candidate = dir.resolve(name + suffix).normalize();
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath();
                }
            }
        }
        return null;
    }

    /**
     * Windows 官方安装目录：{@code %LOCALAPPDATA%\cursor-agent}。
     */
    public static Path officialInstallDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Local")
                : Path.of(localAppData);
        return base.resolve("cursor-agent").toAbsolutePath().normalize();
    }

    private Probe runProbe(List<String> command, long timeoutSeconds) {
        try {
            List<String> launch = buildLaunchCommand(Path.of(command.get(0)), command.subList(1, command.size()));
            Process process = new ProcessBuilder(launch).redirectErrorStream(true).start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            String output = readBounded(process.getInputStream());
            if (!finished) {
                process.destroyForcibly();
                return new Probe(false, output);
            }
            return new Probe(process.exitValue() == 0, output);
        }
        catch (Exception ex) {
            return new Probe(false, ex.getMessage() == null ? "" : ex.getMessage());
        }
    }

    private List<String> buildLaunchCommand(Path executable, List<String> args) {
        String lower = executable.getFileName().toString().toLowerCase(Locale.ROOT);
        List<String> command = new ArrayList<>();
        if (isWindows() && (lower.endsWith(".cmd") || lower.endsWith(".bat"))) {
            command.add(System.getenv().getOrDefault("ComSpec", "C:\\Windows\\System32\\cmd.exe"));
            command.add("/d");
            command.add("/s");
            command.add("/c");
            StringBuilder joined = new StringBuilder("call \"").append(executable).append('"');
            for (String arg : args) {
                joined.append(' ').append(arg.contains(" ") ? '"' + arg + '"' : arg);
            }
            command.add(joined.toString());
            return command;
        }
        command.add(executable.toString());
        command.addAll(args);
        return command;
    }

    private String readBounded(java.io.InputStream stream) throws IOException {
        String output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        return redact(truncate(output));
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_OUTPUT_CHARS ? value : value.substring(0, MAX_OUTPUT_CHARS) + "\n…已截断";
    }

    private static String redact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return SECRET.matcher(value).replaceAll("***");
    }

    private static String firstLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int idx = value.indexOf('\n');
        return idx < 0 ? value.trim() : value.substring(0, idx).trim();
    }

    /** 跳过 Tip 提示行，优先返回形如 {@code 2026.07.23-xxx} 的版本行。 */
    private static String preferVersionLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String[] lines = value.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.toLowerCase(Locale.ROOT).startsWith("tip:")) {
                continue;
            }
            if (trimmed.matches("(?i)\\d{4}\\.\\d{2}\\.\\d{2}[-.].*") || trimmed.matches("(?i)v?\\d+\\.\\d+.*")) {
                return trimmed;
            }
        }
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.toLowerCase(Locale.ROOT).startsWith("tip:")) {
                return trimmed;
            }
        }
        return firstLine(value);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private record Probe(boolean success, String output) { }

    private record Operation(String id, String type, String state, String message,
                             String startedAt, String finishedAt, String output) {
        static Operation idle() {
            return new Operation(null, null, "IDLE", "空闲", null, null, "");
        }

        Operation finished(String state, String message, String output) {
            return new Operation(id, type, state, message, startedAt, Instant.now().toString(),
                    redact(truncate(output)));
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("type", type);
            map.put("state", state);
            map.put("message", message);
            map.put("startedAt", startedAt);
            map.put("finishedAt", finishedAt);
            map.put("output", output);
            return map;
        }
    }
}
