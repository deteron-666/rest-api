package com.mixamus.restapi.repository;

import java.math.BigDecimal;

/**
 * Строка отчёта о продажах товара.
 *
 * @param totalRevenue выручка по всем товарам за тот же период (для доли товара)
 * @param currentStock текущий остаток, null — товар уже удалён из каталога
 */
public record ProductSalesRow(
        Long productId,
        String productName,
        long unitsSold,
        long ordersCount,
        BigDecimal revenue,
        BigDecimal averageUnitPrice,
        BigDecimal totalRevenue,
        Integer currentStock
) {
}
