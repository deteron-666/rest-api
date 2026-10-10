package com.mixamus.restapi.repository;

import com.mixamus.restapi.entity.Order;
import com.mixamus.restapi.entity.QOrder;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
class OrderRepositoryCustomImpl implements OrderRepositoryCustom {

    private static final QOrder order = QOrder.order;

    private final JPAQueryFactory queryFactory;

    /**
     * Пагинация вместе с JOIN FETCH коллекции — известная ловушка: Hibernate не может применить
     * LIMIT к строкам join и пагинирует в памяти (предупреждение HHH90003004).
     * Поэтому два шага: страница id заказов с LIMIT/OFFSET, затем сами заказы с позициями по этим id.
     */
    @Override
    public Page<Order> findAllWithItems(OrderFilter filter, Pageable pageable) {
        Predicate predicate = matching(filter);

        List<Long> ids = queryFactory.select(order.id)
                .from(order)
                .where(predicate)
                .orderBy(order.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        List<Order> orders = ids.isEmpty()
                ? List.of()
                : queryFactory.selectFrom(order)
                        .distinct()
                        .leftJoin(order.items).fetchJoin()
                        .where(order.id.in(ids))
                        .orderBy(order.id.asc())
                        .fetch();

        // COUNT выполняется, только если по первой странице total нельзя посчитать без запроса
        return PageableExecutionUtils.getPage(orders, pageable, () -> queryFactory.select(order.count())
                .from(order)
                .where(predicate)
                .fetchOne());
    }

    private static Predicate matching(OrderFilter filter) {
        BooleanBuilder where = new BooleanBuilder();
        if (StringUtils.hasText(filter.customerName())) {
            where.and(order.customerName.containsIgnoreCase(filter.customerName().trim()));
        }
        if (filter.status() != null) {
            where.and(order.status.eq(filter.status()));
        }
        return where;
    }
}
