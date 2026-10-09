package com.zhiwei.superemployee.app.runtime;

/**
 * 可被协调器调度的个体智能体描述（来自 managed_agent + 在线心跳）。
 */
public record SpecialistDescriptor(
        String id,
        String code,
        String name,
        String description,
        String endpointUrl,
        String runtimeStatus
) {
    public boolean online() {
        return runtimeStatus != null
                && !"OFFLINE".equalsIgnoreCase(runtimeStatus)
                && !"ERROR".equalsIgnoreCase(runtimeStatus);
    }
}
