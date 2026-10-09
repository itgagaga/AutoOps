package com.demo.opsapp.service;

import com.demo.opsapp.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;

/**
 * 故意缺陷：分页越界、日期格式写错。
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OrderService orderService;

    public ReportService(OrderService orderService) {
        this.orderService = orderService;
    }

    public List<Order> page(int page, int size) {
        List<Order> all = orderService.listAll();
        int from = page * size;
        int to = from + size;
        // 修复：添加边界检查，避免 IndexOutOfBoundsException
        if (all == null || from < 0 || to > all.size() || from > to) {
            return Collections.emptyList();
        }
        return all.subList(from, to);
    }

    public LocalDate parseBizDate(String text) {
        // 修复：使用正确的 yyyy-MM-dd 格式解析，并捕获解析异常
        if (text == null || text.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(text, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            log.warn("日期解析失败，使用当前日期: {}", text);
            return LocalDate.now();
        }
    }
}
