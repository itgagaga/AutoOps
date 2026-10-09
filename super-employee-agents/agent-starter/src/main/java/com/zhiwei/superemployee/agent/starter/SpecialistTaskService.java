package com.zhiwei.superemployee.agent.starter;

import com.zhiwei.superemployee.agent.api.SpecialistHandler;
import com.zhiwei.superemployee.agent.api.SpecialistTaskRequest;
import com.zhiwei.superemployee.agent.api.SpecialistTaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 接收平台任务并调用本进程 {@link SpecialistHandler}。
 */
@Service
public class SpecialistTaskService {
    private static final Logger log = LoggerFactory.getLogger(SpecialistTaskService.class);

    private final SpecialistHandler handler;
    private final SpecialistRuntimeStats stats;
    private final Map<String, SpecialistTaskResult> tasks = new ConcurrentHashMap<>();

    public SpecialistTaskService(SpecialistHandler handler, SpecialistRuntimeStats stats) {
        this.handler = handler;
        this.stats = stats;
    }

    public SpecialistTaskResult submit(SpecialistTaskRequest request) {
        if (request.getTaskId() == null || request.getTaskId().isBlank()) {
            request.setTaskId(UUID.randomUUID().toString());
        }
        if (request.getAgentCode() == null || request.getAgentCode().isBlank()) {
            request.setAgentCode(handler.code());
        }
        stats.begin();
        try {
            SpecialistTaskResult result = handler.execute(request);
            if (result.getTaskId() == null) {
                result.setTaskId(request.getTaskId());
            }
            if (result.getAgentCode() == null) {
                result.setAgentCode(handler.code());
            }
            tasks.put(request.getTaskId(), result);
            if (result.isSuccess()) {
                stats.endSuccess();
            }
            else {
                stats.endFailed();
            }
            return result;
        }
        catch (RuntimeException ex) {
            log.warn("个体任务执行失败 taskId={}：{}", request.getTaskId(), ex.getMessage());
            SpecialistTaskResult failed = SpecialistTaskResult.failed(
                    request.getTaskId(), handler.code(), ex.getMessage());
            tasks.put(request.getTaskId(), failed);
            stats.endFailed();
            return failed;
        }
    }

    public SpecialistTaskResult get(String taskId) {
        return tasks.get(taskId);
    }

    public SpecialistHandler handler() {
        return handler;
    }
}
