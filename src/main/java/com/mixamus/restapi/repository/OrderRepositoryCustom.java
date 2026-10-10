package com.mixamus.restapi.repository;

import com.mixamus.restapi.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Запросы, которые неудобно выразить методами Spring Data: реализованы на QueryDSL
 * в {@link OrderRepositoryCustomImpl} и подмешиваются в {@link OrderRepository}.
 */
public interface OrderRepositoryCustom {

    Page<Order> findAllWithItems(OrderFilter filter, Pageable pageable);
}
