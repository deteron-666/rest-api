# JPA + QueryDSL в этом проекте

## Кто за что отвечает

```
Controller ──► Service ──► Spring Data репозиторий ──► Hibernate (JPA) ──► JDBC ──► PostgreSQL
                              │
                              ├─ JpaRepository            CRUD: findById, save, delete
                              ├─ QuerydslPredicateExecutor findAll(Predicate, Pageable) без реализации
                              ├─ @Query / @EntityGraph / @Lock  точечные запросы
                              └─ *CustomImpl + JPAQueryFactory  сложные запросы на QueryDSL
```

- **Liquibase** создаёт и меняет схему. Hibernate её только проверяет (`ddl-auto: validate`):
  если сущность разойдётся с таблицами, приложение не запустится.
- **Hibernate (JPA)** превращает строки таблиц в объекты-сущности и обратно, отслеживает изменения
  (dirty checking) и сам выполняет INSERT/UPDATE/DELETE при коммите.
- **Spring Data JPA** избавляет от шаблонного кода репозиториев.
- **QueryDSL JPA** позволяет строить JPQL-запросы типобезопасно, через Java-код.

## Как появляются Q-классы

При JPA база для генерации не нужна. Annotation processor `querydsl-apt` (classifier `jpa`)
при компиляции читает классы `@Entity` и создаёт рядом с ними `QProduct`, `QOrder` и `QOrderItem`
(`target/generated-sources/annotations`). Настройка — в `maven-compiler-plugin` в `pom.xml`.

```java
public class QProduct extends EntityPathBase<Product> {
    public static final QProduct product = new QProduct("product");
    public final NumberPath<Long> id = createNumber("id", Long.class);
    public final StringPath name = createString("name");
    public final NumberPath<BigDecimal> price = createNumber("price", BigDecimal.class);
    public final NumberPath<Integer> stockQuantity = createNumber("stockQuantity", Integer.class);
}
```

Переименовали поле в сущности — Q-класс перегенерируется, и все запросы со старым именем
перестанут компилироваться.

## Примеры из кода, от простого к сложному

### 1. Поиск без реализации: `QuerydslPredicateExecutor`

`ProductRepository extends JpaRepository<Product, Long>, QuerydslPredicateExecutor<Product>`.
Фильтр собирается в `ProductPredicates`, а пагинацию и COUNT делает Spring Data:

```java
Page<Product> page = productRepository.findAll(
        ProductPredicates.matching(filter),            // BooleanBuilder с заданными условиями
        PageRequest.of(page, size, Sort.by("id")));
```

### 2. Пессимистическая блокировка: `@Lock` + `@Query`

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from Product p where p.id in :ids order by p.id")
List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
// PostgreSQL: SELECT ... ORDER BY id FOR NO KEY UPDATE
```

Остаток проверяется и уменьшается в самой сущности (`Product.decreaseStock`), а UPDATE Hibernate
выполняет при коммите. Блокировка строк не даёт параллельным заказам продать больше, чем есть
на складе, а порядок по `id` исключает deadlock.

### 3. Optimistic locking: `@Version`

`Order.version` — Hibernate выполняет `UPDATE orders ... WHERE id = ? AND version = ?`.
Если заказ одновременно оплачивают и отменяют, один запрос получит
`ObjectOptimisticLockingFailureException` (HTTP 409), и изменения не затрут друг друга.

### 4. Заказ с позициями одним запросом: `@EntityGraph`

```java
@EntityGraph(attributePaths = "items")
Optional<Order> findWithItemsById(Long id);   // LEFT JOIN FETCH order_items
```

### 5. Кастомный фрагмент репозитория: пагинация с `fetchJoin`

`OrderRepository extends ..., OrderRepositoryCustom`, а реализация лежит в `OrderRepositoryCustomImpl`.
Spring Data находит её по суффиксу `Impl` и подмешивает в репозиторий.

`LIMIT` вместе с `JOIN FETCH` коллекции — известная ловушка: Hibernate не может ограничить строки
join и пагинирует **в памяти** (предупреждение HHH90003004). Поэтому запросов два:

```java
List<Long> ids = queryFactory.select(order.id).from(order)
        .where(predicate).orderBy(order.id.asc())
        .offset(pageable.getOffset()).limit(pageable.getPageSize())
        .fetch();

List<Order> orders = queryFactory.selectFrom(order).distinct()
        .leftJoin(order.items).fetchJoin()
        .where(order.id.in(ids))
        .fetch();
```

Тест `orderListLoadsItemsWithoutNPlusOneQueries` по статистике Hibernate проверяет, что страница
из 5 заказов загружается за 3 запроса, а не за 1 + 5 (N+1).

### 6. Сложный запрос: отчёт о продажах

`GET /api/reports/product-sales?from=&to=&status=&minRevenue=&limit=`, реализация —
`SalesReportRepository.findTopProductsByRevenue`. Для каждого товара за период считаются
проданные единицы, число заказов, выручка, средняя цена, доля в общей выручке и текущий остаток.

```java
StringExpression productName        = item.productName.max();
NumberExpression<Long> unitsSold    = item.quantity.sumLong();
NumberExpression<Long> ordersCount  = order.id.countDistinct();
NumberExpression<BigDecimal> revenue = item.totalPrice.sumBigDecimal();

// некоррелированный подзапрос с тем же фильтром, но своими алиасами
QOrderItem totalItem = new QOrderItem("totalItem");
QOrder totalOrder = new QOrder("totalOrder");
JPQLSubQuery<BigDecimal> totalRevenue = JPAExpressions
        .select(totalItem.totalPrice.sumBigDecimal())
        .from(totalItem).join(totalItem.order, totalOrder)
        .where(matching(totalOrder, filter));

// коррелированный подзапрос: связь с Product идёт по productId, а не через @ManyToOne
JPQLSubQuery<Integer> currentStock = JPAExpressions
        .select(product.stockQuantity).from(product)
        .where(product.id.eq(item.productId));

queryFactory.select(item.productId, productName, unitsSold, ordersCount,
                revenue, item.unitPrice.avg(), totalRevenue, currentStock)
        .from(item).join(item.order, order)
        .where(matching(order, filter))                         // динамический WHERE
        .groupBy(item.productId)
        .having(filter.minRevenue() == null ? null : revenue.goe(filter.minRevenue()))
        .orderBy(revenue.desc(), item.productId.asc())          // сортировка по агрегату
        .limit(filter.limit())
        .fetch();
```

SQL, который Hibernate сгенерировал на PostgreSQL:

```sql
select oi1_0.product_id, max(oi1_0.product_name), sum(oi1_0.quantity), count(distinct o1_0.id),
       sum(oi1_0.total_price), avg(oi1_0.unit_price),
       (select sum(oi2_0.total_price) from rest_api.order_items oi2_0
          join rest_api.orders o2_0 on o2_0.id = oi2_0.order_id
         where o2_0.status in (?, ?) and o2_0.created_at >= ?),
       (select p1_0.stock_quantity from rest_api.products p1_0 where p1_0.id = oi1_0.product_id)
from rest_api.order_items oi1_0
join rest_api.orders o1_0 on o1_0.id = oi1_0.order_id
where o1_0.status in (?, ?) and o1_0.created_at >= ?
group by oi1_0.product_id
order by sum(oi1_0.total_price) desc, oi1_0.product_id
fetch first ? rows only
```

Что в этом примере показывает пользу QueryDSL:

- **Агрегаты — переменные.** `revenue` объявлен один раз и используется в SELECT, HAVING и ORDER BY.
  В строковом JPQL выражение пришлось бы повторять трижды.
- **Фильтр — функция от алиаса.** `matching(QOrder order, filter)` переиспользуется и в основном
  запросе, и в подзапросе. В строке пришлось бы копировать условия и следить за именами параметров.
- **Необязательные части исчезают сами.** `having(null)` и пустые условия в `BooleanBuilder` просто
  не попадают в SQL — никакой склейки `" AND ..."`.
- **Типы проверяет компилятор.** `revenue.goe(BigDecimal)` нельзя сравнить со строкой, а
  `row.get(unitsSold)` сразу возвращает `Long`.

Отчёт возвращает проекцию (`Tuple` → `ProductSalesRow`), а не сущности: в отчёте нечего изменять,
поэтому не нужны ни dirty checking, ни лишние колонки.

## Где что использовать

| Задача | Инструмент |
| --- | --- |
| CRUD по id | `JpaRepository` |
| Простой фильтр по 1–2 полям | метод Spring Data (`findByStatus`) |
| Много необязательных фильтров + пагинация | `QuerydslPredicateExecutor` + `BooleanBuilder` |
| Блокировки, точечный JPQL | `@Query` + `@Lock` |
| Связи без N+1 | `@EntityGraph` или `fetchJoin()` |
| Отчёты, агрегаты, подзапросы, проекции | `JPAQueryFactory` в custom-фрагменте или отдельном read-репозитории |
| То, чего нет в JPQL (оконные функции, CTE, `ON CONFLICT`) | native query или `JdbcTemplate` в той же транзакции |

## Подводные камни JPA, которые учтены в проекте

- **N+1.** Коллекции загружаются через `@EntityGraph`/`fetchJoin()`, а страховка —
  `hibernate.default_batch_fetch_size: 100`.
- **`open-in-view: false`.** Сессия Hibernate не живёт до конца HTTP-запроса, поэтому lazy-загрузка
  не может незаметно сработать при сериализации JSON. Всё нужное загружается в сервисе, а DTO
  собирается внутри транзакции.
- **Bulk-операции.** `update(...)`/`delete(...)` в JPQL проходят мимо persistence context, и
  загруженные сущности становятся устаревшими. Поэтому остаток меняется в сущности после блокировки,
  а не массовым UPDATE.
- **`IDENTITY` и batch insert.** С `GenerationType.IDENTITY` Hibernate не умеет вставлять пачкой.
  Для таблиц с массовой вставкой стоит перейти на последовательности (`SEQUENCE` + `allocationSize`).

## Особенности QueryDSL 7 (форк OpenFeign)

Используется `io.github.openfeign.querydsl` 7.x — поддерживаемый форк: оригинальный `com.querydsl`
не развивается с 2023 года. Пакеты остались прежними (`com.querydsl.*`), поэтому Spring Data
(`QuerydslPredicateExecutor`) работает без изменений. Отличия от примеров в интернете для 5.x:

- вместо `sum()` — типизированные `sumLong()`, `sumBigDecimal()`, `sumDouble()`;
- `JPAExpressions.select(...)` возвращает `JPQLSubQuery<T>`, а не `JPQLQuery<T>`.
