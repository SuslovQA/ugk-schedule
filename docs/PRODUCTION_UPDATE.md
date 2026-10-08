# Обновление тестового сервера до production-конфигурации

Изменения подготовлены локально 8 октября 2026 года. Сервер не изменялся. Расписание остаётся публичным.

## Что изменено

- CSRF действует на административный API; JavaScript отправляет masked token из HTML meta. Исключения только для двух launch endpoints.
- Неизвестные endpoints закрыты по умолчанию. Добавлена CSP, embedding Mini App сохранён для Telegram/MAX.
- Профиль prod включает loopback, Secure/HttpOnly/SameSite cookie, cache шаблонов, два scheduler-потока, сетевые таймауты, ограничение Tomcat и DB pool.
- Production-конфигурация проверяется до datasource/Flyway. Секреты короче 16 символов и шаблонные значения отклоняются; PostgreSQL superuser postgres запрещён. Bootstrap не меняет существующие password hashes и останавливает production startup при известных слабых активных паролях.
- Логи: 30 дней, архивы до 1 ГБ, активный файл application.log. Максимальный размер одного файла 10 МБ. Архивный cap применяется при ротации; это не жёсткая файловая квота.
- Карты сообщений ограничены 10000 пользователями, 32 ID на пользователя и TTL один час.
- Боты сохраняют пачку событий и cursor в одной PostgreSQL-транзакции. Cursor зависит от fingerprint token; plaintext token не сохраняется в этих таблицах.
- Повторная обработка: до пяти попыток, минимум 30 секунд между ошибками, учитывается Retry-After при HTTP 429. Для одного пользователя сохраняется порядок, пока более раннее событие ещё повторяется. События разных пользователей не блокируют друг друга.
- После пяти ошибок событие остаётся в БД для разбора оператором; следующие события пользователя могут продолжить обработку. Неудачи пишутся без тела update/API error.
- Payload успешного события удаляется; deduplication key хранится семь дней. Общий незавершённый backlog ограничен 10000 событий на token: при превышении новые события не подтверждаются.
- Публичное расписание и название неактивной группы/родительского курса/уровня дают 404. Неактивные родители не публикуют дочерние списки.
- Добавлены проверки имён каталога, контролируемые ошибки API и audit записи изменения расписания (actor/action/id, без содержимого занятий).
- Nginx-шаблон ограничивает login/API/соединения/body/timeouts. GitHub CI запускает Java, JavaScript и PostgreSQL integration tests; build отделён от deploy secrets; Actions закреплены официальными SHA. Добавлен Dependabot.
- Добавлены скрипт PostgreSQL backup, systemd service/timer и ограничения journald.

## Перед заменой JAR

1. Сделайте backup БД и копии текущих .env/unit/Nginx/JAR. Не перезаписывайте серверную .env шаблоном.
2. Проверьте серверный DB_USERNAME: нужен отдельный пользователь с правами Flyway в собственной БД, без SUPERUSER. Если текущая роль postgres, заранее создайте новую и проверьте её права на существующие таблицы/sequence и flyway_schema_history. Назначьте владельца объектов новой роли контролируемо; не выполняйте слепое REASSIGN OWNED всей роли postgres.
3. Установите уникальные DB_PASSWORD и APP_ADMIN_PASSWORD минимум 16 символов. APP_ADMIN_PASSWORD для BCrypt не должен превышать 72 байта UTF-8. Шаблонные REPLACE_WITH/YOUR_UNIQUE и пароли со словом password отклоняются.
4. Если в БД уже есть admin/admin123, изменение .env не меняет его hash. Безопасный переход: до включения prod установите новый уникальный APP_ADMIN_USERNAME/сильный APP_ADMIN_PASSWORD и перезапустите старую версию. Проверьте вход нового администратора. Затем отключите старого пользователя в psql: UPDATE admin_users SET enabled=false WHERE username='admin'; выполняйте это только после проверки нового входа и имени отключаемой записи. Не удаляйте таблицу admin_users.
5. Проверьте MINIAPP_URL: реальный HTTPS-домен; сохраните bot tokens. Остановите другие экземпляры с теми же tokens.
6. Добавьте APP_LOG_HISTORY_DAYS=30 и APP_LOG_TOTAL_SIZE_CAP=1GB. На тестовом сервере не задавайте APP_ENFORCE_SECRET_POLICY=true: так сохранятся текущие реквизиты БД и существующая учётная запись администратора, даже если профиль prod уже включён в установленном unit. Включайте строгую проверку только после отдельной ротации секретов. Реальные значения Spring properties в нижней части .env могут перекрывать профиль: удалите только конфликтующие overrides после проверки. Порт 8080 в unit фиксирован.
7. Production cookie требует HTTPS. Прямой HTTP login на 8080 не будет поддерживать сессию с Secure cookie. Проверяйте login через Nginx HTTPS.

## Установить обновлённые серверные файлы

Передайте deploy/ugk-schedule.service и deploy/nginx.conf на сервер. Unit не включает профиль prod автоматически, поэтому тестовый сервер продолжит использовать текущие реквизиты. Установите unit в /etc/systemd/system/ugk-schedule.service, затем systemctl daemon-reload.

Не копируйте HTTP-шаблон Nginx поверх действующего HTTPS-конфига целиком: перенесите limit_req_zone/limit_conn_zone/map в http context, лимиты и proxy headers в соответствующие locations, сохранив сертификаты/redirect. Замените server_name реальным доменом. Проверьте nginx -t перед reload. При CDN/туннеле real IP должен доверять только проверенным upstream.

Nginx-шаблон оставляет админку защищённой логином и CSRF; ограничение LAN/VPN добавьте по фактическим адресам в /admin и /api/admin, включая отдельный exact /admin/login. IP rate limits учитывают общий NAT пользователей; настройте после нагрузки.

Установите backup:

```bash
sudo install -o root -g root -m 0700 deploy/backup.sh /usr/local/sbin/ugk-schedule-backup
sudo install -o root -g root -m 0644 deploy/ugk-schedule-backup.service /etc/systemd/system/
sudo install -o root -g root -m 0644 deploy/ugk-schedule-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl start ugk-schedule-backup.service
sudo systemctl enable --now ugk-schedule-backup.timer
sudo systemctl list-timers ugk-schedule-backup.timer
```

Команды предполагают текущий каталог с переданной папкой deploy. Скрипт рассчитан на локальную БД ugk_schedule, пользователя ОС postgres и peer authentication. Для другого имени БД измените backup.sh. Хранение: /var/backups/ugk-schedule, 14 дней; успешность pg_restore --list не заменяет полноценное восстановление. Настройте внешнюю копию и мониторинг ошибки timer/service. Journald-конфиг установите в /etc/systemd/journald.conf.d/ после проверки общей политики логов.

## Миграция и публикация

Соберите Java 17: mvn --batch-mode --no-transfer-progress clean verify, затем node --test src/test/js/*.test.cjs. Для PostgreSQL tests задайте UGK_TEST_DB_URL/USERNAME/PASSWORD отдельной тестовой БД; тест создаёт и удаляет только свою схему ugk_test_*.

Загрузите новый JAR как /opt/ugk-schedule/app.jar.next, выполните существующий deploy/deploy.sh. Flyway применит только новую V3: bot_polling_state, bot_updates и индексы. Старые таблицы расписания, каталога, пользователей и предпочтений сохраняются. Не редактируйте V1/V2 и не чистите flyway_schema_history.

GitHub deploy по-прежнему работает при push в master, но теперь использует Environment production. Настройте его secrets/доступные правила защиты заранее. Deploy job не устанавливает unit, Nginx, backup и секреты: эти файлы нужно обновить вручную. Автоматического отката JAR/БД нет.

После запуска проверьте:

```bash
sudo systemctl status ugk-schedule.service --no-pager
sudo journalctl -u ugk-schedule.service -n 100 --no-pager
curl --fail http://127.0.0.1:8080/api/public/levels
curl --fail https://YOUR_DOMAIN/api/public/levels
```

Проверьте вход/создание/изменение/удаление занятия по HTTPS, cookie flags, Mini App в обоих мессенджерах, запуск без groupId и отказ доступа к inactive-группе. Для новой CSP нужен реальный тест в клиентах: локальные WebMvc tests не запускают MAX Bridge.

## Работа с неуспешными событиями

Проверка в psql (не публикует payload):

```sql
SELECT id, stream, attempts, created_at, available_at, last_failure
FROM bot_updates WHERE completed_at IS NULL ORDER BY id;
```

При attempts>=5 автоматические попытки прекращаются. После устранения причины можно повторить конкретное событие:

```sql
UPDATE bot_updates SET attempts=0, available_at=current_timestamp
WHERE id=EVENT_ID AND completed_at IS NULL;
```

Сначала проверьте смысл старого события: повторный RESET или выбор может изменить текущие предпочтения. Повторы внешних сообщений возможны: приложение может отправить ответ и упасть до отметки complete. Exactly-once для внешнего API не гарантируется. Не запускайте две реплики одного poller; lock/lease для реплик не реализован.

MAX deduplication использует хеш полного JSON update; одинаковые byte-equivalent JSON подавляются семь дней. При ротации token создаётся новый stream; события старого stream требуют отдельного решения оператора. Dead-letter payload хранится до разбора; ограничьте доступ к БД и срок ручного хранения согласно политике.

Удаление setup-сообщений MAX пока выполняется последовательно с паузой платформы. Scheduler изолирует MAX от Telegram, но это не делает MAX массовым параллельным обработчиком. Начальный предел — один экземпляр; нагрузка 500–1000 студентов требует измерения bursts и задержки бота.

## Откат

V3 только расширяет схему. Для отката остановите новую версию, восстановите прежний JAR и прежний unit/профиль при необходимости. Не удаляйте V3-таблицы: это потеряет pending-события. Старые pollers не используют durable cursor и могут повторно получать события; не запускайте старую и новую версии одновременно. Для возврата к новой версии сохранённые очереди доступны.

## Что остаётся проверить перед публичным запуском

Нагрузочный тест целевой VM, восстановление backup, реальные мессенджеры/TLS/CSP, внешняя защита админки, внешние уведомления, CVE/SCA и полный secret scan Git history. Dependabot config не означает, что scans автоматически доступны/включены для private repo. Проект не содержит открытого Actuator endpoint. Полный аудит каталожных действий и optimistic locking нескольких редакторов не реализованы; audit сейчас касается изменения расписания.
