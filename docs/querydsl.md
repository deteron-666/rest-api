# QueryDSL в этом проекте

## Что это

QueryDSL — библиотека для построения SQL (а также JPQL, MongoDB и др.) запросов **Java-кодом**,
а не строками. По схеме данных генерируются Q-классы, у которых каждая колонка — типизированное поле.
Запрос собирается из этих полей, и компилятор проверяет имена колонок и типы значений.

```java
// Было (JdbcTemplate): SQL в строке, ошибка в имени колонки видна только при выполнении
jdbcTemplate.query("SELECT id, name FROM products WHERE stok_quantity > ?", mapper, 0);

// Стало (QueryDSL): опечатка product.stokQuantity не скомпилируется,
// а product.stockQuantity.gt("0") не скомпилируется из-за типа
queryFactory.select(PRODUCT).from(product).where(product.stockQuantity.gt(0)).fetch();
```

В проекте используется **QueryDSL SQL** (модуль `querydsl-sql`), то есть работа напрямую с JDBC,
без JPA/Hibernate. Используется поддерживаемый форк `io.github.openfeign.querydsl` (7.x):
оригинальный `com.querydsl` не развивается с 2023 года.

## Как это работает: генерация Q-классов при сборке

Q-классы генерируются по **реальной схеме БД**, которую строят те же Liquibase-миграции,
что и в рантайме. Так Q-классы никогда не расходятся с миграциями.

```
./mvnw compile
  │
  ├─ initialize        maven-clean-plugin      удаляет target/querydsl-codegen (временную БД)
  ├─ generate-sources  openapi-generator       openapi.yaml → DTO и интерфейсы ProductsApi/OrdersApi
  ├─ generate-sources  liquibase-maven-plugin  db/changelog/*.yaml → файл H2 в target/querydsl-codegen
  ├─ generate-sources  querydsl-maven-plugin   метаданные этой H2 → QProducts, QOrders, QOrderItems
  └─ compile           javac                   компилирует код вместе со сгенерированными классами
```

Настройка — в `pom.xml`, результат — в `target/generated-sources/querydsl/com/mixamus/restapi/generated/querydsl`.

**Важное следствие:** если поменять схему (новая миграция), Q-классы обновятся при следующей
сборке, и весь код, который обращается к удалённой или переименованной колонке, перестанет
компилироваться. Без QueryDSL такая ошибка всплыла бы только в рантайме.

Пример сгенерированного класса (сокращённо):

```java
public class QProducts extends RelationalPathBase<QProducts> {
    public static final QProducts products = new QProducts("products");

    public final NumberPath<Long> id = createNumber("id", Long.class);
    public final StringPath name = createString("name");
    public final NumberPath<BigDecimal> price = createNumber("price", BigDecimal.class);
    public final NumberPath<Integer> stockQuantity = createNumber("stockQuantity", Integer.class); // stock_quantity
}
```

## Как это подключено в рантайме

`config/QuerydslConfig.java`:

- `SQLTemplatesRegistry` определяет диалект по подключению: на H2 используются `H2Templates`,
  на PostgreSQL — `PostgreSQLTemplates`. Код запросов от диалекта не зависит.
- `SpringConnectionProvider` берёт соединение **текущей Spring-транзакции**, поэтому QueryDSL
  работает с `@Transactional` так же, как `JdbcTemplate`. Вне транзакции он бросает исключение,
  поэтому все методы сервисов помечены `@Transactional` (для чтения — `readOnly = true`).
- `SpringExceptionTranslator` превращает `SQLException` в `DataAccessException` Spring
  (например, нарушение CHECK становится `DataIntegrityViolationException` → HTTP 409).

## Примеры из кода

Все примеры — из `repository/ProductRepository.java` и `repository/OrderRepository.java`.

### 1. Динамический фильтр (главное преимущество)

В WHERE попадают только заданные условия. Со строковым SQL пришлось бы склеивать
`" AND ..."` и вручную вести список параметров.

```java
BooleanBuilder where = new BooleanBuilder();
if (StringUtils.hasText(filter.name())) {
    where.and(product.name.containsIgnoreCase(filter.name().trim()));   // lower(name) like ? escape '\'
}
if (filter.minPrice() != null) {
    where.and(product.price.goe(filter.minPrice()));
}
if (filter.inStock() != null) {
    where.and(filter.inStock() ? product.stockQuantity.gt(0) : product.stockQuantity.eq(0));
}
```

Один и тот же предикат используется и для страницы (`findAll`), и для `count` (заголовок `X-Total-Count`).
Значения всегда передаются параметрами, а `%` и `_` в `containsIgnoreCase` экранируются,
поэтому SQL-инъекция или «подсунутый» шаблон LIKE невозможны (это проверяет тест).

### 2. Проекция прямо в record

```java
ConstructorExpression<Product> PRODUCT = Projections.constructor(Product.class,
        product.id, product.name, product.description, product.price, product.stockQuantity);

List<Product> page = queryFactory.select(PRODUCT).from(product)
        .where(predicate).orderBy(product.id.asc()).offset(offset).limit(limit).fetch();
```

Ручной `RowMapper` с `resultSet.getString("name")` больше не нужен.

### 3. Атомарное списание остатка (исправление гонки)

```java
queryFactory.update(product)
        .set(product.stockQuantity, product.stockQuantity.subtract(quantity))
        .where(product.id.eq(id), product.stockQuantity.goe(quantity))
        .execute() > 0;
// UPDATE products SET stock_quantity = stock_quantity - ? WHERE id = ? AND stock_quantity >= ?
```

Выражения над колонками (`subtract`, `add`, `goe`) тоже типобезопасны.

### 4. Insert с получением ключа и batch

```java
Long id = queryFactory.insert(order)
        .set(order.customerName, ...)
        .executeWithKey(order.id);

SQLInsertClause insertItems = queryFactory.insert(orderItem);
for (OrderItem item : items) {
    insertItems.set(orderItem.orderId, id).set(...).addBatch();
}
insertItems.execute();   // один batch вместо N запросов
```

### 5. Группировка результата: GroupBy

```java
Map<Long, List<OrderItem>> itemsByOrderId = queryFactory.from(orderItem)
        .where(orderItem.orderId.in(orderIds))
        .transform(groupBy(orderItem.orderId).as(list(ORDER_ITEM)));
```

Плоский результат превращается в `Map<orderId, List<OrderItem>>` без ручного `LinkedHashMap`
и `computeIfAbsent`, как это было в `ResultSetExtractor`.

## Преимущества

| | JdbcTemplate + строки | QueryDSL SQL |
| --- | --- | --- |
| Опечатка в колонке | ошибка в рантайме | ошибка компиляции |
| Неверный тип параметра | ошибка в рантайме | ошибка компиляции |
| Изменилась схема | ищем строки руками | компилятор показывает все места |
| Динамический WHERE | склейка строк и списка параметров | `BooleanBuilder`, условия — объекты |
| Переиспользование условий | копипаста | предикат — обычная переменная или метод |
| Маппинг результата | ручной `RowMapper` | `Projections.constructor/bean/fields` |
| Автодополнение в IDE | нет | по полям Q-классов |
| Диалекты SQL | пишем под конкретную БД | `limit/offset`, функции и т.п. генерируются под БД |

По сравнению с **JPA Criteria API** QueryDSL заметно короче и читабельнее. По сравнению с **Spring Data**
derived queries (`findByNameContainingAndPriceGreaterThan...`) он хорошо масштабируется на
произвольные комбинации фильтров.

## Недостатки и о чём помнить

- **Сложнее сборка:** нужен шаг генерации Q-классов (здесь Liquibase → H2 → codegen).
  H2 для генерации должна понимать миграции; специфичный для PostgreSQL DDL пришлось бы
  генерировать по настоящему PostgreSQL (например, через Testcontainers).
- **Ещё одна абстракция:** итоговый SQL не виден в коде. Для отладки включите
  `logging.level.com.querydsl.sql=DEBUG`.
- **Не ORM:** QueryDSL SQL не управляет связями и не отслеживает изменения сущностей.
  Вставка позиций заказа, каскады и т.п. остаются явными, как и в JdbcTemplate.
- **Очень сложный SQL** (оконные функции, CTE, специфичные функции БД) поддерживается частично.
  Можно использовать `Expressions.template(...)` или оставить такой запрос на JdbcTemplate:
  они работают в одной транзакции.

## QueryDSL с JPA

Если проект использует JPA/Hibernate, вместо `querydsl-sql` подключают `querydsl-jpa`. Тогда
Q-классы генерирует annotation processor по классам `@Entity` (база при сборке не нужна),
а в Spring Data репозиторий можно унаследовать от `QuerydslPredicateExecutor<T>`:
`productRepository.findAll(QProduct.product.price.lt(100), pageable)`.
Синтаксис запросов (`select/from/where`, `BooleanBuilder`, проекции) тот же самый.
