package com.zhiwei.superemployee.app.system;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 本机 CodeX CLI 环境管理服务。
 * <p>
 * 负责检测、官方安装与 ChatGPT OAuth 登录状态查询。所有命令和路径均由平台固定，
 * 对外接口不接收任意命令，也不读取 CodeX 凭据文件内容。
 * </p>
 * <ul>
 *   <li>安装与登录为异步单线程任务，同一时刻仅允许一个进行中操作；</li>
 *   <li>输出经脱敏与截断后再返回前端；</li>
 *   <li>当前自动安装与浏览器登录仅支持 Windows。</li>
 * </ul>
 * @author yangdb
 */
@Service
public class CodexEnvironmentService {
    /** 官方 PowerShell 安装脚本 URL。 */
    private static final String INSTALL_SCRIPT = "https://chatgpt.com/codex/install.ps1";
    /** 返回给前端的命令输出最大字符数。 */
    private static final int MAX_OUTPUT_CHARS = 4000;
    /** 匹配常见令牌/Bearer 等敏感片段的脱敏正则。 */
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(sk-[a-z0-9_-]{12,}|bearer\\s+[a-z0-9._-]{12,}|access[_ -]?token[\"'=:\\s]+[a-z0-9._-]{12,})");

    private final String configuredExecutable;
    private final long installTimeoutSeconds;
    private final long loginTimeoutSeconds;
    private final Function<String, ExecutionResult> operationRunner;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "codex-environment-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<Operation> operation =
            new AtomicReference<>(Operation.idle());
    private final AtomicReference<Process> activeProcess = new AtomicReference<>();

    /**
     * Spring 注入构造：从配置读取可执行名与超时。
     *
     * @param configuredExecutable   CodeX 可执行名或绝对路径，默认 {@code codex}
     * @param installTimeoutSeconds  安装超时秒数（至少 60）
     * @param loginTimeoutSeconds    登录超时秒数（至少 60）
     */
    @Autowired
    public CodexEnvironmentService(
            @Value("${super-employee.codex.executable:codex}") String configuredExecutable,
            @Value("${super-employee.codex.install-timeout-seconds:600}") long installTimeoutSeconds,
            @Value("${super-employee.codex.login-timeout-seconds:600}") long loginTimeoutSeconds) {
        this.configuredExecutable = configuredExecutable == null ? "codex" : configuredExecutable.trim();
        this.installTimeoutSeconds = Math.max(60, installTimeoutSeconds);
        this.loginTimeoutSeconds = Math.max(60, loginTimeoutSeconds);
        this.operationRunner = null;
    }

    /**
     * 测试用构造：可注入自定义操作执行器以绕过真实进程。
     *
     * @param configuredExecutable  可执行名或路径
     * @param installTimeoutSeconds 安装超时秒数
     * @param loginTimeoutSeconds   登录超时秒数
     * @param operationRunner       按操作类型（INSTALL/LOGIN）返回执行结果的替身
     */
    CodexEnvironmentService(String configuredExecutable, long installTimeoutSeconds,
                            long loginTimeoutSeconds,
                            Function<String, ExecutionResult> operationRunner) {
        this.configuredExecutable = configuredExecutable;
        this.installTimeoutSeconds = installTimeoutSeconds;
        this.loginTimeoutSeconds = loginTimeoutSeconds;
        this.operationRunner = operationRunner;
    }

    /**
     * 探测本机 CodeX 安装与登录状态，并附带当前异步操作快照。
     *
     * @return 含 {@code supported}/{@code installed}/{@code version}/{@code executablePath}/
     *         {@code authenticated}/{@code authSummary}/{@code operation} 的状态 Map
     */
    public Map<String, Object> status() {
        Path executable = resolveExecutable();
        Probe version = executable == null
                ? new Probe(false, "")
                : runProbe(buildCodexCommand(executable, "--version"), 15);
        Probe login = executable == null || !version.success()
                ? new Probe(false, "")
                : runProbe(buildCodexCommand(executable, "login", "status"), 20);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supported", isWindows());
        result.put("installed", executable != null && version.success());
        result.put("version", version.success() ? firstLine(version.output()) : null);
        result.put("executablePath", executable == null ? null : executable.toString());
        result.put("authenticated", login.success());
        result.put("authSummary", login.success() ? safeAuthSummary(login.output()) : "未登录");
        result.put("operation", operation.get().toMap());
        return result;
    }

    /**
     * 返回当前异步操作（安装/登录）的快照。
     *
     * @return 操作 Map（id/type/state/message/startedAt/finishedAt）
     */
    public Map<String, Object> currentOperation() {
        return operation.get().toMap();
    }

    /**
     * 触发官方安装流程；若已安装且可运行则直接返回成功态。
     *
     * @return 已启动或已完成的操作快照
     * @throws IllegalStateException 非 Windows，或已有进行中操作时
     */
    public synchronized Map<String, Object> install() {
        requireWindows();
        Path executable = resolveExecutable();
        if (executable != null && runProbe(buildCodexCommand(executable, "--version"), 15).success()) {
            Operation completed = Operation.completed("INSTALL", "CodeX CLI 已安装，无需重复安装");
            operation.set(completed);
            return completed.toMap();
        }
        return submit("INSTALL");
    }

    /**
     * 触发 ChatGPT 浏览器登录；若已登录则直接返回成功态。
     *
     * @return 已启动或已完成的操作快照
     * @throws IllegalStateException 非 Windows、未安装、CLI 不可用，或已有进行中操作时
     */
    public synchronized Map<String, Object> login() {
        requireWindows();
        Path executable = resolveExecutable();
        if (executable == null) {
            throw new IllegalStateException("尚未安装 CodeX CLI，请先完成安装");
        }
        Probe version = runProbe(buildCodexCommand(executable, "--version"), 15);
        if (!version.success()) {
            throw new IllegalStateException("CodeX CLI 无法运行，请重新安装");
        }
        if (runProbe(buildCodexCommand(executable, "login", "status"), 20).success()) {
            Operation completed = Operation.completed("LOGIN", "ChatGPT 已登录，无需重复登录");
            operation.set(completed);
            return completed.toMap();
        }
        return submit("LOGIN");
    }

    /**
     * 提交异步安装或登录任务到单线程执行器。
     *
     * @param type 操作类型：{@code INSTALL} 或 {@code LOGIN}
     * @return 刚启动的 RUNNING 态操作快照
     * @throws IllegalStateException 已有 RUNNING 操作时
     */
    private Map<String, Object> submit(String type) {
        Operation current = operation.get();
        if ("RUNNING".equals(current.state())) {
            throw new IllegalStateException("CodeX " + operationLabel(current.type()) + "正在进行，请勿重复操作");
        }
        Operation started = new Operation(UUID.randomUUID().toString(), type, "RUNNING",
                type.equals("INSTALL") ? "正在下载并安装 CodeX CLI" : "正在等待浏览器完成 ChatGPT 登录",
                Instant.now().toString(), null);
        operation.set(started);
        executor.submit(() -> execute(started));
        return started.toMap();
    }

    /**
     * 在后台执行安装或登录，并将结果写回操作状态。
     *
     * @param started 已提交的 RUNNING 操作
     */
    private void execute(Operation started) {
        ExecutionResult result;
        try {
            result = operationRunner != null
                    ? operationRunner.apply(started.type())
                    : ("INSTALL".equals(started.type()) ? executeInstall() : executeLogin());
        }
        catch (RuntimeException ex) {
            result = new ExecutionResult(false, ex.getMessage());
        }
        Operation current = operation.get();
        if (!started.id().equals(current.id()) || !"RUNNING".equals(current.state())) return;
        operation.set(new Operation(started.id(), started.type(),
                result.success() ? "SUCCEEDED" : "FAILED",
                result.success()
                        ? ("INSTALL".equals(started.type()) ? "CodeX CLI 安装完成" : "ChatGPT 登录完成")
                        : sanitizeOutput(result.message()),
                started.startedAt(), Instant.now().toString()));
    }

    /**
     * 通过官方 PowerShell 脚本安装 CodeX，并校验 {@code codex.exe} 可运行。
     *
     * @return 安装执行结果（成功时 message 含版本探测输出）
     */
    private ExecutionResult executeInstall() {
        Path installDir = officialInstallDirectory();
        List<String> command = List.of(
                powershellExecutable(), "-NoLogo", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command",
                "Invoke-RestMethod '" + INSTALL_SCRIPT + "' | Invoke-Expression");
        Map<String, String> environment = Map.of(
                "CODEX_NON_INTERACTIVE", "1",
                "CODEX_INSTALL_DIR", installDir.toString());
        ExecutionResult result = run(command, environment, installTimeoutSeconds, true);
        if (!result.success()) return result;
        Path executable = installDir.resolve("codex.exe");
        Probe verified = runProbe(buildCodexCommand(executable, "--version"), 20);
        return verified.success()
                ? new ExecutionResult(true, verified.output())
                : new ExecutionResult(false, "安装程序已结束，但 CodeX CLI 校验失败：" + verified.output());
    }

    /**
     * 执行 {@code codex login} 并再次探测登录状态。
     *
     * @return 登录执行结果
     */
    private ExecutionResult executeLogin() {
        Path executable = resolveExecutable();
        if (executable == null) return new ExecutionResult(false, "CodeX CLI 不存在，请先安装");
        ExecutionResult result = run(buildCodexCommand(executable, "login"), Map.of(),
                loginTimeoutSeconds, true);
        if (!result.success()) return result;
        Probe status = runProbe(buildCodexCommand(executable, "login", "status"), 20);
        return status.success()
                ? new ExecutionResult(true, status.output())
                : new ExecutionResult(false, "登录流程已结束，但未检测到有效登录状态");
    }

    /**
     * 取消当前进行中的安装/登录：销毁子进程并将操作标记为 CANCELLED。
     *
     * @return 取消后或原非 RUNNING 态的操作快照
     */
    public synchronized Map<String, Object> cancel() {
        Operation current = operation.get();
        if (!"RUNNING".equals(current.state())) return current.toMap();
        Process process = activeProcess.getAndSet(null);
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        Operation cancelled = new Operation(current.id(), current.type(), "CANCELLED",
                "操作已取消", current.startedAt(), Instant.now().toString());
        operation.set(cancelled);
        return cancelled.toMap();
    }

    /**
     * 按配置与 PATH / 官方目录解析本机 CodeX 可执行文件路径。
     *
     * @return 存在的可执行文件路径；未找到则 {@code null}
     */
    Path resolveExecutable() {
        String configured = configuredExecutable == null ? "" : configuredExecutable.trim();
        if (!configured.isBlank() && (configured.contains("/") || configured.contains("\\")
                || Path.of(configured).isAbsolute())) {
            Path path = Path.of(configured).toAbsolutePath().normalize();
            return Files.isRegularFile(path) ? path : null;
        }
        String name = configured.isBlank() ? "codex" : configured;
        String pathValue = System.getenv("PATH");
        if (pathValue != null) {
            for (String directory : pathValue.split(Pattern.quote(java.io.File.pathSeparator))) {
                if (directory == null || directory.isBlank()) continue;
                for (String suffix : isWindows() ? List.of(".exe", ".cmd", ".bat", "") : List.of("")) {
                    Path candidate = Path.of(directory.trim()).resolve(name + suffix).normalize();
                    if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
                }
            }
        }
        if (isWindows() && "codex".equalsIgnoreCase(name)) {
            Path official = officialInstallDirectory().resolve("codex.exe");
            if (Files.isRegularFile(official)) return official.toAbsolutePath();
        }
        return null;
    }

    /**
     * 组装调用 CodeX 的进程命令行；Windows 下对 {@code .cmd}/{@code .bat} 走 {@code cmd /c call}。
     *
     * @param executable CodeX 可执行文件路径
     * @param arguments  追加参数
     * @return 可供 {@link ProcessBuilder} 使用的命令列表
     */
    static List<String> buildCodexCommand(Path executable, String... arguments) {
        String lower = executable.getFileName().toString().toLowerCase(Locale.ROOT);
        if (isWindows() && (lower.endsWith(".cmd") || lower.endsWith(".bat"))) {
            StringBuilder command = new StringBuilder("call \"").append(executable).append("\"");
            for (String argument : arguments) command.append(' ').append(argument);
            return List.of(System.getenv().getOrDefault("ComSpec", "C:\\Windows\\System32\\cmd.exe"),
                    "/d", "/s", "/c", command.toString());
        }
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(List.of(arguments));
        return command;
    }

    /**
     * 启动外部进程并在超时内等待结束；可登记为可取消进程。
     *
     * @param command        命令行
     * @param environment    额外环境变量
     * @param timeoutSeconds 超时秒数
     * @param cancellable    {@code true} 时登记到 {@link #activeProcess} 以便 {@link #cancel()}
     * @return 退出码为 0 视为成功，message 为合并后的标准输出
     */
    private ExecutionResult run(List<String> command, Map<String, String> environment,
                                long timeoutSeconds, boolean cancellable) {
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("zhiwei-codex-environment-", ".log");
            ProcessBuilder builder = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile());
            builder.environment().putAll(environment);
            process = builder.start();
            if (cancellable) activeProcess.set(process);
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new ExecutionResult(false, "操作超时，请稍后重试");
            }
            String text = Files.readString(output, StandardCharsets.UTF_8);
            return new ExecutionResult(process.exitValue() == 0, text);
        }
        catch (IOException ex) {
            return new ExecutionResult(false, "命令启动失败：" + ex.getMessage());
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return new ExecutionResult(false, "操作被中断");
        }
        finally {
            if (cancellable && process != null) activeProcess.compareAndSet(process, null);
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                }
                catch (IOException ignored) {
                    // 临时诊断文件由系统后续清理。
                }
            }
        }
    }

    /**
     * 以探测方式运行命令，并对输出做脱敏截断。
     *
     * @param command        命令行
     * @param timeoutSeconds 超时秒数
     * @return 探测结果
     */
    private Probe runProbe(List<String> command, long timeoutSeconds) {
        ExecutionResult result = run(command, Map.of(), timeoutSeconds, false);
        return new Probe(result.success(), sanitizeOutput(result.message()));
    }

    /**
     * 脱敏并截断命令输出，避免凭据与过长日志外泄。
     *
     * @param value 原始输出，可为 {@code null}
     * @return 处理后的文本
     */
    static String sanitizeOutput(String value) {
        String text = value == null ? "" : SECRET.matcher(value).replaceAll("[凭据已隐藏]").trim();
        if (text.length() > MAX_OUTPUT_CHARS) return text.substring(0, MAX_OUTPUT_CHARS) + "\n…输出已截断";
        return text;
    }

    /**
     * 从登录状态输出中提取一行安全摘要。
     *
     * @param output 已探测的登录状态输出
     * @return 摘要文案；空则返回默认「已登录 ChatGPT」
     */
    private static String safeAuthSummary(String output) {
        String line = firstLine(sanitizeOutput(output));
        return line.isBlank() ? "已登录 ChatGPT" : line;
    }

    /**
     * 取文本中第一个非空行。
     *
     * @param value 多行文本
     * @return 首个非空行，或空串
     */
    private static String firstLine(String value) {
        if (value == null || value.isBlank()) return "";
        return value.lines().map(String::trim).filter(line -> !line.isBlank()).findFirst().orElse("");
    }

    /**
     * 将操作类型转为中文标签。
     *
     * @param type {@code INSTALL} 或其它（视为登录）
     * @return 「安装」或「登录」
     */
    private static String operationLabel(String type) {
        return "INSTALL".equals(type) ? "安装" : "登录";
    }

    /**
     * 官方 Windows 安装目录：{@code %LOCALAPPDATA%\Programs\OpenAI\Codex\bin}。
     *
     * @return 规范化后的安装 bin 目录
     */
    public static Path officialInstallDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), "AppData", "Local")
                : Path.of(localAppData);
        return base.resolve("Programs").resolve("OpenAI").resolve("Codex").resolve("bin")
                .toAbsolutePath().normalize();
    }

    /**
     * 解析 PowerShell 可执行路径。
     *
     * @return 系统 PowerShell 绝对路径，找不到则返回 {@code powershell.exe}
     */
    private static String powershellExecutable() {
        String systemRoot = System.getenv().getOrDefault("SystemRoot", "C:\\Windows");
        Path executable = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        return Files.isRegularFile(executable) ? executable.toString() : "powershell.exe";
    }

    /**
     * 判断当前 JVM 是否运行在 Windows。
     *
     * @return Windows 时 {@code true}
     */
    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * 非 Windows 时拒绝自动安装与浏览器登录。
     *
     * @throws IllegalStateException 非 Windows 平台
     */
    private static void requireWindows() {
        if (!isWindows()) throw new IllegalStateException("当前自动安装与浏览器登录仅支持本机 Windows");
    }

    /**
     * 容器销毁时取消进行中操作并关闭后台线程池。
     */
    @PreDestroy
    public void shutdown() {
        cancel();
        executor.shutdownNow();
    }

    /**
     * 轻量探测结果：是否成功及脱敏后的输出。
     *
     * @param success 命令是否成功
     * @param output  脱敏后的输出文本
     */
    private record Probe(boolean success, String output) { }

    /**
     * 安装/登录等操作的执行结果。
     *
     * @param success 是否成功
     * @param message 原始或错误消息
     */
    record ExecutionResult(boolean success, String message) { }

    /**
     * 异步操作状态快照。
     *
     * @param id         操作 ID
     * @param type       类型（INSTALL/LOGIN）
     * @param state      状态（IDLE/RUNNING/SUCCEEDED/FAILED/CANCELLED）
     * @param message    人类可读说明
     * @param startedAt  开始时间 ISO-8601
     * @param finishedAt 结束时间 ISO-8601，进行中可为 {@code null}
     */
    private record Operation(String id, String type, String state, String message,
                             String startedAt, String finishedAt) {
        /**
         * @return 空闲态操作
         */
        static Operation idle() {
            return new Operation(null, null, "IDLE", "暂无进行中的操作", null, null);
        }

        /**
         * 构造已成功完成的操作快照。
         *
         * @param type    操作类型
         * @param message 成功说明
         * @return SUCCEEDED 态操作
         */
        static Operation completed(String type, String message) {
            String now = Instant.now().toString();
            return new Operation(UUID.randomUUID().toString(), type, "SUCCEEDED", message, now, now);
        }

        /**
         * 转为前端可读的有序 Map。
         *
         * @return 含 id/type/state/message/startedAt/finishedAt 的 Map
         */
        Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("type", type);
            result.put("state", state);
            result.put("message", message);
            result.put("startedAt", startedAt);
            result.put("finishedAt", finishedAt);
            return result;
        }
    }
}
