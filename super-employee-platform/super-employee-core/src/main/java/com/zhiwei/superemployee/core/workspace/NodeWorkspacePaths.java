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
 * Node / 前端工作区路径校验：本目录或有限深度子目录须含 {@code package.json}。
 * @author yangdb
 */
public final class NodeWorkspacePaths {
    public static final int MAX_PACKAGE_DEPTH = 4;

    private static final Set<String> SKIP_DIR_NAMES = Set.of(
            "node_modules", ".git", ".svn", ".idea", ".vscode",
            "target", "dist", "build", "out", "coverage", "vendor", "__pycache__"
    );

    private NodeWorkspacePaths() {
    }

    /**
     * 校验路径为目录，且本目录或有限深度子目录内存在 package.json。
     */
    public static Path requireContainsNode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("请配置前端工程目录（目录内或其子目录需含 package.json）");
        }
        Path path = Path.of(MavenWorkspacePaths.normalizeExternalPath(value)).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("前端工程路径不是目录或不存在：" + path);
        }
        if (findNodeModule(path) == null) {
            throw new IllegalArgumentException("前端工程或其子目录中未找到 package.json：" + path);
        }
        return path;
    }

    public static boolean containsNode(Path directory) {
        return directory != null && Files.isDirectory(directory) && findNodeModule(directory) != null;
    }

    public static Path resolveNodeModule(Path workspace) {
        Path module = findNodeModule(workspace);
        if (module == null) {
            throw new IllegalArgumentException("前端工程目录或其子目录中必须包含 package.json");
        }
        return module;
    }

    public static Path findNodeModule(Path root) {
        return findNodeModule(root, MAX_PACKAGE_DEPTH);
    }

    public static Path findNodeModule(Path root, int maxDepth) {
        if (root == null || !Files.isDirectory(root) || maxDepth < 0) {
            return null;
        }
        Path normalized = root.toAbsolutePath().normalize();
        if (hasPackageJson(normalized)) {
            return normalized;
        }
        if (maxDepth == 0) {
            return null;
        }
        List<Path> frontier = listChildDirs(normalized);
        for (int depth = 1; depth <= maxDepth && !frontier.isEmpty(); depth++) {
            List<Path> next = new ArrayList<>();
            for (Path dir : frontier) {
                if (hasPackageJson(dir)) {
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

    private static boolean hasPackageJson(Path dir) {
        return Files.isRegularFile(dir.resolve("package.json"));
    }

    private static boolean shouldSkip(Path dir) {
        Path name = dir.getFileName();
        if (name == null) {
            return true;
        }
        String value = name.toString().toLowerCase(Locale.ROOT);
        return SKIP_DIR_NAMES.contains(value) || value.startsWith(".");
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
}
