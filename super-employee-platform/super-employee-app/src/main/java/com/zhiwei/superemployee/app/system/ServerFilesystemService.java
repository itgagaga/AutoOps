package com.zhiwei.superemployee.app.system;

import com.zhiwei.superemployee.app.runtime.LocalGitRepositoryGateway;
import com.zhiwei.superemployee.core.workspace.BackendWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.ClientWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.MavenWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.NodeWorkspacePaths;
import com.zhiwei.superemployee.core.workspace.ProjectBackendKind;
import com.zhiwei.superemployee.core.workspace.ProjectClientKind;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 服务器本机文件系统浏览服务。
 * <p>
 * 供运维在界面上选择 CodeX / Maven 工作区：列出盘符/根目录与子目录，
 * 并标注是否含 {@code pom.xml}、是否具备 Git 元数据。仅浏览目录结构，不读取文件内容。
 * </p>
 * @author yangdb
 */
@Service
public class ServerFilesystemService {
    /** 当前目录“可选用”判定深度：允许选父目录（子目录含 pom）。 */
    private static final int CURRENT_POM_DEPTH = 2;
    /** 普通子目录探测深度：只看直接子级是否有 pom，避免层层 BFS。 */
    private static final int CHILD_POM_DEPTH = 1;
    /** 盘符根下子目录不做深度扫描，只看本目录 pom.xml。 */
    private static final int ROOT_CHILD_POM_DEPTH = 0;

    /** Windows / 通用系统目录，浏览列表中直接跳过。 */
    private static final Set<String> SKIP_BROWSE_NAMES = Set.of(
            "$recycle.bin", "system volume information", "recovery", "perflogs",
            "documents and settings", "program files", "program files (x86)",
            "programdata", "windows", "windows.old", "config.msi", "msocache",
            "intel", "amd", "nvidia", "boot", "efi", "$windows.~bt", "$windows.~ws",
            "onecache", "system reserved"
    );

    /**
     * 列出可浏览的根目录（Windows 盘符或 Unix {@code /}）。
     *
     * @return 根目录条目列表；每项含 {@code name}/{@code path}/{@code directory}/{@code hasPom}/{@code directPom}
     */
    public List<Map<String, Object>> roots() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (File root : File.listRoots()) {
            Path path = root.toPath().toAbsolutePath().normalize();
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", path.toString());
            item.put("path", path.toString());
            item.put("directory", true);
            boolean directPom = Files.isRegularFile(path.resolve("pom.xml"));
            item.put("hasPom", directPom);
            item.put("directPom", directPom);
            boolean directPkg = Files.isRegularFile(path.resolve("package.json"));
            item.put("hasPackageJson", directPkg);
            item.put("directPackageJson", directPkg);
            boolean directMini = Files.isRegularFile(path.resolve("project.config.json"))
                    || Files.isRegularFile(path.resolve("app.json"));
            item.put("hasMiniProgram", directMini);
            item.put("directMiniProgram", directMini);
            putBackendFlags(item, path, 0);
            result.add(item);
        }
        return result;
    }

    /**
     * 列出指定目录下的子目录（不含文件内容），最多返回 500 项。
     *
     * @param rawPath 待浏览目录；为空时取系统第一个根目录
     * @return 含当前路径、父路径、pom/git 标记及 {@code children} 列表的 Map
     * @throws IllegalArgumentException 路径不是目录或不存在时
     * @throws IllegalStateException    读取目录失败时
     */
    public Map<String, Object> list(String rawPath) {
        Path path = resolveBrowsable(rawPath);
        boolean atRoot = isFilesystemRoot(path);
        int childPomDepth = atRoot ? ROOT_CHILD_POM_DEPTH : CHILD_POM_DEPTH;
        List<Map<String, Object>> children = new ArrayList<>();
        try (var stream = Files.list(path)) {
            stream.filter(Files::isDirectory)
                    .filter(child -> !shouldSkipBrowse(child))
                    .sorted(Comparator.comparing(value -> value.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .limit(500)
                    .forEach(child -> children.add(dirItem(child.getFileName().toString(), child, childPomDepth)));
        }
        catch (Exception ex) {
            throw new IllegalStateException("无法读取目录：" + ex.getMessage(), ex);
        }
        Path parent = path.getParent();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", path.toString());
        result.put("parentPath", parent == null ? null : parent.toString());
        // 当前路径保留浅层扫描，支持“选择含 Maven 子工程的父目录”
        int depth = atRoot ? 0 : CURRENT_POM_DEPTH;
        putPomFlags(result, path, depth);
        putPackageFlags(result, path, depth);
        putMiniProgramFlags(result, path, depth);
        putBackendFlags(result, path, depth);
        putGitFlags(result, path);
        result.put("children", children);
        return result;
    }

    /**
     * 构造单个子目录条目（含 pom/git 标记）。
     *
     * @param name     目录名
     * @param path     目录绝对路径
     * @param pomDepth Maven 探测深度
     * @return 目录条目 Map
     */
    private Map<String, Object> dirItem(String name, Path path, int pomDepth) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("path", path.toAbsolutePath().normalize().toString());
        item.put("directory", true);
        putPomFlags(item, path, pomDepth);
        putPackageFlags(item, path, pomDepth);
        putMiniProgramFlags(item, path, pomDepth);
        putBackendFlags(item, path, pomDepth);
        putGitFlags(item, path);
        return item;
    }

    /**
     * 向条目写入 {@code hasPom}/{@code directPom} 标记。
     *
     * @param item     目标 Map
     * @param path     待探测目录
     * @param pomDepth 相对 path 的最大向下深度；0 仅检查本目录 pom.xml
     */
    private void putPomFlags(Map<String, Object> item, Path path, int pomDepth) {
        boolean directPom = Files.isRegularFile(path.resolve("pom.xml"));
        boolean hasPom = directPom
                || (pomDepth > 0 && MavenWorkspacePaths.findMavenModule(path, pomDepth) != null);
        item.put("hasPom", hasPom);
        item.put("directPom", directPom);
    }

    private void putPackageFlags(Map<String, Object> item, Path path, int depth) {
        boolean direct = Files.isRegularFile(path.resolve("package.json"));
        boolean has = direct
                || (depth > 0 && NodeWorkspacePaths.findNodeModule(path, depth) != null);
        item.put("hasPackageJson", has);
        item.put("directPackageJson", direct);
    }

    private void putMiniProgramFlags(Map<String, Object> item, Path path, int depth) {
        boolean direct = Files.isRegularFile(path.resolve("project.config.json"))
                || Files.isRegularFile(path.resolve("app.json"));
        boolean has = direct
                || (depth > 0 && ClientWorkspacePaths.findClientModule(
                        ProjectClientKind.MP_WEIXIN_NATIVE, path, depth) != null);
        item.put("hasMiniProgram", has);
        item.put("directMiniProgram", direct);
    }

    private void putBackendFlags(Map<String, Object> item, Path path, int depth) {
        for (ProjectBackendKind kind : ProjectBackendKind.values()) {
            boolean direct = BackendWorkspacePaths.hasBackendMarker(kind, path);
            boolean has = direct
                    || (depth > 0 && BackendWorkspacePaths.findBackendModule(kind, path, depth) != null);
            String flag = switch (kind) {
                case MAVEN, JAVA_WAR -> "Maven";
                case GRADLE -> "Gradle";
                case NODE -> "Node";
                case GO -> "Go";
                case PYTHON -> "Python";
                case DOCKER -> "Docker";
            };
            item.put("has" + flag, has);
            item.put("direct" + flag, direct);
        }
        // 兼容旧前端字段
        item.put("hasPom", Boolean.TRUE.equals(item.get("hasMaven")) || Boolean.TRUE.equals(item.get("hasPom")));
        item.put("directPom", Boolean.TRUE.equals(item.get("directMaven")) || Boolean.TRUE.equals(item.get("directPom")));
    }

    /**
     * 向条目写入 {@code hasGit} 标记。
     *
     * @param item 目标 Map
     * @param path 待探测目录
     */
    private void putGitFlags(Map<String, Object> item, Path path) {
        item.put("hasGit", LocalGitRepositoryGateway.isValidGitMetadata(path.resolve(".git")));
    }

    /**
     * 将原始路径解析为可浏览的绝对目录；空路径回退到系统第一个根。
     *
     * @param rawPath 用户传入路径，可为空
     * @return 规范化后的目录路径
     * @throws IllegalArgumentException 无根目录、或路径不是目录时
     */
    private Path resolveBrowsable(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            File[] roots = File.listRoots();
            if (roots == null || roots.length == 0) {
                throw new IllegalArgumentException("当前系统没有可浏览的根目录");
            }
            return roots[0].toPath().toAbsolutePath().normalize();
        }
        Path path = Path.of(rawPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("路径不是目录或不存在：" + path);
        }
        return path;
    }

    /**
     * 是否为盘符/挂载点根目录。
     *
     * @param path 规范化后的路径
     * @return 根目录时为 {@code true}
     */
    private boolean isFilesystemRoot(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        File[] roots = File.listRoots();
        if (roots == null) {
            return normalized.getParent() == null;
        }
        for (File root : roots) {
            if (normalized.equals(root.toPath().toAbsolutePath().normalize())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 浏览列表是否应跳过该子目录（系统目录、隐藏目录、.git 等）。
     *
     * @param dir 子目录
     * @return 应跳过时为 {@code true}
     */
    private boolean shouldSkipBrowse(Path dir) {
        Path fileName = dir.getFileName();
        if (fileName == null) {
            return true;
        }
        String name = fileName.toString();
        if (".git".equalsIgnoreCase(name)) {
            return true;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith("$") || lower.startsWith(".")) {
            return true;
        }
        return SKIP_BROWSE_NAMES.contains(lower);
    }
}
