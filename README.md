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

[SonarQube](https://www.sonarsource.com/products/sonarqube/) — статический анализатор: ищет баги (возможный NPE, незакрытые ресурсы), уязвимости, «запахи» кода (дублирование, сложность) и показывает покрытие тестами. Он не заменяет тесты и ревью, а снимает с них рутину. Его замечания — повод посмотреть на код, решение всегда за разработчиком.

Есть три способа им пользоваться:

| Где | Что это | Когда |
| --- | --- | --- |
| Локальный сервер в Docker | SonarQube Community из `docker-compose.sonar.yml` | попробовать, проверить настройки перед CI |
| SonarQube Cloud | облачный сервис, ставить ничего не нужно | постоянный анализ в CI для команды |
| SonarQube for IDE (SonarLint) | плагин IntelliJ IDEA | подсветка проблем прямо в редакторе |

### Что уже настроено в проекте

Все настройки анализа — свойства `sonar.*` в `pom.xml`:

| Настройка | Зачем |
| --- | --- |
| `jacoco-maven-plugin` | отчёт о покрытии `target/site/jacoco/jacoco.xml`, формируется при `./mvnw test` |
| `sonar.coverage.jacoco.xmlReportPaths` | Sonar берёт покрытие из этого отчёта |
| `lombok.config` → `lombok.addLombokGeneratedAnnotation` | код, сгенерированный Lombok, не учитывается в покрытии |
| исключения JaCoCo и `sonar.coverage.exclusions` | сгенерированные классы (OpenAPI, QueryDSL), конфигурация и `main` не учитываются в покрытии |
| `sonar.issue.ignore.multicriteria` | отключение отдельных правил для конкретных папок (см. ниже) |

Код из `target/generated-sources` Sonar не анализирует: в анализ попадает только `src/main/java`.

### Локальный сервер в Docker

В проекте есть отдельный `docker-compose.sonar.yml`: SonarQube Community и своя PostgreSQL для него. С базой приложения он не связан.

1. Запустить сервер (первый старт около минуты):

   ```bash
   docker compose -f docker-compose.sonar.yml up -d
   ```

2. Открыть <http://localhost:9000>, войти как `admin` / `admin` и сменить пароль.
3. Создать токен: *My Account → Security → Generate Tokens*, тип *Global Analysis Token*.
4. Запустить тесты и анализ (для тестов нужен запущенный Docker):

   ```bash
   SONAR_TOKEN=<токен> ./mvnw clean verify sonar:sonar -Dsonar.host.url=http://localhost:9000
   ```

   Результат: <http://localhost:9000/dashboard?id=com.mixamus%3Arest-api>.

Остановить сервер: `docker compose -f docker-compose.sonar.yml down`. Удалить вместе со всеми анализами и настройками: добавить `-v`.

Токен передаётся через переменную окружения `SONAR_TOKEN`: так он не попадает в историю команд и логи CI. Класть токен в `pom.xml` или другие файлы репозитория нельзя.

### Как читать результаты

- **Quality Gate** (Passed / Failed) — итог проверки по порогам: нет новых багов и уязвимостей, покрытие нового кода не ниже заданного и т.д. Именно его обычно проверяют в CI и в пулл-реквестах.
- **New Code / Overall Code** — замечания по новому коду и по всему проекту. Правило «Clean as You Code»: держать чистым новый код и не обязательно сразу чинить старый.
- **Issues** — сами замечания с типом и важностью:
  - *Bug*, *Vulnerability* — почти всегда стоит исправлять;
  - *Code Smell* — про читаемость и поддержку, часть спорна, решать по ситуации;
  - *Security Hotspot* — место для ручной проверки: посмотреть и отметить *Safe* или исправить.
- **Coverage** — покрытие тестами по строкам и веткам, **Duplications** — повторяющийся код.

Что делать с замечанием, от лучшего к худшему:

1. Исправить код.
2. Если правило не подходит команде в целом — отключить его в Quality Profile на сервере.
3. Если правило не подходит в определённой папке — исключить его для этого пути (см. ниже).
4. Единичный случай — закрыть в интерфейсе Sonar как *Accepted* или *False Positive* с комментарием.
5. Редкое исключение, которое должно быть видно в коде, — `@SuppressWarnings("java:Sxxxx") // причина`. Не используйте `// NOSONAR`: он глушит все правила на строке.

### Исключение правила для папки

В `<properties>` в `pom.xml` (ключи критериев `e1`, `e2` — произвольные имена):

```xml
<sonar.issue.ignore.multicriteria>e1,e2</sonar.issue.ignore.multicriteria>

<sonar.issue.ignore.multicriteria.e1.ruleKey>java:S5411</sonar.issue.ignore.multicriteria.e1.ruleKey>
<sonar.issue.ignore.multicriteria.e1.resourceKey>src/main/java/com/mixamus/restapi/repository/**</sonar.issue.ignore.multicriteria.e1.resourceKey>

<sonar.issue.ignore.multicriteria.e2.ruleKey>java:S5411</sonar.issue.ignore.multicriteria.e2.ruleKey>
<sonar.issue.ignore.multicriteria.e2.resourceKey>src/main/java/com/mixamus/restapi/service/**</sonar.issue.ignore.multicriteria.e2.resourceKey>
```

- `resourceKey` — путь от корня проекта (модуля): `**` — любые вложенные папки, `*` — любые символы внутри одной папки. В одном критерии один шаблон; для второй папки нужен второй критерий.
- Исключения из `pom.xml` работают **только при анализе на сервере** через Maven. SonarLint в IDE их не видит и продолжает подсвечивать такие места.
- Значение из `pom.xml` **заменяет** список исключений, заданный в настройках проекта на сервере, а не дополняет его. Если на сервере уже есть исключения, перенесите их в `pom.xml`.
- Исключение действует и на будущий код в этой папке, поэтому пути стоит делать как можно уже.

### SonarQube for IDE (SonarLint) в IntelliJ IDEA

1. Установить плагин: *Settings → Plugins → Marketplace* → **SonarQube for IDE**.
2. Без настройки плагин работает **автономно**: подсвечивает проблемы по встроенному набору правил.
3. Чтобы правила и решения по замечаниям совпадали с сервером, включите **Connected Mode**:
   1. *Settings → Tools → SonarQube for IDE → Connections* → `+`;
   2. выбрать *SonarQube Server* (свой сервер, например `http://localhost:9000`) или *SonarQube Cloud*;
   3. указать **пользовательский** токен (*My Account → Security*, тип *User Token*), для облака — ещё организацию;
   4. *Settings → Tools → SonarQube for IDE → Project Settings* → **Bind project** → выбрать подключение и проект на сервере.

В Connected Mode плагин берёт правила и исключения из **настроек проекта на сервере**. Исключения из `pom.xml` и локальное отключение правил в этом режиме не действуют. Чтобы подтянуть изменения с сервера сразу: *Connections* → **Update**.

### SonarQube Cloud (облако)

[SonarQube Cloud](https://sonarcloud.io) (раньше назывался SonarCloud) — тот же анализ как сервис: свой сервер поднимать не нужно, анализ запускается из CI. Для публичных репозиториев он бесплатен, для приватных есть ограничения бесплатного тарифа и платные тарифы, актуальные условия смотрите на [странице цен](https://www.sonarsource.com/plans-and-pricing/sonarcloud/).

**Регистрация и проект**

1. Открыть <https://sonarcloud.io> → **Log in**. Отдельной регистрации по почте нет: вход через аккаунт **GitHub**, **GitLab**, **Bitbucket** или **Azure DevOps**, где лежит репозиторий.
2. Создать **организацию**: *+ → Create new organization* → импортировать организацию или личный аккаунт из GitHub (GitLab и т.д.) и выбрать тариф. Запомнить **ключ организации** (*Organization Key*).
3. Добавить проект: *+ → Analyze new project* → выбрать репозиторий. Запомнить **ключ проекта** (*Project Key*, обычно `<владелец>_<репозиторий>`).
4. Для Maven-проекта с покрытием нужен анализ из CI, а не автоматический: *Administration → Analysis Method* → выключить **Automatic Analysis**. Автоматический анализ не умеет покрытие тестами для Java и конфликтует с анализом из CI.
5. Создать токен: *My Account → Security → Generate Tokens*.

**Запуск анализа с локальной машины**

```bash
SONAR_TOKEN=<токен> ./mvnw clean verify sonar:sonar -Dsonar.host.url=https://sonarcloud.io -Dsonar.organization=<ключ_организации> -Dsonar.projectKey=<ключ_проекта>
```

Для организации в регионе US адрес другой: `https://sonarqube.us`.

Организацию и ключ проекта можно один раз записать в `<properties>` в `pom.xml`, чтобы не передавать их в команде. Токен туда не записывается никогда:

```xml
<sonar.organization>ключ_организации</sonar.organization>
<sonar.projectKey>ключ_проекта</sonar.projectKey>
<sonar.host.url>https://sonarcloud.io</sonar.host.url>
```

**Анализ в GitHub Actions**

1. В репозитории GitHub: *Settings → Secrets and variables → Actions → New repository secret* → `SONAR_TOKEN` со значением токена.
2. Добавить файл `.github/workflows/sonar.yml`:

   ```yaml
   name: SonarQube Cloud
   on:
     push:
       branches: [main]
     pull_request:
   jobs:
     analyze:
       runs-on: ubuntu-latest
       steps:
         - uses: actions/checkout@v4
           with:
             fetch-depth: 0  # полная история нужна, чтобы Sonar отличал новый код от старого
         - uses: actions/setup-java@v4
           with:
             distribution: temurin
             java-version: '21'
             cache: maven
         # Docker на ubuntu-latest уже есть, поэтому тесты с Testcontainers работают
         - run: ./mvnw -B clean verify sonar:sonar -Dsonar.host.url=https://sonarcloud.io -Dsonar.organization=<ключ_организации> -Dsonar.projectKey=<ключ_проекта>
           env:
             SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
   ```

После этого результаты анализа появляются в SonarQube Cloud при каждом пуше в `main`, а в пулл-реквестах — статус Quality Gate и комментарии с замечаниями.
