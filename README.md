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

Change `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` in the root `.env` file.
The application automatically loads `.env` from its working directory. Use the
project root as the working directory in IntelliJ. Environment variables override
values in `.env`; remove stale overrides from the run configuration.
Use Java properties syntax: `KEY=value`, without `export` or surrounding quotes.
The file is excluded from Git; preserve existing tokens and passwords when editing it.

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
- Add a course to several education levels by checking the levels in the course form.
- Add a group to several courses (including different education levels) in one action.
  Each combination keeps its own group ID and independent schedule. Existing combinations
  are skipped, preserving their settings and schedule; edit them individually in the table.
- `/admin/schedule` — schedule table. Select level -> course -> group, click a weekday/time cell, enter subject, optional custom time, room and teacher.
- If a custom time is entered, after save it becomes its own row in the grid.

## 4. Telegram

Set:

```text
TELEGRAM_BOT_TOKEN=...
MINIAPP_URL=https://your-public-domain/miniapp/schedule
```

The Mini App URL must be HTTPS. For local development use ngrok or another HTTPS tunnel.

Start ngrok with `ngrok http 8080` (use `SERVER_PORT` if changed), not port 80.
`ERR_NGROK_8012` means the tunnel cannot reach its configured local upstream.
The free ngrok browser warning is served before the application: press Visit Site
inside the messenger for testing, or use hosting/a tunnel without an interstitial.
An application response header or URL query parameter cannot bypass the first
HTML navigation warning. See https://ngrok.com/docs/pricing-limits/free-plan-limits.
Set `MINIAPP_REQUEST_LOGGING=true` in `.env` to log Mini App and public API requests
in the application console. Requests blocked at ngrok will not appear there.

Replace the URL with your actual public address, including `/miniapp/schedule`.
`YOUR_HTTPS_DOMAIN` and `example.com` are placeholders, not working Mini App addresses.
If the address is absent or invalid, the bot keeps the selection/reset menu and
explains that the schedule is not published yet, instead of sending an invalid button.
Restart the application after changing `.env` or environment variables.

Bot flow: level -> course -> group -> `Показать расписание` / `Сброс настроек`. The choice is stored in PostgreSQL.

## 5. MAX

Set:

```text
MAX_BOT_TOKEN=...
```

In MAX Partner Platform bind the HTTPS Mini App URL to the bot. The project uses `https://platform-api2.max.ru` and long polling for local development. For production, replace polling with a webhook subscription.

Enter the current `MINIAPP_URL` from `.env` in the MAX Partner Platform.
The MAX `open_app` button resolves the current bot ID and username using `/me`,
caches them and sends both `contact_id` and the required `web_app` username.
`MAX_BOT_NAME` is no longer needed.
The button passes
the selected group as `payload=g<groupId>`. The hosting URL must be updated in
the partner platform whenever the tunnel address changes.

MAX requires the Russian Trusted Root CA certificate. The project bundles the
official root certificate and applies it only to the MAX HTTP client via the
`max` SSL bundle. TLS and hostname verification remain enabled; no changes to
the JDK or Windows certificate store are needed. Certificate source and fingerprint
are documented in `src/main/resources/certs/README.md`.

Other `.env` settings: `SERVER_PORT`, `TELEGRAM_POLL_DELAY_MS`,
`MAX_POLL_DELAY_MS`, and `MAX_CA_CERTIFICATE` (a Spring resource location).
The bot identity is resolved automatically; remove obsolete `MAX_BOT_NAME`.

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
