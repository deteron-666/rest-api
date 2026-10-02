# REST API

REST API для управления каталогом товаров и заказами. Проект собран на Java 21 и Spring Boot 4.

## Требования

- JDK 21
- Доступ к Maven Central при первой сборке для загрузки Maven и зависимостей

В репозитории есть Maven Wrapper, поэтому устанавливать Maven отдельно не требуется.

## Сборка

Из корня проекта выполните:

```bash
./mvnw clean package
```

При сборке Maven Wrapper загрузит Maven 3.9.16, если он ещё не установлен локально. OpenAPI Generator автоматически создаст Java-модели из `src/main/resources/openapi/openapi.yaml`; сгенерированные файлы помещаются в `target/` и не требуют ручного создания.

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

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
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
| `DELETE` | `/api/orders/{orderId}` | Удалить заказ |

## База данных

По умолчанию используется H2 в режиме совместимости с PostgreSQL. База данных хранится в памяти и создаётся заново при старте приложения. Liquibase применяет миграции схемы при запуске; master changelog и миграции находятся в `src/main/resources/db/changelog`. Hibernate проверяет схему (`ddl-auto: validate`), но не изменяет её. H2 Console доступна по адресу <http://localhost:8080/h2-console>; параметры подключения указаны в `src/main/resources/application.yaml`.