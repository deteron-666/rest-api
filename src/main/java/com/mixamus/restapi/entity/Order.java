package com.mixamus.restapi.entity;

import java.math.BigDecimal;
import java.util.List;

public record Order(
        Long id,
        String customerName,
        Status status,
        BigDecimal totalAmount,
        List<OrderItem> items
) {

    public enum Status {
        PENDING,
        PAID,
        CANCELLED
    }

    public Order withId(Long id) {
        return new Order(id, customerName, status, totalAmount, items);
    }

    public Order withStatus(Status status) {
        return new Order(id, customerName, status, totalAmount, items);
    }
}
