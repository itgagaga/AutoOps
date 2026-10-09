package com.zhiwei.superemployee.core.workspace;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maven / CodeX 工作区路径校验工具。
 * <p>
 * 允许工作区根目录或其有限深度子目录内存在 {@code pom.xml}，
 * 以便前后端同仓场景把父目录交给 CodeX 分析，同时 Maven 仍定位到真实模块目录。
 * 被 {@link com.zhiwei.superemployee.core.gateway.CommandIncidentToolGateway} 等用于解析执行目录。
 * @author yangdb
 */
public final class MavenWorkspacePaths {
    /** 向下探测 pom.xml 的最大深度（相对工作区根）。 */
    public static final int MAX_POM_DEPTH = 4;

    /** BFS 时跳过的目录名（依赖、构建产物、IDE 与隐藏目录等）。 */
    private static final Set<String> SKIP_DIR_NAMES = Set.of(
            "node_modules", ".git", ".svn", ".idea", ".vscode",
            "target", "dist", "build", "out", "coverage", "vendor", "__pycache__"
    );

    /**
     * 工具类不可实例化。
     */
    private MavenWorkspacePaths() {
    }

    /**
     * 校验路径为目录，且本目录或有限深度子目录内存在 pom.xml。
     *
     * @param value 外部配置的工作区路径字符串
     * @return 规范化后的绝对路径
     * @throws IllegalArgumentException 路径为空、非目录或不含 pom.xml 时
     */
    public static Path requireContainsMaven(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("请在运维任务中配置本地代码工作区（目录内或其子目录需含 pom.xml）");
        }
        Path path = Path.of(normalizeExternalPath(value)).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("工作区路径不是目录或不存在：" + path);
        }
        if (findMavenModule(path) == null) {
            throw new IllegalArgumentException("工作区或其子目录中未找到 pom.xml：" + path);
        }
        return path;
    }

    /**
     * 兼容从命令行、IDE 或配置文件复制的带引号路径。
     * 引号只允许作为完整路径的首尾包裹符，内部字符保持不变。
     *
     * @param value 可能带中英文引号的路径字符串
     * @return 剥离首尾包裹引号并 trim 后的路径
     */
    /** 供 Node / 其它工作区工具复用的外部路径规范化。 */
    public static String normalizeExternalPath(String value) {
        String normalized = value.trim();
        boolean changed;
        do {
            changed = false;
            if (normalized.length() >= 2
                    && ((normalized.startsWith("\"") && normalized.endsWith("\""))
                    || (normalized.startsWith("'") && normalized.endsWith("'"))
                    || (normalized.startsWith("“") && normalized.endsWith("”"))
                    || (normalized.startsWith("‘") && normalized.endsWith("’")))) {
                normalized = normalized.substring(1, normalized.length() - 1).trim();
                changed = true;
            }
        }
        while (changed);
        return normalized;
    }

    /**
     * 判断本目录或其有限深度子目录是否含 pom.xml。
     *
     * @param directory 待检查目录
     * @return 能解析到 Maven 模块时为 {@code true}
     */
    public static boolean containsMaven(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return false;
        }
        return findMavenModule(directory) != null;
    }

    /**
     * 解析用于执行 Maven 命令的模块目录：优先工作区根下的 pom.xml，
     * 否则 BFS 查找最近的子模块 pom。
     *
     * @param workspace 工作区根路径
     * @return 含 pom.xml 的模块目录
     * @throws IllegalArgumentException 找不到 pom.xml 时
     */
    public static Path resolveMavenModule(Path workspace) {
        Path module = findMavenModule(workspace);
        if (module == null) {
            throw new IllegalArgumentException("本地验证工作目录或其子目录中必须包含 pom.xml");
        }
        return module;
    }

    /**
     * 返回含 pom.xml 的目录；优先根目录，否则有限深度 BFS（深度上限 {@link #MAX_POM_DEPTH}）。
     *
     * @param root 搜索起点
     * @return 模块目录，未找到时返回 {@code null}
     */
    public static Path findMavenModule(Path root) {
        return findMavenModule(root, MAX_POM_DEPTH);
    }

    /**
     * 返回含 pom.xml 的目录，可指定最大探测深度。
     *
     * @param root     搜索起点
     * @param maxDepth 相对 root 的最大向下深度，{@code 0} 仅检查 root 自身
     * @return 模块目录，未找到或参数非法时返回 {@code null}
     */
    public static Path findMavenModule(Path root, int maxDepth) {
        if (root == null || !Files.isDirectory(root) || maxDepth < 0) {
            return null;
        }
        Path normalized = root.toAbsolutePath().normalize();
        if (hasPom(normalized)) {
            return normalized;
        }
        if (maxDepth == 0) {
            return null;
        }
        List<Path> frontier = listChildDirs(normalized);
        for (int depth = 1; depth <= maxDepth && !frontier.isEmpty(); depth++) {
            List<Path> next = new ArrayList<>();
            for (Path dir : frontier) {
                if (hasPom(dir)) {
                    return dir;
                }
                if (depth < maxDepth) {
                    next.addAll(listChildDirs(dir));
                }
            }
            frontier = next;
        }
        return null;
    }

    /**
     * 判断目录下是否存在常规文件 {@code pom.xml}。
     *
     * @param dir 目录
     * @return 存在 pom.xml 时为 {@code true}
     */
    private static boolean hasPom(Path dir) {
        return Files.isRegularFile(dir.resolve("pom.xml"));
    }

    /**
     * 是否应在 BFS 中跳过该子目录（隐藏目录或构建/依赖目录）。
     *
     * @param dir 子目录
     * @return 应跳过时为 {@code true}
     */
    private static boolean shouldSkip(Path dir) {
        Path name = dir.getFileName();
        if (name == null) {
            return true;
        }
        String value = name.toString().toLowerCase(Locale.ROOT);
        return SKIP_DIR_NAMES.contains(value) || value.startsWith(".");
    }

    /**
     * 列出未跳过且非符号链接的子目录，按目录名忽略大小写排序。
     *
     * @param dir 父目录
     * @return 子目录列表；IO 失败时返回空列表
     */
    private static List<Path> listChildDirs(Path dir) {
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                if (Files.isDirectory(child) && !shouldSkip(child) && !Files.isSymbolicLink(child)) {
                    children.add(child);
                }
            }
        }
        catch (IOException ignored) {
            return List.of();
        }
        children.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
        return children;
    }
}
