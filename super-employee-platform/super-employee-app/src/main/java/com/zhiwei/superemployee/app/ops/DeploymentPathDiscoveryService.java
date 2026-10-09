package com.zhiwei.superemployee.app.ops;

import com.zhiwei.superemployee.app.ssh.ManagedSshConnector;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通过受管 SSH/SFTP 浏览候选目录，并识别 Java 应用当前实际部署位置。
 * <p>
 * 所有探测均为只读操作，不会创建目录、停止进程或修改远端文件。
 * 调用浏览/识别前会先通过 {@link ManagedSshConnector#requireEnabledHost(String)}
 * 校验主机存在且已启用，避免对非法主机发起连接。
 * </p>
 * @author yangdb
 */
@Service
public class DeploymentPathDiscoveryService {
    /** 从 {@code java -jar} 命令行解析 JAR 绝对路径。 */
    private static final Pattern JAVA_JAR =
            Pattern.compile("(?:^|\\s)-jar\\s+(?:\"([^\"]+)\"|'([^']+)'|(\\S+))");
    /** 应用编码白名单：字母数字开头，允许 ._-，最长 64。 */
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{1,63}");
    /** 单次识别允许的最大候选目录数。 */
    private static final int MAX_CANDIDATES = 20;

    private final ManagedSshConnector connector;
    private final long timeoutSeconds;

    /**
     * @param connector      受管 SSH 连接器（含主机启用校验）
     * @param timeoutSeconds 远端命令超时秒数，最小按 5 秒兜底
     */
    public DeploymentPathDiscoveryService(
            ManagedSshConnector connector,
            @Value("${super-employee.ssh.command-timeout-seconds:60}") long timeoutSeconds) {
        this.connector = connector;
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
    }

    /**
     * 列出目标主机指定位置下可进入的子目录，供运维任务表单逐级选择。
     *
     * @param hostId 资源中心主机 ID
     * @param path   远端路径；空则从根目录 {@code /} 开始
     * @return 当前路径、父路径与子目录条目
     * @throws IllegalArgumentException 主机不存在或路径不安全
     * @throws IllegalStateException    主机未启用或 SFTP 读取失败
     */
    public DirectoryListing listDirectories(String hostId, String path) {
        connector.requireEnabledHost(hostId);
        String remotePath = normalizePath(path == null || path.isBlank() ? "/" : path);
        try (SSHClient ssh = connector.open(hostId); SFTPClient sftp = ssh.newSFTPClient()) {
            List<DirectoryEntry> entries = sftp.ls(remotePath).stream()
                    .filter(RemoteResourceInfo::isDirectory)
                    .filter(item -> !".".equals(item.getName()) && !"..".equals(item.getName()))
                    .map(item -> new DirectoryEntry(item.getName(), join(remotePath, item.getName())))
                    .sorted(Comparator.comparing(DirectoryEntry::name, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            return new DirectoryListing(remotePath, parentOf(remotePath), entries);
        }
        catch (IOException ex) {
            throw new IllegalStateException("读取服务器部署目录失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 在候选目录中识别唯一实际部署目录。
     * <p>运行进程证据优先，其次为平台标准布局、匹配应用编码的 JAR 和平台目录标记。
     * 同分多候选时判定为歧义失败。</p>
     *
     * @param hostId          主机 ID
     * @param applicationCode 应用编码，用于匹配 {@code current/{code}.jar} 等
     * @param candidates      用户勾选的候选绝对路径
     * @return 识别结果（是否成功、路径、说明、各候选证据）
     */
    public DetectionResult detect(String hostId, String applicationCode, List<String> candidates) {
        connector.requireEnabledHost(hostId);
        String code = requireCode(applicationCode);
        List<String> paths = normalizeCandidates(candidates);
        if (paths.isEmpty()) {
            return new DetectionResult(false, null, "请至少选择一个候选部署目录", List.of());
        }
        try (SSHClient ssh = connector.open(hostId); SFTPClient sftp = ssh.newSFTPClient()) {
            List<String> runningJars = runningJarPaths(ssh);
            List<CandidateMatch> matches = paths.stream()
                    .map(path -> inspect(sftp, path, code, runningJars))
                    .toList();
            return selectBest(matches);
        }
        catch (IOException ex) {
            throw new IllegalStateException("识别应用部署目录失败：" + ex.getMessage(), ex);
        }
    }

    /**
     * 校验、规范化并去重候选绝对路径。
     *
     * @param candidates 原始候选列表，允许 {@code null}
     * @return 规范化后的绝对路径列表（保序去重）
     * @throws IllegalArgumentException 数量超限、根目录或路径含 {@code ..} 等不安全内容
     */
    public List<String> normalizeCandidates(List<String> candidates) {
        if (candidates == null) {
            return List.of();
        }
        if (candidates.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException("候选部署目录最多 " + MAX_CANDIDATES + " 个");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String candidate : candidates) {
            String path = normalizePath(candidate);
            if ("/".equals(path)) {
                throw new IllegalArgumentException("不能将服务器根目录作为部署候选目录");
            }
            normalized.add(path);
        }
        return List.copyOf(normalized);
    }

    /**
     * 对单个候选目录采集运行进程、标准 JAR、匹配 JAR 与平台标记证据并打分。
     *
     * @param sftp        已打开的 SFTP 客户端
     * @param base        候选部署绝对路径
     * @param code        已校验的应用编码
     * @param runningJars 当前主机上 {@code java -jar} 解析出的 JAR 绝对路径
     * @return 该候选的分值与证据说明
     */
    private CandidateMatch inspect(SFTPClient sftp, String base, String code, List<String> runningJars) {
        int runningScore = runningScore(base, runningJars);
        boolean standardJar = isRegularFile(sftp, join(join(base, "current"), code + ".jar"));
        boolean matchingJar = containsMatchingJar(sftp, base, code)
                || containsMatchingJar(sftp, join(base, "current"), code);
        boolean standardWar = isRegularFile(sftp, join(base, code + ".war"));
        boolean matchingWar = containsMatchingWar(sftp, base, code);
        boolean marker = isDirectory(sftp, join(base, ".zhiwei"));
        List<String> jars = listDeployJars(sftp, base);
        CandidateMatch match = scoreCandidate(base, runningScore, standardJar, matchingJar, marker, jars);
        if (standardWar || matchingWar) {
            List<String> evidence = new ArrayList<>(match.evidence());
            int score = match.score();
            if (standardWar) {
                score = Math.max(score, 85);
                evidence.add("存在 Tomcat webapps 标准 WAR：{applicationCode}.war");
            } else {
                score = Math.max(score, 65);
                evidence.add("存在名称匹配应用编码的 WAR");
            }
            return new CandidateMatch(match.path(), score, List.copyOf(evidence), match.jars());
        }
        return match;
    }

    /**
     * 将远端证据转换为稳定分值，便于测试和后续扩展识别规则。
     * <p>布尔版运行证据映射为满分 100；供单测直接断言打分逻辑。</p>
     *
     * @param base        候选部署绝对路径
     * @param running     是否存在直接位于该目录的运行 JAR
     * @param standardJar 是否存在 {@code current/{code}.jar}
     * @param matchingJar 是否存在名称包含应用编码的 JAR
     * @param marker      是否存在 {@code .zhiwei} 平台目录标记
     * @return 打分后的候选匹配结果
     */
    CandidateMatch scoreCandidate(
            String base, boolean running, boolean standardJar, boolean matchingJar, boolean marker) {
        return scoreCandidate(base, running ? 100 : 0, standardJar, matchingJar, marker, List.of());
    }

    /**
     * 按证据优先级累加（取各证据最高分）并生成可读证据列表。
     * <p>运行进程分值最高，其次标准 JAR、匹配 JAR、平台标记。</p>
     *
     * @param base         候选部署绝对路径
     * @param runningScore 运行进程相关分值（0、90 或 100）
     * @param standardJar  是否存在标准部署 JAR
     * @param matchingJar  是否存在名称匹配的 JAR
     * @param marker       是否存在平台部署标记目录
     * @param jars         该目录下发现的可部署 JAR 相对路径（如 {@code current/app.jar}）
     * @return 含路径、分值、证据与 JAR 列表的匹配结果
     */
    private CandidateMatch scoreCandidate(
            String base, int runningScore, boolean standardJar, boolean matchingJar, boolean marker,
            List<String> jars) {
        List<String> evidence = new ArrayList<>();
        int score = 0;
        if (runningScore > 0) {
            score = runningScore;
            evidence.add(runningScore == 100
                    ? "运行中的 java -jar 直接位于该部署目录"
                    : "运行中的 java -jar 位于该目录的更深层级");
        }
        if (standardJar) {
            score = Math.max(score, 80);
            evidence.add("存在标准部署文件 current/{applicationCode}.jar");
        }
        if (matchingJar) {
            score = Math.max(score, 60);
            evidence.add("存在名称匹配应用编码的 JAR");
        }
        if (marker) {
            score = Math.max(score, 20);
            evidence.add("存在 .zhiwei 平台部署标记");
        }
        return new CandidateMatch(base, score, List.copyOf(evidence), List.copyOf(jars == null ? List.of() : jars));
    }

    /**
     * 根据运行中 JAR 与候选目录的相对位置计算运行证据分值。
     * <p>JAR 直接位于候选目录或其 {@code current} 子目录时记为 100；
     * 仅作为更宽泛祖先目录时记为 90，避免父目录与实际应用目录同时被误判为最高可信。</p>
     *
     * @param base        候选部署绝对路径
     * @param runningJars 运行中 JAR 的绝对路径列表
     * @return 0、90 或 100
     */
    private int runningScore(String base, List<String> runningJars) {
        String prefix = "/".equals(base) ? "/" : base + "/";
        int score = 0;
        for (String jar : runningJars) {
            String jarDirectory = parentOf(jar);
            boolean direct = base.equals(jarDirectory)
                    || (jarDirectory != null && jarDirectory.endsWith("/current")
                    && base.equals(parentOf(jarDirectory)));
            if (direct) {
                return 100;
            }
            if (jar.startsWith(prefix)) {
                score = 90;
            }
        }
        return score;
    }

    /**
     * 从已打分候选中选出唯一最高可信路径。
     * <p>最高分大于 0 且仅有一个候选达到该分时判定成功；否则返回失败说明（无证据或歧义）。</p>
     *
     * @param matches 全部候选的匹配结果
     * @return 识别成功或失败的检测结果（始终携带全部候选明细）
     */
    DetectionResult selectBest(List<CandidateMatch> matches) {
        int highest = matches.stream().mapToInt(CandidateMatch::score).max().orElse(0);
        List<CandidateMatch> winners = matches.stream()
                .filter(item -> item.score() == highest && highest > 0)
                .toList();
        if (winners.size() == 1) {
            CandidateMatch winner = winners.get(0);
            return new DetectionResult(true, winner.path(),
                    "已唯一识别应用部署目录：" + winner.path(), matches);
        }
        String message = highest == 0
                ? "候选目录中未发现运行进程、应用 JAR 或平台部署标记"
                : "存在多个同等可信的部署目录，请选择下方路径与 JAR 进行绑定";
        return new DetectionResult(false, null, message, matches);
    }

    /**
     * 在远端执行 {@code ps}，从进程参数中解析 {@code java -jar} 的 JAR 绝对路径。
     *
     * @param ssh 已认证的 SSH 客户端
     * @return 以 {@code /} 开头的 JAR 路径列表（可能为空）
     * @throws IOException           SSH 会话或命令执行失败
     * @throws IllegalStateException 查询进程超时（退出码尚未返回）
     */
    private List<String> runningJarPaths(SSHClient ssh) throws IOException {
        try (Session session = ssh.startSession()) {
            Session.Command command = session.exec("ps -eo pid=,args=");
            command.join(timeoutSeconds, TimeUnit.SECONDS);
            if (command.getExitStatus() == null) {
                command.close();
                throw new IllegalStateException("查询 Java 运行进程超时");
            }
            String output = new String(command.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            List<String> paths = new ArrayList<>();
            for (String line : output.split("\\R")) {
                Matcher matcher = JAVA_JAR.matcher(line);
                while (matcher.find()) {
                    String value = matcher.group(1) != null ? matcher.group(1)
                            : matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
                    if (value != null && value.startsWith("/")) {
                        paths.add(value);
                    }
                }
            }
            return paths;
        }
    }

    /**
     * 判断目录下是否存在名称包含应用编码的普通 JAR（排除 sources/javadoc）。
     *
     * @param sftp      SFTP 客户端
     * @param directory 待扫描目录绝对路径
     * @param code      应用编码
     * @return 存在匹配 JAR 时为 {@code true}；目录不存在或列举失败时为 {@code false}
     */
    private boolean containsMatchingJar(SFTPClient sftp, String directory, String code) {
        if (!isDirectory(sftp, directory)) {
            return false;
        }
        try {
            String expected = code.toLowerCase();
            return sftp.ls(directory).stream()
                    .filter(item -> !item.isDirectory())
                    .map(RemoteResourceInfo::getName)
                    .map(String::toLowerCase)
                    .anyMatch(name -> name.endsWith(".jar") && name.contains(expected)
                            && !name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar"));
        }
        catch (IOException ignored) {
            return false;
        }
    }

    private boolean containsMatchingWar(SFTPClient sftp, String directory, String code) {
        if (!isDirectory(sftp, directory)) {
            return false;
        }
        try {
            String expected = code.toLowerCase();
            return sftp.ls(directory).stream()
                    .filter(item -> !item.isDirectory())
                    .map(RemoteResourceInfo::getName)
                    .map(String::toLowerCase)
                    .anyMatch(name -> name.endsWith(".war") && name.contains(expected));
        }
        catch (IOException ignored) {
            return false;
        }
    }

    /**
     * 列出候选部署目录及其 {@code current} 子目录下的可部署 JAR/WAR（相对路径）。
     */
    private List<String> listDeployJars(SFTPClient sftp, String base) {
        List<String> jars = new ArrayList<>();
        collectDeployJars(sftp, base, "", jars);
        collectDeployJars(sftp, join(base, "current"), "current/", jars);
        jars.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(jars);
    }

    private void collectDeployJars(SFTPClient sftp, String directory, String prefix, List<String> jars) {
        if (!isDirectory(sftp, directory)) {
            return;
        }
        try {
            for (RemoteResourceInfo item : sftp.ls(directory)) {
                if (item.isDirectory()) {
                    continue;
                }
                String name = item.getName();
                String lower = name.toLowerCase();
                boolean war = lower.endsWith(".war");
                boolean jar = lower.endsWith(".jar")
                        && !lower.endsWith("-sources.jar")
                        && !lower.endsWith("-javadoc.jar")
                        && !lower.startsWith("original-");
                if (!war && !jar) {
                    continue;
                }
                jars.add(prefix + name);
            }
        }
        catch (IOException ignored) {
            // 无权限或不存在时忽略，不影响其它候选打分
        }
    }

    /**
     * 判断远端路径是否为目录。
     *
     * @param sftp SFTP 客户端
     * @param path 远端绝对路径
     * @return 路径存在且类型为目录时为 {@code true}
     */
    private boolean isDirectory(SFTPClient sftp, String path) {
        FileAttributes attrs = stat(sftp, path);
        return attrs != null && attrs.getType() == FileMode.Type.DIRECTORY;
    }

    /**
     * 判断远端路径是否为普通文件。
     *
     * @param sftp SFTP 客户端
     * @param path 远端绝对路径
     * @return 路径存在且类型为普通文件时为 {@code true}
     */
    private boolean isRegularFile(SFTPClient sftp, String path) {
        FileAttributes attrs = stat(sftp, path);
        return attrs != null && attrs.getType() == FileMode.Type.REGULAR;
    }

    /**
     * 安全探测远端路径属性；不存在或 IO 失败时返回 {@code null}。
     *
     * @param sftp SFTP 客户端
     * @param path 远端绝对路径
     * @return 文件属性，或 {@code null}
     */
    private FileAttributes stat(SFTPClient sftp, String path) {
        try {
            return sftp.statExistence(path);
        }
        catch (IOException ignored) {
            return null;
        }
    }

    /**
     * 校验应用编码是否符合安全白名单。
     *
     * @param code 原始应用编码
     * @return 去首尾空白后的合法编码
     * @throws IllegalArgumentException 编码为空或不符合 {@link #SAFE_CODE} 规则
     */
    private String requireCode(String code) {
        String value = code == null ? "" : code.trim();
        if (!SAFE_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException("应用编码不符合部署安全规则");
        }
        return value;
    }

    /**
     * 将原始路径规范为安全的 Linux 绝对路径（统一斜杠、去重斜杠、去掉尾部斜杠）。
     *
     * @param raw 原始路径字符串
     * @return 规范化后的绝对路径
     * @throws IllegalArgumentException 为空、含 {@code ..}、非绝对路径、含非法字符或超长
     */
    private String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("候选部署目录不能为空");
        }
        String value = raw.trim().replace('\\', '/').replaceAll("/+", "/");
        if (!value.startsWith("/") || value.contains("..") || !value.matches("[A-Za-z0-9/_.-]+")) {
            throw new IllegalArgumentException("部署路径必须是安全的 Linux 绝对路径");
        }
        if (value.length() > 512) {
            throw new IllegalArgumentException("部署路径长度不能超过 512 个字符");
        }
        return value.length() > 1 ? value.replaceAll("/+$", "") : value;
    }

    /**
     * 拼接父目录与子名称为远端绝对路径。
     *
     * @param base 父路径（{@code /} 或其它绝对路径）
     * @param name 子目录或文件名
     * @return 拼接后的绝对路径
     */
    private String join(String base, String name) {
        return "/".equals(base) ? "/" + name : base + "/" + name;
    }

    /**
     * 计算路径的父目录。
     *
     * @param path 远端绝对路径
     * @return 父路径；根目录 {@code /} 的父路径为 {@code null}
     */
    private String parentOf(String path) {
        if ("/".equals(path)) {
            return null;
        }
        int index = path.lastIndexOf('/');
        return index <= 0 ? "/" : path.substring(0, index);
    }

    /**
     * 目录浏览中的单个子目录条目。
     *
     * @param name 目录名（不含路径）
     * @param path 该目录的绝对路径
     */
    public record DirectoryEntry(String name, String path) { }

    /**
     * 指定位置下的目录浏览结果。
     *
     * @param path       当前浏览的绝对路径
     * @param parentPath 父路径；当前为根目录时为 {@code null}
     * @param entries    可进入的子目录列表（已排序）
     */
    public record DirectoryListing(String path, String parentPath, List<DirectoryEntry> entries) { }

    /**
     * 单个候选目录的识别证据与分值。
     *
     * @param path     候选绝对路径
     * @param score    综合可信分值（越高越可信）
     * @param evidence 命中的证据说明列表
     * @param jars     目录下发现的可部署 JAR 相对路径，供歧义时人工绑定
     */
    public record CandidateMatch(String path, int score, List<String> evidence, List<String> jars) { }

    /**
     * 部署目录识别的最终结果。
     *
     * @param detected       是否已唯一识别
     * @param deploymentPath 识别成功时的实际部署绝对路径；失败时为 {@code null}
     * @param message        面向用户的成功或失败说明
     * @param candidates     全部候选的分值与证据明细
     */
    public record DetectionResult(
            boolean detected, String deploymentPath, String message, List<CandidateMatch> candidates) { }
}
