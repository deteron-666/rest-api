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
java -jar target/rest-api-1.1.0.jar
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
`409` — конфликт с текущим состоянием (не хватает товара, неподходящий статус заказа) или параллельное изменение
тех же данных, в том числе если не удалось дождаться блокировки строки за 5 секунд (`lock_timeout`); такой запрос можно повторить.
Непредвиденные ошибки — `500` в том же формате, без внутренних подробностей.
Денежные значения передаются как десятичные числа с не более чем двумя знаками после запятой.

У товара есть поле `version`: оно меняется при каждом изменении товара, в том числе когда заказ списывает или возвращает остаток.
Чтобы `PUT /api/products/{productId}` не затёр остаток, изменённый после чтения, передайте `version` из последнего ответа:
если товар успели изменить, запрос отклоняется с `409`. Без `version` обновление перезаписывает текущие значения.

## База данных

Liquibase применяет миграции схемы при запуске. Структура миграций — по релизам, как
рекомендует [документация Liquibase](https://docs.liquibase.com/secure/implementation-guide-5-1/set-up-your-changelog-structure):

```
src/main/resources/db/changelog/
├── changelog-root.yaml              ← root changelog: только include релизов + проверка, что БД — PostgreSQL
└── changelog-1.1.0/
    ├── changelog-1.1.0.yaml         ← релиз 1.1.0: include миграций по порядку + метка отката tag 1.1.0
    ├── 001-create-products.yaml
    ├── ...
    └── 007-product-version.yaml
```

Новая миграция — новый файл `NNN-описание.yaml` в папке текущего, ещё не выпущенного релиза и `include` в его `changelog-<версия>.yaml`. Имя папки
релиза и метка отката совпадают с версией проекта в `pom.xml` (`<version>`). Для следующего релиза — новая папка, например `changelog-1.2.0/` с
`changelog-1.2.0.yaml`, и её `include` в `changelog-root.yaml`. Применённые changeset'ы не меняются, не переименовываются и не переносятся: Liquibase
узнаёт их по id, автору и пути к файлу. Правила подробно описаны в шапке `changelog-root.yaml`.

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
SPRING_PROFILES_ACTIVE=prod DB_URL=jdbc:postgresql://localhost:5432/orders DB_USERNAME=orders DB_PASSWORD=secret java -jar target/rest-api-1.1.0.jar
```
## Проверка оформления кода (Checkstyle)

[Checkstyle](https://checkstyle.org) проверяет, что код оформлен по единым правилам: отступы, длина строк, фигурные скобки, именование, импорты. Правила лежат в `checkstyle/checkstyle.xml`.

```bash
make checkstyle
```

Проверка также выполняется **автоматически в начале каждой сборки** (`compile`, `test`, `verify`, `package`): при нарушении сборка падает со списком файлов, строк и причин. Проверяется только свой код (`src/main/java`, `src/test/java`), сгенерированные классы не проверяются.

Основные правила:

| Что | Правило |
| --- | --- |
| Отступы и пробелы | табы запрещены, пробелы вокруг операторов и `{`, без лишних пробелов в скобках |
| Длина строки | не больше 150 символов (кроме `package`, `import` и ссылок) |
| Импорты | без `*`, без неиспользуемых и повторяющихся |
| Скобки | `{}` обязательны у `if`/`for`/`while`, даже для одной строки; пустой блок только с комментарием; пустой `catch` запрещён |
| Именование | классы `UpperCamelCase`, методы и переменные `lowerCamelCase`, публичные константы `UPPER_SNAKE_CASE` |
| Типичные ошибки | сравнение строк через `==`, `equals` без `hashCode`, проваливание в `switch`, отсутствие `@Override` |
| Размер | метод не длиннее 80 строк, не больше 8 параметров |

Точечное исключение в коде (редко и с пояснением): `@SuppressWarnings("checkstyle:ИмяПроверки") // причина`.

В IntelliJ IDEA те же правила можно подключить плагином **CheckStyle-IDEA**: *Settings → Tools → Checkstyle* → `+` → файл `checkstyle/checkstyle.xml`. Тогда нарушения подсвечиваются прямо в редакторе.

## Анализ кода в SonarQube

[SonarQube](https://www.sonarsource.com/products/sonarqube/) — статический анализатор: ищет баги (возможный NPE, незакрытые ресурсы), уязвимости, «запахи» кода (дублирование, сложность) и показывает покрытие тестами. Он не заменяет тесты и ревью, а снимает с них рутину. Его замечания — повод посмотреть на код, решение всегда за разработчиком.

### Быстрый старт

Всё, что относится к Sonar, лежит в папке `sonar/`, а команды запускаются через `make` из корня проекта:

```bash
cp sonar/.env.example sonar/.env   # один раз, затем вписать токены в sonar/.env
make sonar                         # тесты + анализ в SonarQube Cloud
```

| Команда | Что делает |
| --- | --- |
| `make` | список всех команд |
| `make sonar` | тесты + анализ в SonarQube Cloud (токен `SONAR_TOKEN`) |
| `make sonar-local` | тесты + анализ на локальном сервере (токен `SONAR_LOCAL_TOKEN`) |
| `make sonar-up` / `make sonar-down` | запустить / остановить локальный SonarQube в Docker |
| `make sonar-logs` | логи локального SonarQube |

| Файл                        | Назначение                                                                                                    |
|-----------------------------|---------------------------------------------------------------------------------------------------------------|
| `sonar/.env`                | **ваши токены**; в `.gitignore`, в репозиторий не попадает                                                    |
| `sonar/.env.example`        | шаблон `sonar/.env` без значений; тоже в `.gitignore`                                                         |
| `sonar/sonar.sh`            | загружает токены из `sonar/.env` и запускает тесты с анализом; его вызывают `make sonar` и `make sonar-local` |
| `docker/docker-compose.yml` | локальный SonarQube и PostgreSQL для него                                                                     |
| `Makefile` (в корне)        | короткие команды                                                                                              |

Переменные окружения, заданные до запуска (например, `SONAR_TOKEN` в CI), важнее значений из `sonar/.env`. Если Java не найдена в `PATH`, используется JDK по умолчанию из SDKMAN.

Есть три способа пользоваться Sonar:

| Где                           | Что это                                                                           | Когда                                     |
|-------------------------------|-----------------------------------------------------------------------------------|-------------------------------------------|
| Локальный сервер в Docker     | SonarQube Community из `docker/docker-compose.yml`                                | попробовать, проверить настройки перед CI |
| SonarQube Cloud               | облачный сервис, ставить ничего не нужно; **используется в проекте по умолчанию** | постоянный анализ, в том числе в CI       |
| SonarQube for IDE (SonarLint) | плагин IntelliJ IDEA                                                              | подсветка проблем прямо в редакторе       |

### Что уже настроено в проекте

Все настройки анализа — свойства `sonar.*` в `pom.xml`:

| Настройка | Зачем |
| --- | --- |
| `jacoco-maven-plugin` | отчёт о покрытии `target/site/jacoco/jacoco.xml`, формируется при `./mvnw test` |
| `sonar.host.url`, `sonar.organization`, `sonar.projectKey` | анализ отправляется в SonarQube Cloud: организация `mixamus13`, проект `deteron-666_rest-api` |
| токен | **не хранится в репозитории**, берётся из `sonar/.env` или переменной окружения `SONAR_TOKEN` |
| `sonar.coverage.jacoco.xmlReportPaths` | Sonar берёт покрытие из этого отчёта |
| `lombok.config` → `lombok.addLombokGeneratedAnnotation` | код, сгенерированный Lombok, не учитывается в покрытии |
| исключения JaCoCo и `sonar.coverage.exclusions` | сгенерированные классы (OpenAPI, QueryDSL), конфигурация и `main` не учитываются в покрытии |
| `sonar.issue.ignore.multicriteria` | отключение отдельных правил для конкретных папок (см. ниже) |

Код из `target/generated-sources` Sonar не анализирует: в анализ попадает только `src/main/java`.

### Локальный сервер в Docker

`docker/docker-compose.yml` поднимает SonarQube Community и свою PostgreSQL для него. С базой приложения он не связан.

1. Запустить сервер (первый старт около минуты):

   ```bash
   make sonar-up
   ```

2. Открыть <http://localhost:9000>, войти как `admin` / `admin` и сменить пароль.
3. Создать токен: *My Account → Security → Generate Tokens*, тип *Global Analysis Token*, и вписать его в `sonar/.env` в строку `SONAR_LOCAL_TOKEN=`.
4. Запустить тесты и анализ (для тестов нужен запущенный Docker):

   ```bash
   make sonar-local
   ```

   Скрипт подставляет адрес `http://localhost:9000` вместо SonarQube Cloud из `pom.xml`.
   Результат: <http://localhost:9000/dashboard?id=deteron-666_rest-api>.

Остановить сервер: `make sonar-down`, анализы и настройки при этом сохраняются. Удалить всё: `docker compose -f sonar/docker-compose.yml down -v`.

Класть токены в `pom.xml` или другие отслеживаемые файлы нельзя: только в `sonar/.env` или в переменные окружения.

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

**Запуск анализа этого проекта**

Адрес, организация и ключ проекта уже прописаны в `pom.xml`. Токен вписывается в `sonar/.env` в строку `SONAR_TOKEN=`, после чего:

```bash
make sonar
```

Результат: <https://sonarcloud.io/project/overview?id=deteron-666_rest-api>.

Без `make` то же самое: `sonar/sonar.sh`. Или вручную, если токен уже есть в окружении: `./mvnw clean verify sonar:sonar`.

Запуск из IntelliJ IDEA: окно *Make* (плагин *Makefile Language*) → цель `sonar`, или конфигурация Maven `clean verify sonar:sonar` с `SONAR_TOKEN` в *Settings → Build, Execution, Deployment → Build Tools → Maven → Runner → Environment variables*.

Токен никогда не записывается в `pom.xml` и другие отслеживаемые файлы: репозиторий публичный. `sonar/.env` и шаблон `sonar/.env.example` в `.gitignore` и в репозиторий не попадают. После клонирования создайте `sonar/.env` с переменными `SONAR_TOKEN=` и `SONAR_LOCAL_TOKEN=`.

Без токена анализ завершится ошибкой `Not authorized. Please check the 'sonar.token' property or the 'SONAR_TOKEN' environment variable`.

Для организации в регионе US адрес другой: `https://sonarqube.us`.

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
         - run: ./mvnw -B clean verify sonar:sonar
           env:
             SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
   ```

После этого результаты анализа появляются в SonarQube Cloud при каждом пуше в `main`, а в пулл-реквестах — статус Quality Gate и комментарии с замечаниями.
