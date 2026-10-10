package com.mixamus.restapi.repository;

import com.mixamus.restapi.entity.QOrder;
import com.mixamus.restapi.entity.QOrderItem;
import com.mixamus.restapi.entity.QProduct;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLSubQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Read-only запросы для отчётов. Возвращают проекции, а не сущности: в отчёте нечего изменять,
 * поэтому не нужно ни отслеживание изменений, ни загрузка лишних колонок.
 */
@Repository
@RequiredArgsConstructor
public class SalesReportRepository {

    private final JPAQueryFactory queryFactory;

    /**
     * Топ товаров по выручке за период. Пример сложного запроса на QueryDSL JPA:
     * JOIN, динамический WHERE, GROUP BY с агрегатами, HAVING, сортировка по агрегату,
     * некоррелированный и коррелированный подзапросы в SELECT.
     * <pre>
     * SELECT i.productId, max(i.productName), sum(i.quantity), count(distinct o.id),
     *        sum(i.totalPrice), avg(i.unitPrice),
     *        (SELECT sum(ti.totalPrice) FROM OrderItem ti JOIN ti.order tor WHERE &lt;тот же фильтр&gt;),
     *        (SELECT p.stockQuantity FROM Product p WHERE p.id = i.productId)
     * FROM OrderItem i JOIN i.order o
     * WHERE o.status IN (...) [AND o.createdAt &gt;= :from] [AND o.createdAt &lt; :to]
     * GROUP BY i.productId
     * [HAVING sum(i.totalPrice) &gt;= :minRevenue]
     * ORDER BY sum(i.totalPrice) DESC, i.productId
     * LIMIT :limit
     * </pre>
     */
    public List<ProductSalesRow> findTopProductsByRevenue(SalesReportFilter filter) {
        QOrderItem item = QOrderItem.orderItem;
        QOrder order = QOrder.order;

        // Агрегаты объявлены переменными, чтобы использовать одно выражение в SELECT, HAVING и ORDER BY
        StringExpression productName = item.productName.max();
        // QueryDSL 7: sum() типизирован явно. sum по INTEGER в JPA возвращает Long, поэтому sumLong()
        NumberExpression<Long> unitsSold = item.quantity.sumLong();
        NumberExpression<Long> ordersCount = order.id.countDistinct();
        NumberExpression<BigDecimal> revenue = item.totalPrice.sumBigDecimal();
        NumberExpression<Double> averageUnitPrice = item.unitPrice.avg();

        // Некоррелированный подзапрос: общая выручка с тем же фильтром, но по своим алиасам
        QOrderItem totalItem = new QOrderItem("totalItem");
        QOrder totalOrder = new QOrder("totalOrder");
        JPQLSubQuery<BigDecimal> totalRevenue = JPAExpressions
                .select(totalItem.totalPrice.sumBigDecimal())
                .from(totalItem)
                .join(totalItem.order, totalOrder)
                .where(matching(totalOrder, filter));

        // Коррелированный подзапрос: текущий остаток товара. Связи OrderItem -> Product нет
        // (позиция хранит снимок), поэтому соединяем по productId; для удалённого товара получим null
        QProduct product = QProduct.product;
        JPQLSubQuery<Integer> currentStock = JPAExpressions
                .select(product.stockQuantity)
                .from(product)
                .where(product.id.eq(item.productId));

        List<Tuple> rows = queryFactory
                .select(item.productId, productName, unitsSold, ordersCount,
                        revenue, averageUnitPrice, totalRevenue, currentStock)
                .from(item)
                .join(item.order, order)
                .where(matching(order, filter))
                .groupBy(item.productId)
                .having(filter.minRevenue() == null ? null : revenue.goe(filter.minRevenue()))
                .orderBy(revenue.desc(), item.productId.asc())
                .limit(filter.limit())
                .fetch();

        return rows.stream()
                .map(row -> new ProductSalesRow(
                        row.get(item.productId),
                        row.get(productName),
                        row.get(unitsSold),
                        row.get(ordersCount),
                        row.get(revenue),
                        BigDecimal.valueOf(row.get(averageUnitPrice)).setScale(2, RoundingMode.HALF_UP),
                        row.get(totalRevenue),
                        row.get(currentStock)))
                .toList();
    }

    /**
     * Фильтр строится для переданного алиаса заказа, поэтому один и тот же код
     * используется и в основном запросе, и в подзапросе.
     */
    private static Predicate matching(QOrder order, SalesReportFilter filter) {
        BooleanBuilder where = new BooleanBuilder(order.status.in(filter.statuses()));
        if (filter.from() != null) {
            where.and(order.createdAt.goe(filter.from()));
        }
        if (filter.to() != null) {
            where.and(order.createdAt.lt(filter.to()));
        }
        return where;
    }
}
