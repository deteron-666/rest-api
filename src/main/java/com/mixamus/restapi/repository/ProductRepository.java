package com.mixamus.restapi.repository;

import com.mixamus.restapi.entity.Product;
import com.mixamus.restapi.generated.querydsl.QProducts;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.sql.SQLQueryFactory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

@Repository
@RequiredArgsConstructor
public class ProductRepository {

    private static final QProducts product = QProducts.products;

    /**
     * Проекция строки таблицы прямо в record: типы колонок проверяются компилятором через Q-класс.
     */
    private static final ConstructorExpression<Product> PRODUCT_PROJECTION = Projections.constructor(Product.class,
            product.id, product.name, product.description, product.price, product.stockQuantity);

    private final SQLQueryFactory queryFactory;

    public List<Product> findAll(ProductFilter filter, long offset, int limit) {
        return queryFactory.select(PRODUCT_PROJECTION)
                .from(product)
                .where(toPredicate(filter))
                .orderBy(product.id.asc())
                .offset(offset)
                .limit(limit)
                .fetch();
    }

    public long count(ProductFilter filter) {
        // Тот же предикат, что и в findAll: фильтр описан один раз и переиспользуется
        Long count = queryFactory.select(product.count())
                .from(product)
                .where(toPredicate(filter))
                .fetchOne();
        return count == null ? 0 : count;
    }

    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(queryFactory.select(PRODUCT_PROJECTION)
                .from(product)
                .where(product.id.eq(id))
                .fetchOne());
    }

    public List<Product> findAllByIds(Collection<Long> ids) {
        return queryFactory.select(PRODUCT_PROJECTION)
                .from(product)
                .where(product.id.in(ids))
                .fetch();
    }

    public Product insert(Product newProduct) {
        Long id = queryFactory.insert(product)
                .set(product.name, newProduct.name())
                .set(product.description, newProduct.description())
                .set(product.price, newProduct.price())
                .set(product.stockQuantity, newProduct.stockQuantity())
                .executeWithKey(product.id);
        if (id == null) {
            throw new IllegalStateException("Database did not return the generated product ID");
        }
        return newProduct.withId(id);
    }

    /**
     * @return false, если товара с таким id нет
     */
    public boolean update(Product changedProduct) {
        return queryFactory.update(product)
                .set(product.name, changedProduct.name())
                .set(product.description, changedProduct.description())
                .set(product.price, changedProduct.price())
                .set(product.stockQuantity, changedProduct.stockQuantity())
                .where(product.id.eq(changedProduct.id()))
                .execute() > 0;
    }

    /**
     * @return false, если товара с таким id нет
     */
    public boolean delete(Long id) {
        return queryFactory.delete(product)
                .where(product.id.eq(id))
                .execute() > 0;
    }

    /**
     * Атомарное списание: проверка остатка и уменьшение выполняются одним UPDATE,
     * поэтому параллельные заказы не могут продать больше, чем есть на складе.
     * <pre>UPDATE products SET stock_quantity = stock_quantity - ? WHERE id = ? AND stock_quantity >= ?</pre>
     *
     * @return false, если товара нет или остатка не хватает
     */
    public boolean decreaseStock(Long id, int quantity) {
        return queryFactory.update(product)
                .set(product.stockQuantity, product.stockQuantity.subtract(quantity))
                .where(product.id.eq(id), product.stockQuantity.goe(quantity))
                .execute() > 0;
    }

    public void increaseStock(Long id, int quantity) {
        queryFactory.update(product)
                .set(product.stockQuantity, product.stockQuantity.add(quantity))
                .where(product.id.eq(id))
                .execute();
    }

    /**
     * Динамический фильтр: в WHERE попадают только заданные условия.
     */
    private static Predicate toPredicate(ProductFilter filter) {
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
