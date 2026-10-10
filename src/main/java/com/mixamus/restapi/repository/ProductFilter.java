package com.mixamus.restapi.repository;

import java.math.BigDecimal;

/**
 * Необязательные критерии поиска товаров: null означает "не фильтровать".
 */
public record ProductFilter(String name, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock) {

    public static ProductFilter none() {
        return new ProductFilter(null, null, null, null);
    }
}
