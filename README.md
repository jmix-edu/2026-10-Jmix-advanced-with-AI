# Timesheets

This is a web application based on the [Jmix](https://www.jmix.io) framework.

## Getting Started

The newly created project requires Java 21 and uses the embedded HSQL database.

Use the following resources to learn more about Jmix:
* [Jmix Documentation](https://docs.jmix.io)
* [Online Demo Applications](https://www.jmix.io/live-demo)
* [Jmix AI Assistant](https://ai-assistant.jmix.io) (also available in the **Jmix AI** tool window of IntelliJ IDEA)

## Development

- [Setup](https://docs.jmix.io/jmix/setup.html) your development environment.
- [Open](https://docs.jmix.io/jmix/studio/project.html#opening-existing-project) the project in the IDE.
- If you want to use AI agents to develop the application, check out the [Jmix AI Agent Guidelines](https://github.com/jmix-framework/jmix-agent-guidelines) repository.

## Running

To start the application, use the **Timesheets Jmix Application** run configuration in your IDE, or run the following command in the project root directory:

```bash
./gradlew bootRun
```

The application will be available at <http://localhost:8080>.

Without an active profile the application runs with the `dev` profile (`application-dev.properties`).
It needs no settings: the local demo credentials are defaults in that file.
Environment variables override them:

* `DB_USERNAME`, `DB_PASSWORD` — the main database `ts_live` (`jmix`/`jmix` by default);
* `REPORTS_DB_USERNAME`, `REPORTS_DB_PASSWORD` — the reports database `ts_reports` (`root`/`root` by default);
* `LDAP_MANAGER_PASSWORD` — the demo LDAP server (`admin` by default);
* `UI_LOGIN_DEFAULT_PASSWORD` — the password filled in on the login form (`admin` by default).

The default user credentials in development are:
* Login: `admin`
* Password: `admin`

They work only in development: the `prod` profile does not start until admin gets another password, see below.

## Запуск в проде

### Сборка

```bash
./gradlew -Pvaadin.productionMode=true bootJar
```

Фронтенд собирается в production-режиме, `vaadin-dev` в jar не попадает.
Результат — `build/libs/timesheets-0.0.1-SNAPSHOT.jar`.

### Секреты в Vault

Пароли лежат в Vault, KV v2, путь `secret/timesheets`. Ключи совпадают с именами свойств:

| Ключ | Что это |
|---|---|
| `main.datasource.password` | пароль основной базы |
| `reports.datasource.password` | пароль базы отчётов |
| `timesheets.admin.initial-password` | пароль admin при первом запуске, не короче 12 символов |
| `jmix.ldap.manager-password` | пароль bind-учётки LDAP |
| `spring.mail.password` | пароль SMTP, если сервер требует авторизации |

Пока у admin пароль `admin`, приложение с профилем `prod` не стартует. При первом запуске
оно ставит admin пароль из `timesheets.admin.initial-password`. Дальше ключ не нужен, его можно удалить.
Если Vault недоступен или токен неверный, приложение тоже не стартует.

Для проверки локально — dev-сервер Vault в Docker (данные в памяти, после перезапуска контейнера
секреты нужно положить заново):

```bash
docker run -d --name ts-vault -p 8200:8200 -e VAULT_DEV_ROOT_TOKEN_ID=dev-root hashicorp/vault:2.1.2
docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=dev-root ts-vault \
  vault kv put secret/timesheets \
    main.datasource.password=... \
    reports.datasource.password=... \
    timesheets.admin.initial-password=... \
    jmix.ldap.manager-password=...
```

### Переменные окружения

| Переменная | Значение |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `VAULT_URI`, `VAULT_TOKEN` | адрес Vault и токен |
| `MAIN_DB_URL`, `MAIN_DB_USERNAME` | основная база, например `jdbc:postgresql://db/ts_live` |
| `REPORTS_DB_URL`, `REPORTS_DB_USERNAME` | база отчётов |
| `TIMESHEETS_PUBLIC_URL` | адрес приложения для ссылок в письмах |
| `MAIL_HOST`, `MAIL_PORT` (587 по умолчанию), `MAIL_USERNAME` (необязательно), `MAIL_FROM` | SMTP |
| `LDAP_URLS`, `LDAP_BASE_DN`, `LDAP_MANAGER_DN` | LDAP |

### Запуск

```bash
export SPRING_PROFILES_ACTIVE=prod
export VAULT_URI=http://localhost:8200 VAULT_TOKEN=dev-root
export MAIN_DB_URL=jdbc:postgresql://localhost/ts_prod MAIN_DB_USERNAME=...
export REPORTS_DB_URL=jdbc:postgresql://localhost/ts_prod_reports REPORTS_DB_USERNAME=...
export TIMESHEETS_PUBLIC_URL=https://timesheets.example.com
export MAIL_HOST=... MAIL_FROM=timesheets@example.com
export LDAP_URLS=ldap://... LDAP_BASE_DN=... LDAP_MANAGER_DN=...
java -jar build/libs/timesheets-0.0.1-SNAPSHOT.jar
```

Liquibase применяет миграции обеих баз при старте. Демо-данных в проде нет.

### Проверка

Без входа открыты только два эндпоинта Actuator:

```bash
curl http://localhost:8080/actuator/health   # UP, компоненты db: dataSource и reportsDataSource
curl http://localhost:8080/actuator/info     # версия и время сборки
```

Остальные эндпоинты Actuator закрыты для всех, включая admin.

## Запуск в Docker

Весь стек одной командой: приложение с профилем `prod`, основная база и база отчётов (PostgreSQL),
Vault и демо-LDAP. На машине нужен только Docker с Compose v2, JDK и Node не нужны: jar собирается внутри образа.

### Первый запуск

```bash
docker compose up -d --build
docker compose logs secrets-init   # пароль admin и пароль LDAP-пользователей, показываются один раз
```

Лог `secrets-init` пропадает после `docker compose down`. Пароли остаются в томе `secrets`, их можно прочитать в любой момент:

```bash
docker compose run --rm --no-deps --entrypoint cat secrets-init /secrets/admin_initial_password
docker compose run --rm --no-deps --entrypoint cat secrets-init /secrets/ldap_users_password
```

Приложение: <http://localhost:8080>. Порт меняется переменной `APP_PORT`, например `APP_PORT=8090 docker compose up -d`.
Наружу открыт только порт приложения, базы, Vault и LDAP видны только внутри сети compose.

Первый старт делает следующее:

1. `secrets-init` генерирует пароли в том `secrets`: баз, LDAP, admin.
2. Базы инициализируются с этими паролями (`POSTGRES_PASSWORD_FILE`), LDAP загружает пользователей и группы,
   `ldap-init` ставит пароль пользователям `ivanov`, `petrova`, `sidorov`.
3. `vault-init` инициализирует и распечатывает Vault, кладёт пароли в `secret/timesheets`
   и выдаёт приложению AppRole только на чтение этого пути.
4. `app` стартует после всех, Liquibase создаёт схемы, admin получает пароль из Vault.

### Состояние

```bash
docker compose ps                              # app, main-db, reports-db, ldap, vault — healthy
curl http://localhost:8080/actuator/health     # UP, обе базы
```

### Остановка и данные

* `docker compose down` — контейнеры удаляются, данные остаются в томах: базы, файлы клиентов, Vault, пароли.
* `docker compose up -d` — стек поднимается с теми же данными и паролями.
* `docker compose down -v` — **удаляет всё**, включая пароли и ключи Vault. Следующий `up` начинает с нуля.

### Обновление приложения

```bash
docker compose up -d --build app
```

После правки кода пересобирается только последний слой образа.

### Если что-то пошло не так

* Vault, перезапущенный отдельно (`docker compose restart vault`), стартует запечатанным.
  `docker compose up -d` запускает `vault-init` заново, тот распечатывает Vault.
* Пароль базы применяется только при создании пустого тома. Чтобы сменить его потом:
  `ALTER USER` в базе, затем новое значение в файле тома `secrets` и `docker compose up -d`.
* Почты в стеке нет: письма регистрации и приглашений не уходят, приложение сообщает об ошибке отправки.
