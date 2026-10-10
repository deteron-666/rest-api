package com.mixamus.restapi.entity;

import java.math.BigDecimal;

/**
 * Позиция заказа хранит снимок названия и цены товара на момент покупки.
 */
public record OrderItem(
        Long productId,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice
) {

    public static OrderItem of(Product product, int quantity) {
        return new OrderItem(
                product.id(),
                product.name(),
                quantity,
                product.price(),
                product.price().multiply(BigDecimal.valueOf(quantity)));
    }
}
