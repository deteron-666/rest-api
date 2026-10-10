package com.mixamus.restapi.repository;

import com.mixamus.restapi.entity.QProduct;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import org.springframework.util.StringUtils;

/**
 * Динамический фильтр товаров: в WHERE попадают только заданные условия.
 */
public final class ProductPredicates {

    private static final QProduct product = QProduct.product;

    private ProductPredicates() {
    }

    public static Predicate matching(ProductFilter filter) {
        BooleanBuilder where = new BooleanBuilder();
        if (StringUtils.hasText(filter.name())) {
            where.and(product.name.containsIgnoreCase(filter.name().trim()));
        }
        if (filter.minPrice() != null) {
            where.and(product.price.goe(filter.minPrice()));
        }
        if (filter.maxPrice() != null) {
            where.and(product.price.loe(filter.maxPrice()));
        }
        if (filter.inStock() != null) {
            where.and(filter.inStock() ? product.stockQuantity.gt(0) : product.stockQuantity.eq(0));
        }
        return where;
    }
}
