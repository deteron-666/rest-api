package com.mixamus.restapi.repository;

import com.mixamus.restapi.entity.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long>, OrderRepositoryCustom {

    /**
     * Заказ сразу вместе с позициями одним запросом (LEFT JOIN FETCH), без lazy-догрузки.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);
}
