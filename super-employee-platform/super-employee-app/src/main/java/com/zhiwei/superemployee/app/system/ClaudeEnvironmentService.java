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
 * 本机 Claude Code CLI（{@code claude}）环境探测与登录。
 * @author yangdb
 */
@Service
public class ClaudeEnvironmentService {
    private static final int MAX_OUTPUT_CHARS = 4000;
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(sk-ant-[a-z0-9_-]{12,}|anthropic[_ -]?api[_ -]?key[\"'=:\\s]+[a-z0-9._-]{12,}|bearer\\s+[a-z0-9._-]{12,})");

    private final String configuredExecutable;
    private final String configuredApiKey;
    private final long loginTimeoutSeconds;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "claude-environment-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<Operation> operation = new AtomicReference<>(Operation.idle());
    private final AtomicReference<Process> activeProcess = new AtomicReference<>();

    @Autowired
    public ClaudeEnvironmentService(
            @Value("${super-employee.claude.executable:claude}") String configuredExecutable,
            @Value("${super-employee.claude.api-key:}") String configuredApiKey,
            @Value("${super-employee.claude.login-timeout-seconds:600}") long loginTimeoutSeconds) {
        this.configuredExecutable = configuredExecutable == null ? "claude" : configuredExecutable.trim();
        this.configuredApiKey = configuredApiKey == null ? "" : configuredApiKey.trim();
        this.loginTimeoutSeconds = Math.max(60, loginTimeoutSeconds);
    }

    public Map<String, Object> status() {
        Path executable = resolveExecutable();
        Probe version = executable == null ? new Probe(false, "") : runProbe(List.of(executable.toString(), "--version"), 15);
        Probe auth = executable == null || !version.success()
                ? new Probe(false, "")
                : runProbe(List.of(executable.toString(), "auth", "status"), 20);
        boolean apiKeyConfigured = !configuredApiKey.isBlank()
                || (System.getenv("ANTHROPIC_API_KEY") != null && !System.getenv("ANTHROPIC_API_KEY").isBlank());
        boolean authenticated = auth.success() || (version.success() && apiKeyConfigured);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("installed", executable != null && version.success());
        result.put("version", version.success() ? firstLine(version.output()) : null);
        result.put("executablePath", executable == null ? null : executable.toString());
        result.put("authenticated", authenticated);
        result.put("authSummary", auth.success()
                ? redact(firstLine(auth.output()))
                : apiKeyConfigured ? "已配置 ANTHROPIC_API_KEY" : "未登录或未配置 ANTHROPIC_API_KEY");
        result.put("installHint", isWindows()
                ? "PowerShell: irm https://claude.ai/install.ps1 | iex"
                : "curl -fsSL https://claude.ai/install.sh | bash");
        result.put("detectHint", isWindows()
                ? "claude --version（亦会扫描 WinGet 安装目录 Anthropic.ClaudeCode）"
                : "claude --version");
        result.put("operation", operation.get().toMap());
        return result;
    }

    public Map<String, Object> currentOperation() {
        return operation.get().toMap();
    }

    public synchronized Map<String, Object> login() {
        Path executable = resolveExecutable();
        if (executable == null) {
            throw new IllegalStateException("尚未安装 Claude Code CLI（claude），请先按官方脚本安装");
        }
        if (!runProbe(List.of(executable.toString(), "--version"), 15).success()) {
            throw new IllegalStateException("Claude Code CLI 无法运行，请检查安装与 PATH");
        }
        Operation current = operation.get();
        if ("RUNNING".equals(current.state())) {
            throw new IllegalStateException("Claude 登录正在进行，请勿重复操作");
        }
        Operation started = new Operation(UUID.randomUUID().toString(), "LOGIN", "RUNNING",
                "正在等待完成 Claude Code 登录", Instant.now().toString(), null, "");
        operation.set(started);
        Path exe = executable;
        executor.submit(() -> {
            try {
                List<String> command = buildLaunchCommand(exe, List.of("auth", "login"));
                ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
                Process process = builder.start();
                activeProcess.set(process);
                String output = readBounded(process.getInputStream());
                boolean finished = process.waitFor(loginTimeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    operation.set(started.finished("FAILED", "Claude Code 登录超时", output));
                    return;
                }
                boolean ok = process.exitValue() == 0;
                operation.set(started.finished(ok ? "SUCCEEDED" : "FAILED",
                        ok ? "Claude Code 登录完成" : "Claude Code 登录失败", output));
            }
            catch (Exception ex) {
                operation.set(started.finished("FAILED", "Claude Code 登录异常：" + ex.getMessage(), ""));
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
            Operation cancelled = current.finished("CANCELLED", "已取消 Claude 环境操作", current.output());
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
        String configured = configuredExecutable.isBlank() ? "claude" : configuredExecutable;
        if (configured.contains("/") || configured.contains("\\") || Path.of(configured).isAbsolute()) {
            Path path = Path.of(configured).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? path : null;
        }
        List<String> names = List.of(configured, "claude");
        List<String> suffixes = isWindows() ? List.of(".exe", ".cmd", ".bat", "") : List.of("");
        for (String name : names) {
            Path found = findOnPath(name, suffixes);
            if (found != null) {
                return found;
            }
        }
        return findInOfficialInstalls(names, suffixes);
    }

    private Path findOnPath(String name, List<String> suffixes) {
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank()) {
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

    private Path findInOfficialInstalls(List<String> names, List<String> suffixes) {
        for (Path dir : officialInstallDirectories()) {
            if (!Files.isDirectory(dir)) {
                continue;
            }
            for (String name : names) {
                for (String suffix : suffixes) {
                    Path candidate = dir.resolve(name + suffix).normalize();
                    if (Files.isRegularFile(candidate)) {
                        return candidate.toAbsolutePath();
                    }
                }
            }
        }
        return null;
    }

    /**
     * 常见 Claude Code 安装目录（含 WinGet 包目录）。
     */
    public static List<Path> officialInstallDirectories() {
        List<Path> dirs = new ArrayList<>();
        Path localAppData = localAppDataBase();
        Path userHome = Path.of(System.getProperty("user.home"));
        dirs.add(userHome.resolve(".local").resolve("bin"));
        dirs.add(localAppData.resolve("Programs").resolve("Claude"));
        dirs.add(localAppData.resolve("Programs").resolve("claude"));
        Path wingetPackages = localAppData.resolve("Microsoft").resolve("WinGet").resolve("Packages");
        if (Files.isDirectory(wingetPackages)) {
            try (var stream = Files.list(wingetPackages)) {
                stream.filter(Files::isDirectory)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.startsWith("Anthropic.ClaudeCode");
                        })
                        .sorted()
                        .forEach(dirs::add);
            }
            catch (IOException ignored) {
                // 忽略目录枚举失败，继续其它候选
            }
        }
        Path wingetLinks = localAppData.resolve("Microsoft").resolve("WinGet").resolve("Links");
        dirs.add(wingetLinks);
        return dirs;
    }

    private static Path localAppDataBase() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            return Path.of(System.getProperty("user.home"), "AppData", "Local");
        }
        return Path.of(localAppData);
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
