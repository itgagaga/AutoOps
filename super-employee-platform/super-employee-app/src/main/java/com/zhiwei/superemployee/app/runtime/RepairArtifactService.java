package com.zhiwei.superemployee.app.runtime;

import com.zhiwei.superemployee.app.system.WorkspaceSettingsService;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.workspace.BackendWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.ProjectBackendKind;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 构建、定位并安全下载人工部署验证所需的 JAR 制品。 
 * @author yangdb
 */
@Service
public class RepairArtifactService {
    private static final int MAX_OUTPUT_CHARS = 20_000;
    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("\"?(\\d+)(?:\\.(\\d+))?");
    private final WorkspaceSettingsService workspaceSettings;
    private final long timeoutSeconds;
    private final String configuredJavaHome;
    private final int minJavaVersion;

    /**
     * 注入工作区设置、Maven 构建超时与构建 JDK。
     *
     * @param workspaceSettings   工作区/Maven 模块解析服务
     * @param timeoutSeconds      {@code mvn package} 超时秒数，最小 60
     * @param configuredJavaHome  可选：强制 Maven 使用的 JDK 家目录
     * @param minJavaVersion      构建所需最低主版本，默认 17
     */
    public RepairArtifactService(
            WorkspaceSettingsService workspaceSettings,
            @Value("${super-employee.artifact.timeout-seconds:900}") long timeoutSeconds,
            @Value("${super-employee.artifact.java-home:}") String configuredJavaHome,
            @Value("${super-employee.artifact.min-java-version:17}") int minJavaVersion) {
        this.workspaceSettings = workspaceSettings;
        this.timeoutSeconds = Math.max(60, timeoutSeconds);
        this.configuredJavaHome = configuredJavaHome == null ? "" : configuredJavaHome.trim();
        this.minJavaVersion = Math.max(8, minJavaVersion);
    }

    /**
     * 在故障任务绑定的 Maven 模块内执行 {@code mvn package}，并定位本次生成的可部署 JAR。
     *
     * @param task 故障任务（上下文需含 {@code workspacePath}）
     * @return 构建是否成功、制品路径与构建日志摘要
     */
    public BuildResult build(IncidentTask task) {
        ProjectBackendKind kind = resolveBackendKind(task);
        String workspacePath = String.valueOf(task.getContext().getOrDefault("workspacePath", ""));
        Object moduleOverride = task.getContext().get("backendModulePath");
        Path module = moduleOverride != null && !String.valueOf(moduleOverride).isBlank()
                ? Path.of(String.valueOf(moduleOverride)).toAbsolutePath().normalize()
                : BackendWorkspacePaths.requireBackend(kind, workspacePath);
        if (kind == ProjectBackendKind.GRADLE) {
            return buildGradle(module);
        }
        if (kind == ProjectBackendKind.JAVA_WAR) {
            return buildMavenWar(module);
        }
        if (kind != ProjectBackendKind.MAVEN) {
            return new BuildResult(true, module,
                    "非 Java 制品将在部署阶段按画像打包（" + kind.displayName() + "），跳过 Maven package");
        }
        Instant startedAt = Instant.now();
        Path javaHome = resolveBuildJavaHome();
        if (javaHome == null) {
            return new BuildResult(false, null,
                    "JAR 构建失败：未找到可用的 JDK " + minJavaVersion
                            + "+。请安装 JDK 17/21，或在配置中设置 super-employee.artifact.java-home");
        }
        // clean 规避 target/maven-status 下损坏的 inputFiles.lst（中文路径 + 编码不一致时常见）
        // -B 批处理；显式 UTF-8，降低中文路径/Windows 控制台编码干扰
        String mavenArgs = "clean package -B -DskipTests -Dfile.encoding=UTF-8 -Dproject.build.sourceEncoding=UTF-8";
        List<String> command = isWindows()
                ? List.of("cmd.exe", "/d", "/s", "/c", "mvn.cmd " + mavenArgs)
                : List.of("mvn", "clean", "package", "-B", "-DskipTests",
                        "-Dfile.encoding=UTF-8", "-Dproject.build.sourceEncoding=UTF-8");
        Process process = null;
        Path outputFile = null;
        try {
            purgeStaleCompilerStatus(module);
            outputFile = Files.createTempFile("zhiwei-artifact-", ".log");
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(module.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile());
            configureBuildEnvironment(processBuilder.environment(), javaHome);
            process = processBuilder.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                String output = readBounded(outputFile);
                return new BuildResult(false, null,
                        "JAR 构建超时（" + timeoutSeconds + " 秒）\nJAVA_HOME=" + javaHome + "\n" + output);
            }
            String output = readBounded(outputFile);
            if (process.exitValue() != 0) {
                String hint = "";
                if (output.contains("--release") || output.contains("无效的标记") || output.contains("invalid flag")) {
                    hint = "\n提示：Maven 未使用 JDK " + minJavaVersion
                            + "+。当前构建 JAVA_HOME=" + javaHome
                            + "，可配置 super-employee.artifact.java-home 指向 JDK 17/21。";
                }
                return new BuildResult(false, null,
                        "Maven package 执行失败（exitCode=" + process.exitValue() + "）\nJAVA_HOME="
                                + javaHome + "\n" + output + hint);
            }
            Path artifact = findArtifact(module, startedAt);
            if (artifact == null) {
                return new BuildResult(false, null,
                        "Maven package 已完成，但未找到本次生成的可部署 JAR\nJAVA_HOME="
                                + javaHome + "\n" + output);
            }
            return new BuildResult(true, artifact,
                    "JAR 构建成功：" + artifact.getFileName() + "\nJAVA_HOME=" + javaHome + "\n" + output);
        }
        catch (IOException ex) {
            String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (process == null) {
                return new BuildResult(false, null,
                        "无法启动 Maven package：" + detail + "（工作区：" + module
                                + "，JAVA_HOME=" + javaHome + "）");
            }
            return new BuildResult(false, null, "Maven package 过程异常：" + detail);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new BuildResult(false, null, "JAR 构建被中断");
        }
        finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                }
                catch (IOException ignored) {
                    // 临时构建日志由系统临时目录后续清理。
                }
            }
        }
    }

    /**
     * 为 Maven 子进程设置 JAVA_HOME，并把对应 bin 前置到 PATH。
     */
    static void configureBuildEnvironment(Map<String, String> env, Path javaHome) {
        String home = javaHome.toAbsolutePath().normalize().toString();
        env.put("JAVA_HOME", home);
        env.put("JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8");
        String bin = javaHome.resolve("bin").toAbsolutePath().normalize().toString();
        String pathKey = env.containsKey("Path") ? "Path" : "PATH";
        String current = env.getOrDefault(pathKey, "");
        env.put(pathKey, bin + (current.isBlank() ? "" : (isWindowsOs() ? ";" : ":") + current));
    }

    /**
     * 解析用于 Maven 构建的 JDK 家目录（优先配置，再当前 JVM / JAVA_HOME，再本机常见安装路径）。
     */
    Path resolveBuildJavaHome() {
        if (!configuredJavaHome.isBlank()) {
            Path configured = normalizeJdkHome(Path.of(configuredJavaHome));
            if (configured != null && isUsableJdk(configured, minJavaVersion)) {
                return configured;
            }
        }
        List<Path> candidates = new ArrayList<>();
        String runtimeHome = System.getProperty("java.home");
        if (runtimeHome != null && !runtimeHome.isBlank()) {
            candidates.add(Path.of(runtimeHome));
            Path parent = Path.of(runtimeHome).getParent();
            if (parent != null) {
                candidates.add(parent);
            }
        }
        String envHome = System.getenv("JAVA_HOME");
        if (envHome != null && !envHome.isBlank()) {
            candidates.add(Path.of(envHome));
        }
        candidates.addAll(discoverInstalledJdks());
        return candidates.stream()
                .map(this::normalizeJdkHome)
                .filter(path -> path != null && isUsableJdk(path, minJavaVersion))
                .max(Comparator.comparingInt(RepairArtifactService::majorVersion))
                .orElse(null);
    }

    private List<Path> discoverInstalledJdks() {
        List<Path> roots = new ArrayList<>();
        if (isWindowsOs()) {
            roots.add(Path.of("C:\\Program Files\\Java"));
            roots.add(Path.of("C:\\Program Files\\Eclipse Adoptium"));
            roots.add(Path.of("C:\\Program Files\\Microsoft"));
            roots.add(Path.of("C:\\Program Files\\Amazon Corretto"));
        }
        else {
            roots.add(Path.of("/usr/lib/jvm"));
            roots.add(Path.of("/Library/Java/JavaVirtualMachines"));
        }
        List<Path> found = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                for (Path child : stream) {
                    String name = child.getFileName() == null ? "" : child.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.contains("jdk") || name.startsWith("java-") || name.contains("temurin")
                            || name.contains("corretto") || name.contains("msopenjdk")) {
                        found.add(child);
                        Path contentsHome = child.resolve("Contents").resolve("Home");
                        if (Files.isDirectory(contentsHome)) {
                            found.add(contentsHome);
                        }
                    }
                }
            }
            catch (IOException ignored) {
                // skip unreadable install roots
            }
        }
        return found;
    }

    private Path normalizeJdkHome(Path candidate) {
        if (candidate == null) {
            return null;
        }
        Path path = candidate.toAbsolutePath().normalize();
        if (Files.isRegularFile(path.resolve("bin").resolve(isWindowsOs() ? "javac.exe" : "javac"))) {
            return path;
        }
        // java.home 有时指向 jre
        Path parent = path.getParent();
        if (parent != null
                && Files.isRegularFile(parent.resolve("bin").resolve(isWindowsOs() ? "javac.exe" : "javac"))) {
            return parent;
        }
        Path contentsHome = path.resolve("Contents").resolve("Home");
        if (Files.isRegularFile(contentsHome.resolve("bin").resolve("javac"))) {
            return contentsHome;
        }
        return null;
    }

    static boolean isUsableJdk(Path javaHome, int minMajor) {
        return javaHome != null && majorVersion(javaHome) >= minMajor
                && Files.isRegularFile(javaHome.resolve("bin").resolve(
                isWindowsOs() ? "javac.exe" : "javac"));
    }

    static int majorVersion(Path javaHome) {
        if (javaHome == null) {
            return -1;
        }
        Path release = javaHome.resolve("release");
        if (Files.isRegularFile(release)) {
            try {
                for (String line : Files.readAllLines(release, StandardCharsets.UTF_8)) {
                    if (line.startsWith("JAVA_VERSION=")) {
                        String raw = line.substring("JAVA_VERSION=".length()).trim().replace("\"", "");
                        return parseMajorVersion(raw);
                    }
                }
            }
            catch (IOException ignored) {
                // fall through to directory name heuristic
            }
        }
        Path name = javaHome.getFileName();
        return name == null ? -1 : parseMajorVersion(name.toString());
    }

    static int parseMajorVersion(String version) {
        if (version == null || version.isBlank()) {
            return -1;
        }
        Matcher matcher = JAVA_VERSION_PATTERN.matcher(version.trim());
        if (!matcher.find()) {
            return -1;
        }
        int first = Integer.parseInt(matcher.group(1));
        // 旧版 1.8.x
        if (first == 1 && matcher.group(2) != null) {
            return Integer.parseInt(matcher.group(2));
        }
        return first;
    }

    /**
     * 将任务上下文中的 {@code artifactPath} 解析为可下载资源，并校验仍位于工作区内。
     *
     * @param task 故障任务
     * @return JAR 文件资源
     * @throws IllegalStateException 未生成制品、路径越界或不存在
     */
    public Resource downloadableArtifact(IncidentTask task) {
        String raw = String.valueOf(task.getContext().getOrDefault("artifactPath", ""));
        String workspacePath = String.valueOf(task.getContext().getOrDefault("workspacePath", ""));
        if (raw.isBlank() || workspacePath.isBlank()) {
            throw new IllegalStateException("当前事件尚未生成可下载的 JAR");
        }
        Path workspace = workspaceSettings.resolveWorkspace(workspacePath).toAbsolutePath().normalize();
        Path artifact = Path.of(raw).toAbsolutePath().normalize();
        if (!artifact.startsWith(workspace) || !artifact.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")
                || !Files.isRegularFile(artifact)) {
            throw new IllegalStateException("JAR 制品不存在或已超出任务工作区");
        }
        return new FileSystemResource(artifact);
    }

    /**
     * 将受控部署器生成的三个脚本（backup/start/deploy）打包为 ZIP 供人工一键部署。
     *
     * @param task 故障任务（上下文需含 {@code deploymentScripts} 与 {@code workspacePath}）
     * @return ZIP 文件资源
     * @throws IllegalStateException 脚本缺失、越界或打包失败
     */
    public Resource downloadableDeploymentScripts(IncidentTask task) {
        String raw = String.valueOf(task.getContext().getOrDefault("deploymentScripts", ""));
        String workspacePath = String.valueOf(task.getContext().getOrDefault("workspacePath", ""));
        if (raw.isBlank() || workspacePath.isBlank()) {
            throw new IllegalStateException("当前事件尚未生成部署脚本");
        }
        Path workspace = workspaceSettings.resolveWorkspace(workspacePath).toAbsolutePath().normalize();
        Path directory = Path.of(raw).toAbsolutePath().normalize();
        if (!directory.startsWith(workspace) || !Files.isDirectory(directory)) {
            throw new IllegalStateException("部署脚本不存在或已超出任务工作区");
        }
        Path zip = directory.resolve("java-deployment-scripts.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String name : List.of("backup.sh", "start.sh", "deploy.sh")) {
                Path script = directory.resolve(name);
                if (!Files.isRegularFile(script)) {
                    throw new IllegalStateException("部署脚本缺失：" + name);
                }
                output.putNextEntry(new ZipEntry(name));
                Files.copy(script, output);
                output.closeEntry();
            }
        }
        catch (IOException ex) {
            throw new IllegalStateException("部署脚本打包失败：" + ex.getMessage(), ex);
        }
        return new FileSystemResource(zip);
    }

    /**
     * 在模块目录下查找构建开始后生成的可部署 JAR（排除 sources/javadoc/original）。
     *
     * @param module    Maven 模块根
     * @param startedAt 构建开始时间（用于过滤旧制品）
     * @return 最新匹配的 JAR 路径；未找到时为 {@code null}
     * @throws IOException 遍历文件系统失败
     */
    private Path findArtifact(Path module, Instant startedAt) throws IOException {
        try (Stream<Path> paths = Files.walk(module, 8)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/target/"))
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return !name.startsWith("original-") && !name.endsWith("-sources.jar")
                                && !name.endsWith("-javadoc.jar");
                    })
                    .filter(path -> {
                        try {
                            return !Files.getLastModifiedTime(path).toInstant().isBefore(startedAt.minusSeconds(2));
                        }
                        catch (IOException ignored) {
                            return false;
                        }
                    })
                    .max(Comparator.comparing(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toInstant();
                        }
                        catch (IOException ignored) {
                            return Instant.EPOCH;
                        }
                    }))
                    .orElse(null);
        }
    }

    /**
     * 删除可能已损坏的 compiler 增量状态，避免 clean 前读取失败。
     */
    private void purgeStaleCompilerStatus(Path module) {
        Path statusRoot = module.resolve("target").resolve("maven-status").resolve("maven-compiler-plugin");
        if (!Files.isDirectory(statusRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(statusRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                }
                catch (IOException ignored) {
                    // best-effort；后续 clean package 会再清一次
                }
            });
        }
        catch (IOException ignored) {
            // best-effort
        }
    }

    /**
     * 读取构建日志文件并截断到 {@link #MAX_OUTPUT_CHARS}。
     * <p>Windows 中文环境 Maven/控制台常为 GBK，JVM 默认 UTF-8 时 {@code Files.readString}
     * 会抛 {@code MalformedInputException: Input length = 1}，故按多编码容错解码。</p>
     *
     * @param outputFile 临时日志路径
     * @return 有界日志文本
     * @throws IOException 读取失败
     */
    private String readBounded(Path outputFile) throws IOException {
        if (!Files.exists(outputFile)) {
            return "";
        }
        String output = decodeProcessOutput(Files.readAllBytes(outputFile));
        return output.length() <= MAX_OUTPUT_CHARS
                ? output
                : output.substring(0, MAX_OUTPUT_CHARS) + "\n…构建输出已截断";
    }

    /**
     * 解码子进程输出：优先 UTF-8，失败再试 GBK / 系统默认；最终以替换字符兜底。
     */
    static String decodeProcessOutput(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        List<Charset> candidates = isWindowsOs()
                ? List.of(StandardCharsets.UTF_8, Charset.forName("GBK"), Charset.defaultCharset())
                : List.of(StandardCharsets.UTF_8, Charset.defaultCharset());
        for (Charset charset : candidates) {
            try {
                CharsetDecoder decoder = charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
                return decoder.decode(ByteBuffer.wrap(bytes)).toString();
            }
            catch (CharacterCodingException ignored) {
                // try next charset
            }
        }
        CharsetDecoder fallback = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        try {
            return fallback.decode(ByteBuffer.wrap(bytes)).toString();
        }
        catch (CharacterCodingException ex) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static boolean isWindowsOs() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * 判断当前进程是否运行在 Windows（用于选择 {@code mvn.cmd}）。
     *
     * @return 操作系统名包含 {@code win} 时返回 {@code true}
     */
    private boolean isWindows() {
        return isWindowsOs();
    }

    private ProjectBackendKind resolveBackendKind(IncidentTask task) {
        Object raw = task.getContext().get("backendKind");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            return ProjectBackendKind.from(String.valueOf(raw));
        }
        Object type = task.getContext().get("applicationType");
        if (type != null && "JAVA".equalsIgnoreCase(String.valueOf(type).trim())) {
            return ProjectBackendKind.MAVEN;
        }
        if (type != null) {
            try {
                return ProjectBackendKind.from(String.valueOf(type));
            }
            catch (RuntimeException ignored) {
                return ProjectBackendKind.MAVEN;
            }
        }
        return ProjectBackendKind.MAVEN;
    }

    private BuildResult buildMavenWar(Path module) {
        Instant startedAt = Instant.now();
        Path javaHome = resolveBuildJavaHome();
        if (javaHome == null) {
            return new BuildResult(false, null, "WAR 构建失败：未找到 JDK " + minJavaVersion + "+");
        }
        String mavenArgs = "clean package -B -DskipTests -Dfile.encoding=UTF-8 -Dproject.build.sourceEncoding=UTF-8";
        List<String> command = isWindows()
                ? List.of("cmd.exe", "/d", "/s", "/c", "mvn.cmd " + mavenArgs)
                : List.of("mvn", "clean", "package", "-B", "-DskipTests",
                        "-Dfile.encoding=UTF-8", "-Dproject.build.sourceEncoding=UTF-8");
        Process process = null;
        Path outputFile = null;
        try {
            purgeStaleCompilerStatus(module);
            outputFile = Files.createTempFile("zhiwei-war-", ".log");
            ProcessBuilder processBuilder = new ProcessBuilder(command)
                    .directory(module.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile());
            configureBuildEnvironment(processBuilder.environment(), javaHome);
            process = processBuilder.start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new BuildResult(false, null, "WAR 构建超时\n" + readBounded(outputFile));
            }
            String output = readBounded(outputFile);
            if (process.exitValue() != 0) {
                return new BuildResult(false, null, "Maven package 失败\n" + output);
            }
            Path war = findWarArtifact(module, startedAt);
            if (war == null) {
                return new BuildResult(false, null, "Maven package 完成但未找到 WAR（请确认 packaging=war）\n" + output);
            }
            return new BuildResult(true, war, "WAR 构建成功：" + war.getFileName() + "\n" + output);
        }
        catch (Exception ex) {
            return new BuildResult(false, null, "WAR 构建异常：" + ex.getMessage());
        }
        finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                }
                catch (IOException ignored) {
                }
            }
        }
    }

    private Path findWarArtifact(Path module, Instant startedAt) throws IOException {
        Path target = module.resolve("target");
        if (!Files.isDirectory(target)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(target)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".war"))
                    .max(Comparator.comparingLong(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        }
                        catch (IOException ex) {
                            return 0L;
                        }
                    }))
                    .orElse(null);
        }
    }

    private BuildResult buildGradle(Path module) {
        Instant startedAt = Instant.now();
        Path gradlew = isWindows() ? module.resolve("gradlew.bat") : module.resolve("gradlew");
        List<String> command;
        if (Files.isRegularFile(gradlew)) {
            command = isWindows()
                    ? List.of("cmd.exe", "/d", "/s", "/c", "gradlew.bat clean build -x test")
                    : List.of("./gradlew", "clean", "build", "-x", "test");
        } else {
            command = List.of("gradle", "clean", "build", "-x", "test");
        }
        Process process = null;
        Path outputFile = null;
        try {
            outputFile = Files.createTempFile("zhiwei-gradle-", ".log");
            process = new ProcessBuilder(command)
                    .directory(module.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
                    .start();
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new BuildResult(false, null, "Gradle 构建超时\n" + readBounded(outputFile));
            }
            String output = readBounded(outputFile);
            if (process.exitValue() != 0) {
                return new BuildResult(false, null, "Gradle 构建失败\n" + output);
            }
            Path artifact = findGradleArtifact(module, startedAt);
            if (artifact == null) {
                return new BuildResult(false, null, "Gradle 构建完成但未找到 JAR\n" + output);
            }
            return new BuildResult(true, artifact, "Gradle JAR 构建成功：" + artifact.getFileName() + "\n" + output);
        }
        catch (Exception ex) {
            return new BuildResult(false, null, "Gradle 构建异常：" + ex.getMessage());
        }
        finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (outputFile != null) {
                try {
                    Files.deleteIfExists(outputFile);
                }
                catch (IOException ignored) {
                }
            }
        }
    }

    private Path findGradleArtifact(Path module, Instant startedAt) throws IOException {
        Path libs = module.resolve("build").resolve("libs");
        if (!Files.isDirectory(libs)) {
            return null;
        }
        try (Stream<Path> stream = Files.list(libs)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".jar") && !name.endsWith("-sources.jar")
                                && !name.endsWith("-javadoc.jar");
                    })
                    .max(Comparator.comparingLong(path -> {
                        try {
                            return Files.getLastModifiedTime(path).toMillis();
                        }
                        catch (IOException ex) {
                            return 0L;
                        }
                    }))
                    .orElse(null);
        }
    }

    /**
     * 制品构建结果。
     *
     * @param success  是否构建成功并找到制品
     * @param artifact 可部署制品路径；失败时为 {@code null}
     * @param output   构建日志与说明
     */
    public record BuildResult(boolean success, Path artifact, String output) { }
}
