package com.mixamus.restapi.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    public enum OrderStatus {
        PENDING,
        PAID,
        CANCELLED
    }

    private Long id;

    private String customerName;

    private OrderStatus status = OrderStatus.PENDING;

    private BigDecimal totalAmount = BigDecimal.ZERO;

    private List<OrderItem> items = new ArrayList<>();
}
