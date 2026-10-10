# REST API

REST API для управления каталогом товаров и заказами. Проект собран на Java 21 и Spring Boot 3.5.16.

## Требования

- JDK 21
- PostgreSQL на `localhost:5432` (для локального запуска, профиль `dev`)
- Доступ к Maven Central при первой сборке для загрузки Maven и зависимостей

В репозитории есть Maven Wrapper, поэтому устанавливать Maven отдельно не требуется.

## Сборка

Из корня проекта выполните:

```bash
./mvnw clean package
```

При сборке Maven Wrapper загрузит Maven 3.9.16, если он ещё не установлен локально. OpenAPI Generator создаст Java-модели и интерфейсы контроллеров из `src/main/resources/openapi/openapi.yaml`, а QueryDSL APT — Q-классы по JPA-сущностям. Сгенерированные файлы помещаются в `target/generated-sources` и не требуют ручного создания. В IntelliJ IDEA после первой сборки выполните *Maven → Reload Project*, чтобы IDE увидела эти каталоги.

## Тесты

Запуск тестов:

```bash
./mvnw test
```

## Запуск приложения

Запуск из исходников:

```bash
./mvnw spring-boot:run
```

Или запуск JAR после сборки:

```bash
java -jar target/rest-api-0.0.1-SNAPSHOT.jar
```

По умолчанию приложение доступно на `http://localhost:8080`.

## API и Swagger UI

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Спецификация OpenAPI, используемая для генерации моделей: `src/main/resources/openapi/openapi.yaml`

Основные endpoints:

| Метод | Путь | Описание |
| --- | --- | --- |
| `GET` | `/api/products` | Получить список товаров |
| `POST` | `/api/products` | Создать товар |
| `GET` | `/api/products/{productId}` | Получить товар по ID |
| `PUT` | `/api/products/{productId}` | Обновить товар |
| `DELETE` | `/api/products/{productId}` | Удалить товар |
| `GET` | `/api/orders` | Получить список заказов |
| `POST` | `/api/orders` | Создать заказ |
| `GET` | `/api/orders/{orderId}` | Получить заказ по ID |
| `POST` | `/api/orders/{orderId}/pay` | Оплатить заказ (`PENDING` → `PAID`) |
| `POST` | `/api/orders/{orderId}/cancel` | Отменить заказ (`PENDING` → `CANCELLED`), вернуть товар на склад |
| `DELETE` | `/api/orders/{orderId}` | Удалить заказ (`PENDING` или `CANCELLED`; для `PENDING` товар возвращается на склад) |
| `GET` | `/api/reports/product-sales` | Топ товаров по выручке за период: продано единиц, число заказов, выручка, средняя цена, доля в выручке, текущий остаток |

Списки поддерживают фильтры и пагинацию, общее число найденных записей возвращается в заголовке `X-Total-Count`:

- `GET /api/products?name=mouse&minPrice=10&maxPrice=100&inStock=true&page=0&size=20`
- `GET /api/orders?customerName=alex&status=PENDING&page=0&size=20`

Ошибки возвращаются в формате [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`):
`400` — невалидный запрос (поле `errors` содержит ошибки по полям), `404` — ресурс не найден,
`409` — конфликт с текущим состоянием (не хватает товара, неподходящий статус заказа).
Денежные значения передаются как десятичные числа с не более чем двумя знаками после запятой.

## База данных

Liquibase применяет миграции схемы при запуске; master changelog и миграции находятся в `src/main/resources/db/changelog`.

Слой хранения: **Spring Data JPA (Hibernate) + QueryDSL JPA**. Схемой управляет Liquibase, Hibernate только проверяет соответствие сущностей таблицам (`ddl-auto: validate`). Простые операции идут через `JpaRepository`, поиск с фильтрами — через `QuerydslPredicateExecutor`, сложные запросы (пагинация с fetch join, отчёт с агрегатами и подзапросами) — через `JPAQueryFactory`. Подробный разбор с примерами — в [docs/querydsl.md](docs/querydsl.md).

### Профили

| Профиль | База данных | Когда используется |
| --- | --- | --- |
| `dev` | PostgreSQL `localhost:5432/postgres`, пользователь `postgres`, пароль `password`, схема `rest_api` | по умолчанию, локальная разработка |
| `prod` | из переменных окружения `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | `SPRING_PROFILES_ACTIVE=prod` |
| `test` | PostgreSQL 16 во временном Docker-контейнере (Testcontainers), нужен запущенный Docker | автотесты |

В профиле `dev` схема `rest_api` создаётся автоматически при старте (`CREATE SCHEMA IF NOT EXISTS`), после чего Liquibase создаёт в ней таблицы приложения и свои служебные таблицы. Поднять PostgreSQL локально можно так:

```bash
docker run -d --name rest-api-postgres -e POSTGRES_PASSWORD=password -p 5432:5432 postgres:16-alpine
```

Пример запуска в профиле `prod`:

```bash
SPRING_PROFILES_ACTIVE=prod DB_URL=jdbc:postgresql://localhost:5432/orders DB_USERNAME=orders DB_PASSWORD=secret java -jar target/rest-api-0.0.1-SNAPSHOT.jar
```
## Анализ кода в SonarQube

Для локальной проверки в проекте есть отдельный `docker-compose.sonar.yml`: SonarQube Community и своя PostgreSQL для него. С базой приложения он не связан.

1. Запустить сервер (первый старт около минуты):

   ```bash
   docker compose -f docker-compose.sonar.yml up -d
   ```

2. Открыть <http://localhost:9000>, войти как `admin` / `admin` и сменить пароль.
3. Создать токен: *My Account → Security → Generate Tokens*, тип *Global Analysis Token*.
4. Запустить тесты и анализ:

   ```bash
   ./mvnw clean verify sonar:sonar -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<токен>
   ```

   Результат: <http://localhost:9000/dashboard?id=com.mixamus%3Arest-api>.

Настройки анализа лежат в `pom.xml` (свойства `sonar.*`): покрытие берётся из отчёта JaCoCo, а отдельные правила отключены для конкретных путей через `sonar.issue.ignore.multicriteria`. Эти исключения применяются только при анализе на сервере. SonarLint в IDE их не читает и продолжает подсвечивать такие места.

Остановить сервер: `docker compose -f docker-compose.sonar.yml down`. Удалить вместе со всеми анализами и настройками: добавить `-v`.
