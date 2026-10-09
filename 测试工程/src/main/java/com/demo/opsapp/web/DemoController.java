package com.demo.opsapp.web;

import com.demo.opsapp.model.Order;
import com.demo.opsapp.service.InventoryService;
import com.demo.opsapp.service.OrderService;
import com.demo.opsapp.service.ReportService;
import com.demo.opsapp.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    private final UserService userService;
    private final OrderService orderService;
    private final ReportService reportService;
    private final InventoryService inventoryService;

    public DemoController(UserService userService,
                          OrderService orderService,
                          ReportService reportService,
                          InventoryService inventoryService) {
        this.userService = userService;
        this.orderService = orderService;
        this.reportService = reportService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/healthz")
    public Map<String, Object> healthz() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("app", "demo-ops-app");
        return body;
    }

    /** 触发 NullPointerException / StringIndexOutOfBoundsException */
    @GetMapping("/users/{id}/email-mask")
    public Map<String, String> maskEmail(@PathVariable Long id) {
        return Map.of("masked", userService.maskEmail(id));
    }

    /** 触发 NumberFormatException / NullPointerException */
    @PostMapping("/orders")
    public Order createOrder(@RequestParam Long userId,
                             @RequestParam String quantity,
                             @RequestParam(defaultValue = "10.00") String unitPrice) {
        return orderService.create(userId, quantity, unitPrice);
    }

    /** 触发 ArithmeticException（订单列表为空时） */
    @GetMapping("/orders/average-price")
    public Map<String, BigDecimal> averagePrice() {
        return Map.of("averageUnitPrice", orderService.averageUnitPrice());
    }

    /** 触发 ConcurrentModificationException */
    @PostMapping("/orders/cleanup")
    public List<Order> cleanup() {
        return orderService.clearAndSnapshot();
    }

    /** 触发 IndexOutOfBoundsException */
    @GetMapping("/reports/orders")
    public List<Order> pageOrders(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        return reportService.page(page, size);
    }

    /** 触发 DateTimeParseException */
    @GetMapping("/reports/biz-date")
    public Map<String, LocalDate> parseDate(@RequestParam String date) {
        return Map.of("bizDate", reportService.parseBizDate(date));
    }

    /** 触发 NoSuchFileException */
    @GetMapping("/inventory/template")
    public Map<String, String> inventoryTemplate() throws Exception {
        return Map.of("content", inventoryService.loadTemplate());
    }

    /** 触发 StringIndexOutOfBoundsException / NumberFormatException */
    @GetMapping("/inventory/sku/{sku}")
    public Map<String, Integer> parseSku(@PathVariable String sku) {
        return Map.of("code", inventoryService.parseSkuCode(sku));
    }
}
