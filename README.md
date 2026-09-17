# UGK Schedule

Spring Boot 3 / Java 17 / PostgreSQL project for schedule administration, student Mini App and messenger bots.

## 1. Local PostgreSQL (without Docker)

Install PostgreSQL locally, then create database:

```sql
CREATE DATABASE ugk_schedule;
```

By default the app connects to:
- URL: `jdbc:postgresql://localhost:5432/ugk_schedule`
- user: `postgres`
- password: `postgres`

Change them with environment variables `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` or edit `application.properties` for local development.

## 2. IntelliJ IDEA

1. File -> Open -> select this `ugk-schedule` folder.
2. Select JDK 17.
3. Let Maven import dependencies.
4. Run `UgkScheduleApplication`.
5. Open `http://localhost:8080/admin`.

Default first-run admin: `admin / admin123`. Change `APP_ADMIN_PASSWORD` before deployment.

Flyway creates all tables and inserts example education levels and courses automatically.

## 3. Admin panel

- `/admin` — education levels, allowed courses and groups/directions.
- `/admin/schedule` — schedule table. Select level -> course -> group, click a weekday/time cell, enter subject, optional custom time, room and teacher.
- If a custom time is entered, after save it becomes its own row in the grid.

## 4. Telegram

Set:

```text
TELEGRAM_BOT_TOKEN=...
MINIAPP_URL=https://public-domain.example/miniapp/schedule
```

The Mini App URL must be HTTPS. For local development use ngrok or another HTTPS tunnel.

Bot flow: level -> course -> group -> `Показать расписание` / `Сброс настроек`. The choice is stored in PostgreSQL.

## 5. MAX

Set:

```text
MAX_BOT_TOKEN=...
MAX_BOT_NAME=YourBotName
```

In MAX Partner Platform bind the HTTPS Mini App URL to the bot. The project uses `https://platform-api2.max.ru` and long polling for local development. For production, replace polling with a webhook subscription.

## 6. Architecture

- `domain` — JPA model.
- `repository` — PostgreSQL access.
- `service` — all business rules.
- `controller` — Thymeleaf admin pages and Mini App.
- `controller/api` — API reusable by future website/mobile app.
- `bot/telegram`, `bot/max` — thin messenger adapters. They do not contain schedule business logic.

No separate Subject/Teacher/Room catalogs are used in this version: these are plain schedule-entry text fields.

## Production notes

For production add HTTPS/reverse proxy, strong admin password, webhook integrations, Mini App init-data validation for Telegram/MAX, audit logging and backups. Long polling is intentionally used here because it is easiest to run locally from IntelliJ IDEA.
