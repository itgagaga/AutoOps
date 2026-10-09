package com.zhiwei.superemployee.core.workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 前后端一体修复时的 CLI 沙箱根解析：取后端与前端工作区的安全公共祖先。
 * @author yangdb
 */
public final class RepairWorkspacePaths {
    /** 公共祖先相对「较深一侧」最多允许再向上抬升的目录层数。 */
    public static final int MAX_ANCESTOR_LIFT = 3;

    private RepairWorkspacePaths() {
    }

    /**
     * 仅后端工作区时，返回规范化后的后端路径作为修码沙箱根。
     *
     * @param backendWorkspace  已通过 Maven 校验的后端工作区
     * @param frontendWorkspace 可选前端工作区；为空则忽略
     * @return CLI 修码沙箱根目录
     */
    public static Path resolveRepairRoot(Path backendWorkspace, Path frontendWorkspace) {
        if (backendWorkspace == null) {
            throw new IllegalArgumentException("后端代码工作区不能为空");
        }
        Path backend = backendWorkspace.toAbsolutePath().normalize();
        if (frontendWorkspace == null) {
            return backend;
        }
        Path frontend = frontendWorkspace.toAbsolutePath().normalize();
        if (frontend.startsWith(backend)) {
            return backend;
        }
        if (backend.startsWith(frontend)) {
            return frontend;
        }
        Path ancestor = lowestCommonAncestor(backend, frontend);
        if (ancestor == null || isTooBroad(ancestor)) {
            throw new IllegalArgumentException(
                    "后端与前端工程无法形成安全的公共父目录，请将两者放在同一父目录下再配置（例如同仓的 backend/ 与 frontend/）");
        }
        int liftBackend = depthBetween(ancestor, backend);
        int liftFrontend = depthBetween(ancestor, frontend);
        if (liftBackend > MAX_ANCESTOR_LIFT || liftFrontend > MAX_ANCESTOR_LIFT) {
            throw new IllegalArgumentException(
                    "后端与前端公共父目录过高（超过 " + MAX_ANCESTOR_LIFT
                            + " 层），存在误改无关工程风险；请缩小到更近的同级父目录");
        }
        return ancestor;
    }

    /**
     * 解析可选前端路径；空白返回 null。
     */
    public static Path optionalFrontend(String frontendWorkspacePath) {
        if (frontendWorkspacePath == null || frontendWorkspacePath.isBlank()) {
            return null;
        }
        return NodeWorkspacePaths.requireContainsNode(frontendWorkspacePath);
    }

    static Path lowestCommonAncestor(Path left, Path right) {
        List<Path> leftChain = ancestorsInclusive(left);
        for (Path candidate : ancestorsInclusive(right)) {
            if (leftChain.contains(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<Path> ancestorsInclusive(Path path) {
        List<Path> chain = new ArrayList<>();
        Path current = path.toAbsolutePath().normalize();
        while (current != null) {
            chain.add(current);
            current = current.getParent();
        }
        return chain;
    }

    private static int depthBetween(Path ancestor, Path child) {
        Path normalizedAncestor = ancestor.toAbsolutePath().normalize();
        Path current = child.toAbsolutePath().normalize();
        int depth = 0;
        while (current != null && !current.equals(normalizedAncestor)) {
            current = current.getParent();
            depth++;
        }
        return depth;
    }

    /**
     * 盘符根、Unix 根或仅一层盘符根下的目录视为过宽。
     */
    static boolean isTooBroad(Path ancestor) {
        Path path = ancestor.toAbsolutePath().normalize();
        Path parent = path.getParent();
        if (parent == null) {
            return true;
        }
        // Windows: D:\ 的 parent 可能是 null；D:\code 的 parent 是 D:\
        if (parent.getParent() == null) {
            String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
            // 允许 D:\code 这类一层目录；禁止 D:\ 本身（已由 parent==null 覆盖）
            return name.isBlank();
        }
        return false;
    }
}
