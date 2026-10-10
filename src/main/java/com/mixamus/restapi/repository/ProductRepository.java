package com.mixamus.restapi.repository;

import com.mixamus.restapi.entity.Product;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;

/**
 * JpaRepository даёт CRUD, а QuerydslPredicateExecutor — findAll(Predicate, Pageable):
 * поиск по предикату QueryDSL с пагинацией и подсчётом total без единой строки реализации.
 */
public interface ProductRepository extends JpaRepository<Product, Long>, QuerydslPredicateExecutor<Product> {

    /**
     * SELECT ... FOR UPDATE в порядке возрастания id: параллельные заказы блокируют товары
     * в одном порядке (без deadlock) и ждут друг друга, пока не закончится списание.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
