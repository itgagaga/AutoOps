package com.zhiwei.superemployee.app.security;

import com.zhiwei.superemployee.app.ops.OpsTask;
import com.zhiwei.superemployee.app.ops.OpsTaskRepository;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import com.zhiwei.superemployee.core.repository.IncidentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运维业务行级数据权限门禁服务。
 * <p>
 * 运维任务、故障任务及其派生的会话、审批、制品等，均以创建人 {@code createdBy} 作为隔离键：
 * </p>
 * <ul>
 *   <li>普通用户：只能看到/操作 {@code createdBy} 与当前 JWT 用户名一致的数据；</li>
 *   <li>管理员（{@code ROLE_ADMIN}）：可访问全部数据；</li>
 *   <li>历史数据若缺少 {@code createdBy}，普通用户不可见，仅管理员可处理。</li>
 * </ul>
 * 无权访问时统一抛出 HTTP 404，避免通过差异化错误码探测他人资源是否存在。
 * @author yangdb
 */
@Service
public class OpsDataPermissionService {
    private final OpsTaskRepository opsTasks;
    private final IncidentRepository incidents;

    /**
     * 注入运维任务与故障任务仓储，供可见性查询与单条门禁使用。
     *
     * @param opsTasks  运维任务仓储
     * @param incidents 故障任务仓储
     */
    public OpsDataPermissionService(OpsTaskRepository opsTasks, IncidentRepository incidents) {
        this.opsTasks = opsTasks;
        this.incidents = incidents;
    }

    /**
     * 返回当前登录用户名。
     * <p>无认证或未认证上下文时（例如部分后台调度线程）回退为 {@code system}，
     * 此时按普通用户规则几乎看不到业务数据，避免误放行。</p>
     *
     * @return 当前用户名或 {@code system}
     */
    public String currentUsername() {
        Authentication authentication = authentication();
        return authentication == null ? "system" : authentication.getName();
    }

    /**
     * 判断当前请求是否具有管理员角色。
     *
     * @return 拥有 {@code ROLE_ADMIN} 时为 {@code true}
     */
    public boolean isAdmin() {
        Authentication authentication = authentication();
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    /**
     * 查询当前用户可见的运维任务列表（管理员全量，否则按创建人过滤）。
     *
     * @return 可见运维任务，不会返回无权数据
     */
    public List<OpsTask> visibleOpsTasks() {
        return isAdmin() ? opsTasks.findAll() : opsTasks.findAllByCreatedBy(currentUsername());
    }

    /**
     * 查询当前用户可见的完整故障任务列表。
     *
     * @return 可见故障任务
     */
    public List<IncidentTask> visibleIncidents() {
        return isAdmin() ? incidents.findAll() : incidents.findAllByCreatedBy(currentUsername());
    }

    /**
     * 查询执行记录列表所需的轻量故障数据（字段可能少于完整实体）。
     *
     * @return 可见的执行列表用故障数据
     */
    public List<IncidentTask> visibleExecutionIncidents() {
        return isAdmin()
                ? incidents.findAllForExecution()
                : incidents.findAllForExecutionByCreatedBy(currentUsername());
    }

    /**
     * 汇总当前用户可见故障 ID，供会话、审批等关联过滤。
     *
     * @return 不可变的故障 ID 集合
     */
    public Set<String> visibleIncidentIds() {
        return visibleIncidents().stream().map(IncidentTask::getId).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 按 ID 加载运维任务并校验访问权；不存在或无权统一 404。
     *
     * @param id 运维任务主键
     * @return 有权访问的运维任务
     * @throws ResponseStatusException HTTP 404
     */
    public OpsTask requireOpsTask(String id) {
        OpsTask task = opsTasks.findById(id).orElseThrow(this::notFound);
        if (!canAccess(task)) {
            throw notFound();
        }
        return task;
    }

    /**
     * 按 ID 加载故障任务并校验访问权；不存在或无权统一 404。
     *
     * @param id 故障任务主键
     * @return 有权访问的故障任务
     * @throws ResponseStatusException HTTP 404
     */
    public IncidentTask requireIncident(String id) {
        IncidentTask task = incidents.findById(id).orElseThrow(this::notFound);
        if (!canAccess(task)) {
            throw notFound();
        }
        return task;
    }

    /**
     * 校验已加载的故障实体是否属于当前用户（或管理员）。
     *
     * @param task 故障实体，允许为 {@code null}（视为无权）
     * @throws ResponseStatusException HTTP 404
     */
    public void requireIncident(IncidentTask task) {
        if (task == null || !canAccess(task)) {
            throw notFound();
        }
    }

    /**
     * 判断当前用户是否可访问指定运维任务。
     *
     * @param task 运维任务，{@code null} 视为不可访问
     * @return 管理员或创建人匹配时为 {@code true}
     */
    public boolean canAccess(OpsTask task) {
        return task != null && (isAdmin()
                || Objects.equals(currentUsername(), task.getCreatedBy()));
    }

    /**
     * 判断当前用户是否可访问指定故障任务。
     *
     * @param task 故障任务，{@code null} 视为不可访问
     * @return 管理员或创建人匹配时为 {@code true}
     */
    public boolean canAccess(IncidentTask task) {
        return task != null && (isAdmin()
                || Objects.equals(currentUsername(), task.getCreatedBy()));
    }

    /**
     * 从 SecurityContext 取出已认证主体；未登录或匿名视为无认证。
     *
     * @return 已认证的 {@link Authentication}，否则 {@code null}
     */
    private Authentication authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated() ? null : authentication;
    }

    /**
     * 构造统一的“数据不存在”响应，用于隐藏越权探测。
     *
     * @return HTTP 404 异常
     */
    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "数据不存在");
    }
}
