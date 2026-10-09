package com.demo.opsapp.job;

import com.demo.opsapp.service.InventoryService;
import com.demo.opsapp.service.OrderService;
import com.demo.opsapp.service.ReportService;
import com.demo.opsapp.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时自动踩坑，保证不手动调接口时日志里也会持续出现 ERROR/Exception，
 * 方便智维平台「拉取日志 → 判异常 → 根因分析 → 本地修复」。
 */
@Component
@ConditionalOnProperty(prefix = "demo.fault-injector", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FaultInjectorJob {

    private static final Logger log = LoggerFactory.getLogger(FaultInjectorJob.class);

    private final UserService userService;
    private final OrderService orderService;
    private final ReportService reportService;
    private final InventoryService inventoryService;
    private int tick;

    public FaultInjectorJob(UserService userService,
                            OrderService orderService,
                            ReportService reportService,
                            InventoryService inventoryService) {
        this.userService = userService;
        this.orderService = orderService;
        this.reportService = reportService;
        this.inventoryService = inventoryService;
    }

    @Scheduled(
            initialDelayString = "${demo.fault-injector.initial-delay-seconds:15}000",
            fixedDelayString = "${demo.fault-injector.interval-seconds:30}000"
    )
    public void inject() {
        int caseId = tick++ % 6;
        try {
            switch (caseId) {
                case 0 -> {
                    log.info("fault-injector case=NPE userId=999");
                    userService.maskEmail(999L);
                }
                case 1 -> {
                    log.info("fault-injector case=NumberFormatException");
                    orderService.create(1L, "abc", "10.00");
                }
                case 2 -> {
                    log.info("fault-injector case=DateTimeParseException");
                    reportService.parseBizDate("2026-07-31");
                }
                case 3 -> {
                    log.info("fault-injector case=IndexOutOfBoundsException");
                    reportService.page(99, 20);
                }
                case 4 -> {
                    log.info("fault-injector case=NoSuchFileException");
                    inventoryService.loadTemplate();
                }
                default -> {
                    log.info("fault-injector case=StringIndexOutOfBoundsException");
                    inventoryService.parseSkuCode("A");
                }
            }
            log.warn("fault-injector unexpected success caseId={}", caseId);
        }
        catch (Exception ex) {
            // 显式打 ERROR，匹配运维平台 ERROR_PATTERN: error|exception|fatal|severe|panic|oom
            log.error("fault-injector triggered exception caseId={} type={}",
                    caseId, ex.getClass().getName(), ex);
        }
    }
}
