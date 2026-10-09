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
 * 按后端工程画像校验并解析工作区模块目录。
 * @author yangdb
 */
public final class BackendWorkspacePaths {
    public static final int MAX_DEPTH = 4;

    private static final Set<String> SKIP_DIR_NAMES = Set.of(
            "node_modules", ".git", ".svn", ".idea", ".vscode",
            "target", "dist", "build", "out", "coverage", "vendor", "__pycache__",
            "venv", ".venv", "unpackage", "miniprogram_npm"
    );

    private BackendWorkspacePaths() {
    }

    public static Path requireBackend(ProjectBackendKind kind, String value) {
        ProjectBackendKind backend = kind == null ? ProjectBackendKind.MAVEN : kind;
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "请配置后端代码工作区（" + backend.displayName() + "）");
        }
        Path path = Path.of(MavenWorkspacePaths.normalizeExternalPath(value)).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("工作区路径不是目录或不存在：" + path);
        }
        Path module = findBackendModule(backend, path, MAX_DEPTH);
        if (module == null) {
            throw new IllegalArgumentException(missingMarkerMessage(backend, path));
        }
        return module;
    }

    public static Path findBackendModule(ProjectBackendKind kind, Path root) {
        return findBackendModule(kind, root, MAX_DEPTH);
    }

    public static Path findBackendModule(ProjectBackendKind kind, Path root, int maxDepth) {
        if (kind == null || root == null || !Files.isDirectory(root)) {
            return null;
        }
        int depth = Math.max(0, maxDepth);
        return switch (kind) {
            case MAVEN, JAVA_WAR -> MavenWorkspacePaths.findMavenModule(root, depth);
            case GRADLE -> findByAnyMarker(root, depth, "build.gradle", "build.gradle.kts");
            case NODE -> NodeWorkspacePaths.findNodeModule(root, depth);
            case GO -> findByAnyMarker(root, depth, "go.mod");
            case PYTHON -> findByAnyMarker(root, depth, "pyproject.toml", "requirements.txt", "setup.py");
            case DOCKER -> findByAnyMarker(root, depth, "Dockerfile", "docker-compose.yml",
                    "docker-compose.yaml", "compose.yml", "compose.yaml");
        };
    }

    public static boolean hasBackendMarker(ProjectBackendKind kind, Path dir) {
        if (kind == null || dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        return switch (kind) {
            case MAVEN, JAVA_WAR -> Files.isRegularFile(dir.resolve("pom.xml"));
            case GRADLE -> Files.isRegularFile(dir.resolve("build.gradle"))
                    || Files.isRegularFile(dir.resolve("build.gradle.kts"));
            case NODE -> Files.isRegularFile(dir.resolve("package.json"));
            case GO -> Files.isRegularFile(dir.resolve("go.mod"));
            case PYTHON -> Files.isRegularFile(dir.resolve("pyproject.toml"))
                    || Files.isRegularFile(dir.resolve("requirements.txt"))
                    || Files.isRegularFile(dir.resolve("setup.py"));
            case DOCKER -> Files.isRegularFile(dir.resolve("Dockerfile"))
                    || Files.isRegularFile(dir.resolve("docker-compose.yml"))
                    || Files.isRegularFile(dir.resolve("docker-compose.yaml"))
                    || Files.isRegularFile(dir.resolve("compose.yml"))
                    || Files.isRegularFile(dir.resolve("compose.yaml"));
        };
    }

    private static String missingMarkerMessage(ProjectBackendKind kind, Path path) {
        return switch (kind) {
            case MAVEN, JAVA_WAR -> "工作区或其子目录中未找到 pom.xml：" + path;
            case GRADLE -> "工作区或其子目录中未找到 build.gradle / build.gradle.kts：" + path;
            case NODE -> "工作区或其子目录中未找到 package.json：" + path;
            case GO -> "工作区或其子目录中未找到 go.mod：" + path;
            case PYTHON -> "工作区或其子目录中未找到 pyproject.toml / requirements.txt：" + path;
            case DOCKER -> "工作区或其子目录中未找到 Dockerfile / docker-compose：" + path;
        };
    }

    private static Path findByAnyMarker(Path root, int maxDepth, String... markers) {
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
