package com.zhiwei.superemployee.app.web;

import com.zhiwei.superemployee.app.ops.OpsTaskRepository;
import com.zhiwei.superemployee.app.ops.OpsTask;
import com.zhiwei.superemployee.app.security.OpsDataPermissionService;
import com.zhiwei.superemployee.core.domain.AgentStepRecord;
import com.zhiwei.superemployee.core.domain.IncidentTask;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 执行记录查询入口：按故障时间线汇总执行情况。
 * <p>
 * 路径前缀 {@code /api/agent-executions}。菜单权限：管理员或 {@code AGENT_EXECUTION}。
 * 列表仅含有时间线步骤的可见故障，并尽量关联运维任务标题/主机信息；详情按需返回完整步骤。
 * </p>
 * @author yangdb
 */
@RestController
@RequestMapping("/api/agent-executions")
@PreAuthorize("hasRole('ADMIN') or hasAuthority('AGENT_EXECUTION')")
public class AgentExecutionController {
    private final ObjectProvider<OpsTaskRepository> opsTasks;
    private final OpsDataPermissionService dataPermission;

    /**
     * @param opsTasks       运维任务仓储（可选，用于关联标题与主机）
     * @param dataPermission 运维数据权限
     */
    public AgentExecutionController(ObjectProvider<OpsTaskRepository> opsTasks,
                                    OpsDataPermissionService dataPermission) {
        this.opsTasks = opsTasks;
        this.dataPermission = dataPermission;
    }

    /**
     * 返回任务级轻量汇总（步骤数、成败、最近执行 Agent 等）；完整步骤在详情接口中按需读取。
     * 按最近执行时间倒序；仅包含有时间线的可见执行故障。
     *
     * @return 汇总视图列表
     */
    @GetMapping
    public List<Map<String, Object>> list() {
        OpsTaskRepository repository = opsTasks.getIfAvailable();
        Map<String, OpsTask> byId = new HashMap<>();
        Map<String, OpsTask> byIncidentId = new HashMap<>();
        if (repository != null) {
            for (OpsTask ops : dataPermission.visibleOpsTasks()) {
                byId.put(ops.getId(), ops);
                if (ops.getIncidentId() != null) byIncidentId.put(ops.getIncidentId(), ops);
            }
        }
        return dataPermission.visibleExecutionIncidents().stream()
                .filter(task -> !task.getTimeline().isEmpty())
                .map(task -> summary(task, resolveOpsTask(task, byId, byIncidentId)))
                .sorted(Comparator.comparing(
                        item -> (Instant) item.get("lastExecutedAt"),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    /**
     * 查询单个故障的完整执行步骤摘要（点击详情后按需加载）。
     *
     * @param incidentId 故障主键
     * @return 含 incidentId 与 steps 的详情；无权限或不存在时由权限服务抛错
     */
    @GetMapping("/{incidentId}")
    public Map<String, Object> detail(@PathVariable String incidentId) {
        IncidentTask task = dataPermission.requireIncident(incidentId);
        List<Map<String, Object>> steps = new java.util.ArrayList<>();
        int index = 0;
        for (AgentStepRecord step : task.getTimeline()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", task.getId() + "-" + index++);
            item.put("agentCode", step.getStep());
            item.put("success", step.isSuccess());
            item.put("summary", step.getSummary());
            item.put("executedAt", step.getExecutedAt());
            steps.add(item);
        }
        return Map.of("incidentId", task.getId(), "steps", steps);
    }

    /**
     * 将故障时间线折叠为列表行：成败计数、最近 Agent、关联运维任务字段等。
     *
     * @param task 故障任务
     * @param ops  关联运维任务，可为 null
     * @return 汇总 Map
     */
    private Map<String, Object> summary(IncidentTask task, OpsTask ops) {
        int successCount = 0;
        int failCount = 0;
        Instant lastExecutedAt = null;
        String lastAgentCode = null;
        LinkedHashSet<String> agentCodes = new LinkedHashSet<>();
        for (AgentStepRecord step : task.getTimeline()) {
            if (step.isSuccess()) successCount++;
            else failCount++;
            if (step.getStep() != null) agentCodes.add(step.getStep());
            if (step.getExecutedAt() != null
                    && (lastExecutedAt == null || step.getExecutedAt().isAfter(lastExecutedAt))) {
                lastExecutedAt = step.getExecutedAt();
                lastAgentCode = step.getStep();
            }
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", ops == null ? task.getId() : ops.getId());
        item.put("opsTaskId", ops == null ? null : ops.getId());
        item.put("opsTaskTitle", ops == null ? task.getTitle() : ops.getTitle());
        item.put("incidentId", task.getId());
        item.put("incidentTitle", task.getTitle());
        item.put("incidentStatus", task.getStatus() == null ? null : task.getStatus().name());
        item.put("summary", task.getLatestSummary());
        item.put("hostName", ops == null ? null : ops.getHostName());
        item.put("workspacePath", ops == null ? null : ops.getWorkspacePath());
        item.put("priority", task.getPriority() == null ? null : task.getPriority().name());
        item.put("stepCount", task.getTimeline().size());
        item.put("successCount", successCount);
        item.put("failCount", failCount);
        item.put("lastAgentCode", lastAgentCode);
        item.put("lastExecutedAt", lastExecutedAt);
        item.put("agentCodes", List.copyOf(agentCodes));
        return item;
    }

    /**
     * 解析故障关联的运维任务：优先 context.opsTaskId，其次按故障 ID 反查。
     *
     * @param task         故障任务
     * @param byId         运维任务 ID 索引
     * @param byIncidentId 故障 ID → 运维任务索引
     * @return 关联运维任务，可能为 null
     */
    private OpsTask resolveOpsTask(IncidentTask task, Map<String, OpsTask> byId,
                                   Map<String, OpsTask> byIncidentId) {
        Object opsTaskId = task.getContext().get("opsTaskId");
        OpsTask ops = opsTaskId == null ? null : byId.get(String.valueOf(opsTaskId));
        return ops == null ? byIncidentId.get(task.getId()) : ops;
    }
}
