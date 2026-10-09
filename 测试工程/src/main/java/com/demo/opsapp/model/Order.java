package com.demo.opsapp.model;

import java.math.BigDecimal;

public class Order {

    private final Long id;
    private final Long userId;
    private final int quantity;
    private final BigDecimal unitPrice;

    public Order(Long id, Long userId, int quantity, BigDecimal unitPrice) {
        this.id = id;
        this.userId = userId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
