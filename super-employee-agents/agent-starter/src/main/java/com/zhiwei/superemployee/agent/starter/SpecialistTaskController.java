package com.zhiwei.superemployee.agent.starter;

import com.zhiwei.superemployee.agent.api.SpecialistTaskRequest;
import com.zhiwei.superemployee.agent.api.SpecialistTaskResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 个体 Agent 对外任务 API。
 */
@RestController
@RequestMapping("/v1")
public class SpecialistTaskController {
    private final SpecialistTaskService tasks;

    public SpecialistTaskController(SpecialistTaskService tasks) {
        this.tasks = tasks;
    }

    @PostMapping("/tasks")
    public SpecialistTaskResult create(@RequestBody SpecialistTaskRequest request) {
        return tasks.submit(request);
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<SpecialistTaskResult> get(@PathVariable String taskId) {
        SpecialistTaskResult result = tasks.get(taskId);
        return result == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(result);
    }

    @GetMapping("/meta")
    public Map<String, String> meta() {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("code", tasks.handler().code());
        meta.put("description", tasks.handler().description());
        meta.put("sysPrompt", tasks.handler().sysPrompt());
        return meta;
    }
}
