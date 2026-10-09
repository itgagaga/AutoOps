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
 * 按端侧工程画像校验客户端工作区。
 * @author yangdb
 */
public final class ClientWorkspacePaths {
    public static final int MAX_DEPTH = 4;

    private static final Set<String> SKIP_DIR_NAMES = Set.of(
            "node_modules", ".git", ".svn", ".idea", ".vscode",
            "target", "dist", "build", "out", "coverage", "vendor", "__pycache__",
            "unpackage", "miniprogram_npm"
    );

    private ClientWorkspacePaths() {
    }

    /**
     * 校验并解析端侧工程根目录；{@link ProjectClientKind#NONE} 时返回 null。
     */
    public static Path requireClient(ProjectClientKind kind, String value) {
        if (kind == null || kind == ProjectClientKind.NONE) {
            return null;
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("已选择端侧类型「" + kind.displayName() + "」，请配置端侧工程目录");
        }
        Path path = Path.of(MavenWorkspacePaths.normalizeExternalPath(value)).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("端侧工程路径不是目录或不存在：" + path);
        }
        Path module = findClientModule(kind, path);
        if (module == null) {
            throw new IllegalArgumentException(missingMarkerMessage(kind, path));
        }
        return module;
    }

    public static Path findClientModule(ProjectClientKind kind, Path root) {
        return findClientModule(kind, root, MAX_DEPTH);
    }

    /**
     * 在指定深度内查找端侧工程根目录。
     */
    public static Path findClientModule(ProjectClientKind kind, Path root, int maxDepth) {
        if (kind == null || kind == ProjectClientKind.NONE || root == null || !Files.isDirectory(root)) {
            return null;
        }
        int depth = Math.max(0, maxDepth);
        if (kind.usesNodePackageJson()) {
            return NodeWorkspacePaths.findNodeModule(root, depth);
        }
        if (kind == ProjectClientKind.MP_WEIXIN_NATIVE) {
            return findByMarkers(root, depth, "project.config.json", "app.json");
        }
        return null;
    }

    private static String missingMarkerMessage(ProjectClientKind kind, Path path) {
        return switch (kind) {
            case WEB_NPM, UNI_APP -> "端侧工程或其子目录中未找到 package.json：" + path;
            case MP_WEIXIN_NATIVE -> "微信原生小程序工程或其子目录中未找到 project.config.json / app.json：" + path;
            case NONE -> "未配置端侧工程";
        };
    }

    private static Path findByMarkers(Path root, int maxDepth, String... markers) {
        Path normalized = root.toAbsolutePath().normalize();
        if (hasAnyMarker(normalized, markers)) {
            return normalized;
        }
        if (maxDepth <= 0) {
            return null;
        }
        List<Path> frontier = listChildDirs(normalized);
        for (int depth = 1; depth <= maxDepth && !frontier.isEmpty(); depth++) {
            List<Path> next = new ArrayList<>();
            for (Path dir : frontier) {
                if (hasAnyMarker(dir, markers)) {
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

    private static boolean hasAnyMarker(Path dir, String... markers) {
        for (String marker : markers) {
            if (Files.isRegularFile(dir.resolve(marker))) {
                return true;
            }
        }
        return false;
    }

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

    private static boolean shouldSkip(Path dir) {
        Path name = dir.getFileName();
        if (name == null) {
            return true;
        }
        String value = name.toString().toLowerCase(Locale.ROOT);
        return SKIP_DIR_NAMES.contains(value) || value.startsWith(".");
    }
}
