package com.mixamus.restapi.repository;

import static com.querydsl.core.group.GroupBy.groupBy;
import static com.querydsl.core.group.GroupBy.list;

import com.mixamus.restapi.entity.Order;
import com.mixamus.restapi.entity.OrderItem;
import com.mixamus.restapi.generated.querydsl.QOrderItems;
import com.mixamus.restapi.generated.querydsl.QOrders;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.sql.SQLQuery;
import com.querydsl.sql.SQLQueryFactory;
import com.querydsl.sql.dml.SQLInsertClause;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private static final QOrders order = QOrders.orders;
    private static final QOrderItems orderItem = QOrderItems.orderItems;

    private static final ConstructorExpression<OrderItem> ORDER_ITEM = Projections.constructor(OrderItem.class,
            orderItem.productId, orderItem.productName, orderItem.quantity,
            orderItem.unitPrice, orderItem.totalPrice);

    private final SQLQueryFactory queryFactory;

    /**
     * Пагинация применяется к заказам, а не к строкам JOIN с позициями:
     * сначала выбирается страница заказов, затем одним запросом их позиции.
     */
    public List<Order> findAll(OrderFilter filter, long offset, int limit) {
        List<Tuple> rows = selectOrders()
                .where(toPredicate(filter))
                .orderBy(order.id.asc())
                .offset(offset)
                .limit(limit)
                .fetch();
        return withItems(rows);
    }

    public long count(OrderFilter filter) {
        Long count = queryFactory.select(order.count())
                .from(order)
                .where(toPredicate(filter))
                .fetchOne();
        return count == null ? 0 : count;
    }

    public Optional<Order> findById(Long id) {
        return withItems(selectOrders().where(order.id.eq(id)).fetch())
                .stream()
                .findFirst();
    }

    public Order insert(Order newOrder) {
        Long id = queryFactory.insert(order)
                .set(order.customerName, newOrder.customerName())
                .set(order.status, newOrder.status().name())
                .set(order.totalAmount, newOrder.totalAmount())
                .executeWithKey(order.id);
        if (id == null) {
            throw new IllegalStateException("Database did not return the generated order ID");
        }

        // Все позиции вставляются одним batch-запросом
        SQLInsertClause insertItems = queryFactory.insert(orderItem);
        for (OrderItem item : newOrder.items()) {
            insertItems.set(orderItem.orderId, id)
                    .set(orderItem.productId, item.productId())
                    .set(orderItem.productName, item.productName())
                    .set(orderItem.quantity, item.quantity())
                    .set(orderItem.unitPrice, item.unitPrice())
                    .set(orderItem.totalPrice, item.totalPrice())
                    .addBatch();
        }
        if (!newOrder.items().isEmpty()) {
            insertItems.execute();
        }
        return newOrder.withId(id);
    }

    /**
     * Переход статуса с проверкой текущего значения в том же UPDATE (optimistic check):
     * два параллельных запроса не смогут, например, одновременно оплатить и отменить заказ.
     *
     * @return false, если заказа нет или его статус уже не {@code expected}
     */
    public boolean updateStatus(Long id, Order.Status expected, Order.Status newStatus) {
        return queryFactory.update(order)
                .set(order.status, newStatus.name())
                .where(order.id.eq(id), order.status.eq(expected.name()))
                .execute() > 0;
    }

    /**
     * Позиции удаляются каскадно (ON DELETE CASCADE).
     */
    public boolean delete(Long id) {
        return queryFactory.delete(order)
                .where(order.id.eq(id))
                .execute() > 0;
    }

    private SQLQuery<Tuple> selectOrders() {
        return queryFactory.select(order.id, order.customerName, order.status, order.totalAmount)
                .from(order);
    }

    private List<Order> withItems(List<Tuple> orderRows) {
        if (orderRows.isEmpty()) {
            return List.of();
        }
        List<Long> orderIds = orderRows.stream().map(row -> row.get(order.id)).toList();

        // GroupBy-трансформация QueryDSL: плоский результат -> Map<orderId, List<OrderItem>>
        Map<Long, List<OrderItem>> itemsByOrderId = queryFactory.from(orderItem)
                .where(orderItem.orderId.in(orderIds))
                .orderBy(orderItem.id.asc())
                .transform(groupBy(orderItem.orderId).as(list(ORDER_ITEM)));

        return orderRows.stream()
                .map(row -> new Order(
                        row.get(order.id),
                        row.get(order.customerName),
                        Order.Status.valueOf(row.get(order.status)),
                        row.get(order.totalAmount),
                        itemsByOrderId.getOrDefault(row.get(order.id), List.of())))
                .toList();
    }

    private static Predicate toPredicate(OrderFilter filter) {
        BooleanBuilder where = new BooleanBuilder();
        if (StringUtils.hasText(filter.customerName())) {
            where.and(order.customerName.containsIgnoreCase(filter.customerName().trim()));
        }
        if (filter.status() != null) {
            where.and(order.status.eq(filter.status().name()));
        }
        return where;
    }
}
