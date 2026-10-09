package com.zhiwei.superemployee.app.system;

import com.zhiwei.superemployee.core.workspace.MavenWorkspacePaths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地 Maven / CodeX 工作区配置服务。
 * <p>
 * 生效优先级：界面写入数据库的配置优先，其次 {@code application.yml} 中的默认值。
 * 工作区可为含 {@code pom.xml} 的目录，或其子目录含 {@code pom.xml} 的父目录（前后端同仓场景）。
 * </p>
 * @author yangdb
 */
@Service
public class WorkspaceSettingsService {
    /** 数据库中存储本地代码工作区路径的配置键。 */
    public static final String KEY_WORKSPACE = "local.code.workspace";

    private final SystemSettingRepository settings;
    private final String yamlDefault;

    /**
     * 构造工作区配置服务。
     *
     * @param settings    系统键值配置仓储
     * @param yamlDefault YAML 中配置的默认工作区路径（可为空）
     */
    public WorkspaceSettingsService(SystemSettingRepository settings,
                                    @Value("${super-employee.codex.workspace:${super-employee.tools.working-directory:}}")
                                    String yamlDefault) {
        this.settings = settings;
        this.yamlDefault = yamlDefault == null ? "" : yamlDefault.trim();
    }

    /**
     * 返回当前生效的工作区配置及来源说明。
     *
     * @return 含 {@code workspacePath}、{@code storedPath}、{@code yamlDefaultPath}、
     *         {@code source}（DATABASE/YAML）、{@code validMavenProject} 的只读 Map
     */
    public Map<String, Object> current() {
        String stored = settings.find(KEY_WORKSPACE).orElse(null);
        String effective = firstNonBlank(stored, yamlDefault);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workspacePath", effective);
        result.put("storedPath", stored);
        result.put("yamlDefaultPath", yamlDefault);
        result.put("source", stored != null && !stored.isBlank() ? "DATABASE" : "YAML");
        result.put("validMavenProject", isMavenProject(effective));
        return result;
    }

    /**
     * 保存工作区路径；路径须为本目录或子目录含 {@code pom.xml} 的 Maven 工程。
     *
     * @param workspacePath 用户选择的工作区绝对路径
     * @return 保存后的当前生效配置（同 {@link #current()}）
     * @throws IllegalArgumentException 路径无效或非 Maven 工程时由校验层抛出
     */
    public Map<String, Object> save(String workspacePath) {
        Path path = requireMavenProject(workspacePath);
        settings.upsert(KEY_WORKSPACE, path.toString(), Instant.now().toString());
        return current();
    }

    /**
     * 解析 CodeX / Maven 共用的系统级工作区路径。
     *
     * @return 规范化后的 Maven 工作区 {@link Path}
     * @throws IllegalArgumentException 未配置或路径无效时
     */
    public Path requireWorkspace() {
        String effective = firstNonBlank(settings.find(KEY_WORKSPACE).orElse(null), yamlDefault);
        return requireMavenProject(effective);
    }

    /**
     * 校验并解析为含 Maven 工程的路径。
     *
     * @param value 候选工作区路径字符串
     * @return 规范化后的路径
     * @throws IllegalArgumentException 路径为空、不存在或不含 {@code pom.xml} 时
     */
    public static Path requireMavenProject(String value) {
        return MavenWorkspacePaths.requireContainsMaven(value);
    }

    /**
     * 解析实际工作区：优先使用任务级路径，否则回退到系统/YAML 配置。
     *
     * @param taskWorkspacePath 任务覆盖的工作区路径，可为空
     * @return 规范化后的 Maven 工作区路径
     * @throws IllegalArgumentException 路径无效时
     */
    public Path resolveWorkspace(String taskWorkspacePath) {
        if (taskWorkspacePath != null && !taskWorkspacePath.isBlank()) {
            return requireMavenProject(taskWorkspacePath);
        }
        return requireWorkspace();
    }

    /**
     * 解析用于执行 Maven 命令的模块目录（可能落在工作区子目录）。
     *
     * @param taskWorkspacePath 任务覆盖的工作区路径，可为空
     * @return 含 {@code pom.xml} 的模块目录
     * @throws IllegalArgumentException 工作区无效时
     */
    public Path resolveMavenModule(String taskWorkspacePath) {
        return MavenWorkspacePaths.resolveMavenModule(resolveWorkspace(taskWorkspacePath));
    }

    /**
     * 判断给定路径是否为合法 Maven 工程（吞掉校验异常）。
     *
     * @param value 候选路径
     * @return 合法时 {@code true}，否则 {@code false}
     */
    private boolean isMavenProject(String value) {
        try {
            requireMavenProject(value);
            return true;
        }
        catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * 返回第一个非空白字符串；都空白时返回空串。
     *
     * @param first  优先值
     * @param second 回退值
     * @return 去首尾空白后的结果
     */
    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }
}
