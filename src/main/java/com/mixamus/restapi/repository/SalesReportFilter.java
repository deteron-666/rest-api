package com.mixamus.restapi.repository;

import com.mixamus.restapi.entity.Order;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/**
 * @param from       начало периода включительно, null — без ограничения
 * @param to         конец периода не включительно, null — без ограничения
 * @param statuses   статусы заказов, попадающих в отчёт
 * @param minRevenue минимальная выручка по товару (условие HAVING), null — без ограничения
 * @param limit      сколько строк вернуть (топ-N по выручке)
 */
public record SalesReportFilter(
        Instant from,
        Instant to,
        Set<Order.Status> statuses,
        BigDecimal minRevenue,
        int limit
) {
}
