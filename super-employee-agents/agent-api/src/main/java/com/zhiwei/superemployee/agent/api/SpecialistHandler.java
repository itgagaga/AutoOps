package com.zhiwei.superemployee.agent.api;

/**
 * 独立 Agent 工程实现的业务处理器。
 */
public interface SpecialistHandler {
    /** 管理台 / spawn 使用的 agent_id。 */
    String code();

    /** 简短描述（供协调器 Catalog）。 */
    String description();

    /** 系统提示词（个体 Agent 自身职责说明）。 */
    String sysPrompt();

    /**
     * 同步执行一次协同任务。
     *
     * @param request 平台下发的任务
     * @return 执行摘要与结构化数据
     */
    SpecialistTaskResult execute(SpecialistTaskRequest request);
}
