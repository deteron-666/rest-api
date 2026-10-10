package com.mixamus.restapi.entity;

import java.math.BigDecimal;

public record Product(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity
) {

    public Product withId(Long id) {
        return new Product(id, name, description, price, stockQuantity);
    }
}
