package com.mixamus.restapi.repository;

import com.mixamus.restapi.entity.Order;

/**
 * Необязательные критерии поиска заказов: null означает "не фильтровать".
 */
public record OrderFilter(String customerName, Order.Status status) {

    public static OrderFilter none() {
        return new OrderFilter(null, null);
    }
}
