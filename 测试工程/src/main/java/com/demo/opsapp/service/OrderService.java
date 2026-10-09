package com.demo.opsapp.service;

import com.demo.opsapp.model.Order;
import com.demo.opsapp.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 故意缺陷：空指针、除零、数值解析、并发修改。
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final UserService userService;
    private final AtomicLong seq = new AtomicLong(1000);
    private final List<Order> orders = new ArrayList<>();

    public OrderService(UserService userService) {
        this.userService = userService;
        orders.add(new Order(1L, 1L, 2, new BigDecimal("19.90")));
        orders.add(new Order(2L, 2L, 1, new BigDecimal("99.00")));
    }

    public Order create(Long userId, String quantityText, String unitPriceText) {
        User user = userService.findById(userId);
        // 修复：用户不存在时避免 NPE
        if (user == null) {
            log.warn("创建订单失败，用户不存在 userId={}", userId);
            return null;
        }
        log.info("创建订单 user={}", user.getName());

        // 修复：添加 NumberFormatException 处理，非法输入时使用默认数量
        int quantity;
        try {
            quantity = Integer.parseInt(quantityText);
        } catch (NumberFormatException e) {
            log.warn("quantity 解析失败，使用默认值 1: {}", quantityText);
            quantity = 1;
        }
        BigDecimal unitPrice;
        try {
            unitPrice = new BigDecimal(unitPriceText);
        } catch (NumberFormatException e) {
            log.warn("unitPrice 解析失败，使用默认值 0: {}", unitPriceText);
            unitPrice = BigDecimal.ZERO;
        }
        Order order = new Order(seq.incrementAndGet(), userId, quantity, unitPrice);
        orders.add(order);
        return order;
    }

    public BigDecimal averageUnitPrice() {
        BigDecimal sum = BigDecimal.ZERO;
        for (Order order : orders) {
            sum = sum.add(order.getUnitPrice());
        }
        // BUG: orders 被清空或未初始化时除零 ArithmeticException
        return sum.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);
    }

    public List<Order> clearAndSnapshot() {
        List<Order> snapshot = new ArrayList<>();
        // BUG: 遍历中修改同一 ArrayList，触发 ConcurrentModificationException
        for (Order order : orders) {
            snapshot.add(order);
            if (order.getQuantity() <= 1) {
                orders.remove(order);
            }
        }
        return snapshot;
    }

    public List<Order> listAll() {
        return List.copyOf(orders);
    }
}
