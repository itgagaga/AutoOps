package com.zhiwei.superemployee.core.domain;

/**
 * 故障处置优先级，数值越小表示影响越大、处置越紧急。
 * <p>
 * 创建任务时由调用方以枚举名字符串传入（如 {@code "P1"}），经 {@link IncidentPriority#valueOf} 解析。
 * @author yangdb
 */
public enum IncidentPriority {
    /** 最高优先级：核心业务中断或大面积影响。 */
    P0,
    /** 高优先级：重要服务受损。 */
    P1,
    /** 中优先级：局部或可降级影响。 */
    P2,
    /** 低优先级：轻微问题或计划内处置。 */
    P3
}
