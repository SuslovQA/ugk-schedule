> После локальных исправлений актуальная инструкция обновления: [PRODUCTION_UPDATE.md](PRODUCTION_UPDATE.md). Ниже сохранён исходный аудит/план.

# Приватный GitHub и домашний сервер

Инструкция подготовлена 8 октября 2026 года для текущего проекта UGK Schedule.

## 1. Выбранная схема и условия

Основной вариант: **приватный GitHub → сборка на компьютере → передача JAR по LAN/VPN → Ubuntu 24.04 LTS → systemd → Nginx HTTPS → PostgreSQL на том же сервере**. Первый деплой ручной. SSH не требуется публиковать в интернет. GitHub Actions для автоматического деплоя описан отдельно.

Исправьте блокеры из [PRODUCTION_REVIEW.md](PRODUCTION_REVIEW.md) до открытия сервиса в интернет. Инструкция описывает настройку инфраструктуры и не исправляет Java-код автоматически.

Предположения: отдельная машина или VM с Ubuntu 24.04 LTS, sudo, 2 CPU, 4 ГБ RAM, SSD; собственный домен; доступ к маршрутизатору. Для другой ОС команды адаптируйте. Используйте поддерживаемые обновления ОС/JDK/PostgreSQL. Аппаратные ресурсы — стартовая гипотеза, не гарантия производительности.

Во всех примерах замените:

- `OWNER` — ваш аккаунт/организация GitHub.
- `schedule.example.com` — ваш домен.
- `192.168.1.50` — постоянный LAN-адрес сервера.
- `192.168.1.0/24` — ваша доверенная LAN-подсеть. Не используйте эту маску, если ваша сеть другая.
- `ADMIN_USER` — ваш административный пользователь Ubuntu.

Внешний путь: браузер → HTTPS 443 → Nginx → Java 127.0.0.1:8080 → PostgreSQL loopback:5432. Боты сами подключаются к API мессенджеров через исходящий HTTPS. В проекте пока реализован polling, webhook endpoint нет.

## 2. Проверить домашнюю сеть

1. Зарезервируйте LAN-IP сервера в DHCP маршрутизатора.
2. Сравните WAN IPv4 маршрутизатора с внешним IPv4, видимым из интернета. Частный WAN или диапазон `100.64.0.0/10` указывает на вероятный CGNAT. Несовпадение адресов требует проверки двойного NAT/провайдера.
3. При публичном IPv4 создайте DNS A на внешний адрес. При динамическом IP настройте обновление DNS/DDNS и проверьте обновление после смены адреса.
4. Пробросьте TCP 80 и 443 на сервер. Не пробрасывайте 8080 и 5432. В основном варианте не пробрасывайте SSH.
5. Добавляйте AAAA только при реально доступном IPv6. IPv6 часто не требует port forwarding, но требует firewall на сервере и маршрутизаторе.
6. Проверяйте HTTPS с телефона через мобильную сеть. Проверка только через домашний Wi-Fi не доказывает внешнюю доступность.
7. Если домен не открывается из домашней сети при рабочем внешнем доступе, проверьте NAT loopback или настройте split DNS на LAN-IP.

**При CGNAT основной вариант с пробросом не работает.** Варианты: заказать публичный IP; использовать доступный публичный IPv6 с учётом клиентов; поставить публичный reverse proxy/VPS и туннель до дома. В последнем случае HTTPS завершается на публичном proxy, к домашнему Nginx идёт защищённый туннель. Защитите домашний upstream firewall и настройте доверие real IP только от proxy. Домен указывает на публичный proxy. Деплой JAR остаётся через LAN/VPN.

DNS-01 помогает выпустить сертификат без входящего 80, но не делает сервер доступным пользователям. HTTP-01 требует доступного порта 80. [Let's Encrypt: типы проверок](https://letsencrypt.org/docs/challenge-types/).

## 3. Подготовить Git к переносу

Команды этого раздела выполняйте на компьютере в PowerShell:

```powershell
Set-Location 'C:\Users\Lime\IdeaProjects\ugk-schedule'
git status --short
git branch --show-current
git remote -v
git ls-files .env
git check-ignore .env
git log --all --format= --name-only -- .env '*.pem' '*.key' 'id_*'
```

Сейчас ветка — `master`. Не переименовывайте её в `main`, не исправив условия workflow. `.env` не должен быть tracked. `git remote -v` может показать встроенные в URL credentials: не публикуйте такой вывод, замените URL на SSH/HTTPS без секретов.

Установите Gitleaks из проверенного официального релиза и выполните:

```powershell
gitleaks git --redact --log-opts='--all' .
```

Проверьте также подготовленные к коммиту файлы и незакоммиченный исходный код. `gitleaks dir --redact .` может сканировать локальную `.env`, target и логи; найденные локальные секреты не означают, что они уже попали в Git. Разберите результаты по пути и коммиту. Не добавляйте отчёты с секретами в репозиторий. [Команды Gitleaks](https://github.com/gitleaks/gitleaks).

Если секрет уже был закоммичен, сначала отзовите/смените его, затем очищайте историю по отдельному плану. Удаление файла в новом коммите не удаляет старые версии. Приватность репозитория не отменяет необходимость ротации.

Проверьте `.gitignore`: исключены `.env`, `target/`, `.idea/`, `logs/`. Добавьте подходящие исключения для `.env.*` с сохранением безопасных `.example`, приватных SSH-ключей и дампов. Не исключайте все `.cer`: сертификат CA MAX публичный и нужен приложению.

`README.md` уже отслеживается Git, хотя записан в `.gitignore`: tracked-файл всё равно обновляется. В новом репозитории без истории игнорирование README помешает добавить его. Для обычного переноса существующей истории это не блокер.

### Предотвратить преждевременный автоматический деплой

Текущий `.github/workflows/deploy.yml` запускается при push в `master` и требует `VPS_*` secrets. Для основного ручного варианта отключите его **до первого push**:

```powershell
git mv .github/workflows/deploy.yml .github/workflows/deploy.yml.disabled
```

Так вы сохраните шаблон для будущего включения. Альтернатива — преобразовать его в CI без шагов SSH/deploy. Не используйте публичный домашний SSH только ради сохранения шаблона.

## 4. Создать приватный репозиторий

В GitHub откройте New repository. Название `ugk-schedule`, видимость **Private**. Создайте пустой репозиторий без README, лицензии и `.gitignore`, поскольку локальная история уже существует.

Настройте GitHub SSH-ключ на компьютере либо Git Credential Manager для HTTPS. GitHub пароль аккаунта не используется как пароль `git push`. Включите 2FA для GitHub.

После ручного просмотра изменённых файлов:

```powershell
git add docs/PRODUCTION_REVIEW.md docs/HOME_SERVER.md
git add .github/workflows/deploy.yml.disabled
git diff --cached --stat
git diff --cached
```

`git mv` уже подготовил удаление исходного workflow. Добавляйте другие исправления безопасности только после проверки. Затем:

```powershell
git commit -m "docs: add production review and home deployment guide"
git remote add private-github git@github.com:OWNER/ugk-schedule.git
git push -u private-github master
```

Если `private-github` уже существует, сначала проверьте его адрес; при необходимости используйте `git remote set-url private-github ...`. Отдельный remote сохраняет старый origin и не меняет прежний репозиторий. Переносятся история и содержимое `master`; остальные ветки/теги отправляйте отдельно после проверки. Не применяйте `--mirror` без понимания всех refs.

Проверьте в браузере значок Private, наличие файлов, отсутствие `.env`, логов, JAR, dump и ключей. Если нужно перенести владельца уже существующего GitHub repository, это отдельная операция Transfer ownership; создание нового remote её не выполняет.

Источник: [GitHub: импорт локального проекта](https://docs.github.com/en/migrations/importing-source-code/using-the-command-line-to-import-source-code/adding-locally-hosted-code-to-github).

### Настройки GitHub

- Выдайте доступ только нужным людям; deploy-ключ при необходимости ограничьте одним репозиторием и read-only.
- Оставьте GITHUB_TOKEN с минимальными правами `contents: read`.
- Добавьте CI на pull request и push: Java 17, `mvn clean verify`, Node.js и `node --test src/test/js/miniapp.test.cjs`.
- Добавьте Dependabot для Maven и GitHub Actions; закрепите Actions проверенными полными commit SHA.
- Запустите SCA, например OWASP Dependency-Check, с обновляемой базой уязвимостей. Разберите результаты/false positives, а не только exit code. Не считайте успешный Maven test проверкой CVE. [OWASP Dependency-Check](https://owasp.org/projects/dependency-check).
- Включите доступные на вашем плане Dependabot alerts/secret protection и защиту основной ветки. Не рассчитывайте на функции, недоступные для вашего private repository.
- Когда появится deploy, отделите job сборки от job с SSH secrets; используйте production environment и доступные правила защиты.

GitHub secret scanning для private repository зависит от типа владельца и тарифа. Локальная проверка истории обязательна независимо от доступности функции. [GitHub: secret scanning](https://docs.github.com/en/code-security/concepts/secret-security/secret-scanning).

## 5. Подготовить Ubuntu

Далее команды Bash выполняйте на сервере через LAN/VPN. Первый вход — существующий пользователь с sudo.

```bash
sudo apt update
sudo apt upgrade
sudo apt install -y openjdk-17-jre-headless postgresql nginx curl ufw openssh-server certbot python3-certbot-nginx
sudo systemctl enable --now postgresql nginx ssh
java -version
timedatectl status
```

При необходимости:

```bash
sudo timedatectl set-timezone Asia/Yekaterinburg
sudo timedatectl set-ntp true
```

Корректные часы нужны проверке auth_date Mini App. Настройте автоматические обновления ОС и контролируемые перезагрузки. В BIOS/UEFI включите восстановление питания; VM должна автоматически запускаться после старта хоста. Желателен UPS.

На компьютере создайте отдельный SSH-ключ для ручного деплоя с passphrase:

```powershell
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\ugk-home-deploy" -C ugk-home-deploy
```

Не перезаписывайте существующий ключ. Приватный ключ остаётся на компьютере.

На сервере:

```bash
sudo adduser --disabled-password --gecos '' deploy
sudo useradd --system --user-group --home-dir /opt/ugk-schedule --shell /usr/sbin/nologin ugk-schedule
sudo install -d -o deploy -g ugk-schedule -m 0755 /opt/ugk-schedule
sudo install -d -o deploy -g deploy -m 0700 /home/deploy/.ssh
sudo touch /home/deploy/.ssh/authorized_keys
sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
sudo chmod 0600 /home/deploy/.ssh/authorized_keys
sudo nano /home/deploy/.ssh/authorized_keys
```

Вставьте только содержимое публичного `.pub`. Можно добавить `restrict ` перед `ssh-ed25519`: это запрещает forwarding и PTY, оставляя возможность запуска команды деплоя. Проверьте вход ключом во второй сессии прежде, чем ограничивать SSH/firewall.

Для firewall с реальной LAN-подсетью:

```bash
sudo ufw allow from 192.168.1.0/24 to any port 22 proto tcp
sudo ufw allow 'Nginx Full'
sudo ufw enable
sudo ufw status verbose
```

Если администрируете через VPN, сначала добавьте правило для его подсети/интерфейса. Не отрезайте текущую SSH-сессию. После проверки входа ключами запретите SSH login root и парольный вход в конфигурации sshd, проверьте `sudo sshd -t`, затем reload службы. Маршрутизатор должен разрешать WAN только 80/443.

## 6. Создать PostgreSQL и перенести данные

На сервере:

```bash
sudo -u postgres psql
```

В psql:

```sql
CREATE ROLE ugk_schedule LOGIN;
\password ugk_schedule
CREATE DATABASE ugk_schedule OWNER ugk_schedule;
\q
```

Назначьте длинный уникальный пароль в менеджере паролей. Не задавайте роль SUPERUSER. Приложению нужны права миграций в его собственной БД.

```bash
psql -h localhost -U ugk_schedule -d ugk_schedule -W -c 'SELECT 1;'
sudo ss -lntp
```

5432 должен слушать loopback, а не внешние интерфейсы. Проверьте PostgreSQL `listen_addresses` и `pg_hba.conf`; не добавляйте `0.0.0.0/0 trust`. При необходимости перезапустите PostgreSQL и повторите проверку.

### Новая установка

Ничего не восстанавливайте. На первом запуске Flyway создаст схему и seed-каталог. Администратор создастся из серверной конфигурации.

### Сохранить существующее расписание и настройки

Сначала сделайте тест переноса в отдельную БД. Для финального переноса остановите локальное приложение/запись администраторов, чтобы после dump не потерять новые изменения. На компьютере с установленными клиентскими утилитами PostgreSQL:

```powershell
pg_dump -h localhost -U postgres -d ugk_schedule -W -Fc --no-owner --no-acl -f "$env:USERPROFILE\ugk_schedule-migration.dump"
scp "$env:USERPROFILE\ugk_schedule-migration.dump" ADMIN_USER@192.168.1.50:/tmp/ugk_schedule-migration.dump
```

Замените имя исходного пользователя фактическим. Используйте клиент pg_dump совместимой версии: не старше сервера-источника; целевой PostgreSQL должен поддерживать перенос версии исходной БД. Dump содержит user IDs, предпочтения и password hashes; передавайте по SSH, храните приватно, не добавляйте в Git.

На сервере, **только в новой пустой целевой БД до первого запуска приложения**:

```bash
chmod 0600 /tmp/ugk_schedule-migration.dump
pg_restore -h localhost -U ugk_schedule -d ugk_schedule -W --no-owner --no-acl --exit-on-error /tmp/ugk_schedule-migration.dump
```

Проверьте восстановление таблиц и `flyway_schema_history`. Не накатывайте dump поверх уже созданной Flyway-схемы. Не используйте `--clean` против рабочей БД.

Старая таблица `admin_users` восстанавливает старый пароль. Новый `APP_ADMIN_PASSWORD` его не меняет. Если нужен новый bootstrap-admin, задайте уникальный APP_ADMIN_USERNAME, создайте его при запуске, проверьте вход и затем отключите старую учётную запись через контролируемую административную операцию. Не оставляйте прежний `admin/admin123` активным.

После проверки переноса уберите временный dump с `/tmp` и компьютера согласно вашей политике хранения; сохраните отдельную защищённую backup-копию.

## 7. Установить конфигурацию приложения

На компьютере, из корня проекта:

```powershell
scp deploy/ugk-schedule.service deploy/nginx.conf deploy/app.env.example ADMIN_USER@192.168.1.50:/tmp/
```

На сервере:

```bash
sudo install -o root -g ugk-schedule -m 0640 /tmp/app.env.example /opt/ugk-schedule/.env
sudo nano /opt/ugk-schedule/.env
```

Заполните:

```properties
DB_URL=jdbc:postgresql://localhost:5432/ugk_schedule
DB_USERNAME=ugk_schedule
DB_PASSWORD=YOUR_UNIQUE_DATABASE_PASSWORD
APP_ADMIN_USERNAME=YOUR_ADMIN_NAME
APP_ADMIN_PASSWORD=YOUR_UNIQUE_ADMIN_PASSWORD
MINIAPP_URL=https://schedule.example.com/miniapp/schedule
TELEGRAM_BOT_TOKEN=
MAX_BOT_TOKEN=
MINIAPP_REQUEST_LOGGING=false
APP_LOG_DIR=/var/log/ugk-schedule
APP_LOG_MAX_FILE_SIZE=10MB
APP_LOG_HISTORY_DAYS=30

spring.task.scheduling.pool.size=2
spring.http.client.connect-timeout=5s
spring.http.client.read-timeout=15s
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=lax
server.servlet.session.timeout=30m
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.connection-timeout=5000
```

Нижний блок содержит реальные Spring property names. Этот файл импортируется как Java properties, а не shell EnvironmentFile. Без `export`, без окружающих кавычек. Пробелы/обратные слэши имеют правила properties; случайные буквенно-цифровые пароли проще вводить без ошибок. Не выполняйте `source .env`.

`spring.http.client.*` применяется к автоматически настроенному RestClient.Builder Telegram. MAX использует собственный клиент с timeout 10/15 секунд. Scheduler=2 изолирует два polling-задания, но не создаёт параллельную очередь пользователей и не исправляет потерю updates.

Токены вводите только здесь. Не кладите серверную `.env` в GitHub secrets целиком. Пустой token отключает соответствующий polling. Используйте отдельные токены для разработки и production. Остановите локальный экземпляр с production token до запуска сервера.

Параметры Spring подтверждены [документацией Boot 3.5](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html). Проверьте cookie flags реального HTTPS-ответа; тестовый HTTP login с Secure cookie работать не будет.

### systemd

```bash
sudo install -o root -g root -m 0644 /tmp/ugk-schedule.service /etc/systemd/system/ugk-schedule.service
sudo systemctl daemon-reload
sudo systemctl enable ugk-schedule.service
sudo visudo -f /etc/sudoers.d/ugk-schedule-deploy
```

Добавьте ровно:

```text
deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart ugk-schedule.service
```

```bash
sudo chmod 0440 /etc/sudoers.d/ugk-schedule-deploy
sudo visudo -c
```

Не выдавайте deploy общий sudo. Текущий unit использует 8080 и loopback, heap 512 МБ, cache Thymeleaf=true, forwarded headers и sandbox. `.env` читается из WorkingDirectory. CLI-параметры unit имеют приоритет: SERVER_PORT не изменит установленный 8080. Приложение не может менять JAR/конфигурацию, а deploy имеет право поставлять исполняемый код — защищайте его SSH-ключ.

Не стартуйте службу до загрузки JAR. Изменения unit и Nginx не доставляются существующим workflow автоматически; их устанавливают отдельно.

## 8. Nginx, ограничения и приватная админка

Установите шаблон:

```bash
sudo install -o root -g root -m 0644 /tmp/nginx.conf /etc/nginx/sites-available/ugk-schedule
sudo nano /etc/nginx/sites-available/ugk-schedule
```

Для прямого доступа без CDN/туннеля используйте эту исходную HTTP-конфигурацию. Она затем будет дополнена Certbot:

```nginx
# Файл sites-enabled включается внутри http в обычной Ubuntu-конфигурации.
map $request_method $ugk_login_key {
    default "";
    POST $binary_remote_addr;
}
limit_req_zone $ugk_login_key zone=ugk_login:10m rate=5r/m;
limit_req_zone $binary_remote_addr zone=ugk_api:10m rate=50r/s;

server {
    listen 80;
    server_name schedule.example.com;
    server_tokens off;
    client_max_body_size 64k;
    client_body_timeout 15s;
    client_header_timeout 15s;
    limit_req_status 429;

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $remote_addr;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 5s;
    proxy_read_timeout 30s;
    proxy_send_timeout 30s;

    location = /admin/login {
        allow 192.168.1.0/24;
        deny all;
        limit_req zone=ugk_login burst=5 nodelay;
        proxy_pass http://127.0.0.1:8080;
    }

    location ~ ^/(admin|api/admin)(/|$) {
        allow 192.168.1.0/24;
        deny all;
        proxy_pass http://127.0.0.1:8080;
    }

    location /api/public/ {
        limit_req zone=ugk_api burst=100 nodelay;
        proxy_pass http://127.0.0.1:8080;
    }

    location / {
        proxy_pass http://127.0.0.1:8080;
    }
}
```

Это начальные значения, настройте их после нагрузки. 50 RPS ограничивает один IP, не суммарный сервер. При общем NAT колледжа ограничения могут затронуть многих студентов. Дополнительно подберите общий бюджет и соединения после измерений. Директивы лимита определяются в `http`, а применяются в `location`. [Nginx: limit_req](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html).

Для VPN добавьте его CIDR в обе административные location до `deny all`. Не разрешайте произвольный `X-Forwarded-For`: пользователь может подделать заголовок. За туннелем/CDN этот пример требует настройки доверенных proxy и real IP; без неё allow/rate limit будут учитывать адрес proxy.

Не добавляйте глобально `X-Frame-Options: DENY`: это сломает Mini App. Административные страницы уже защищены от embedding Spring Security. CSRF нужно исправить в приложении даже при приватной админке.

Если файла symlink ещё нет:

```bash
sudo ln -s /etc/nginx/sites-available/ugk-schedule /etc/nginx/sites-enabled/ugk-schedule
sudo nginx -t
sudo systemctl reload nginx
sudo certbot --nginx -d schedule.example.com --redirect
sudo systemctl status certbot.timer --no-pager
sudo certbot renew --dry-run
```

Certbot запросит email и согласие с условиями. Для HTTP-01 DNS и порт 80 должны работать снаружи. До появления JAR ответы 502 нормальны. После Certbot проверьте, что административные ограничения и proxy headers сохранились в HTTPS server block. HSTS включайте после проверки корректного HTTPS и срока действия сертификата; Mini App требует действительного публичного TLS-сертификата.

Ограничьте log retention Nginx и journald. Для journald, например, создайте `/etc/systemd/journald.conf.d/ugk-limits.conf`:

```ini
[Journal]
SystemMaxUse=200M
RuntimeMaxUse=100M
MaxRetentionSec=14day
```

Затем `sudo systemctl restart systemd-journald`. Для приложения retention=30 ограничивает дни, но не объём суток: добавьте общий cap в Logback как отдельное исправление. Следите за `df -h`.

## 9. Собрать и развернуть вручную

На компьютере нужен JDK 17 и Maven. Java в PATH сейчас может указывать на Java 8; используйте явный JAVA_HOME.

```powershell
Set-Location 'C:\Users\Lime\IdeaProjects\ugk-schedule'
$env:JAVA_HOME='C:\Users\Lime\.jdks\temurin-17.0.20.1'
& 'C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd' --batch-mode --no-transfer-progress clean verify
```

Продолжайте только после `BUILD SUCCESS`. Для другого компьютера замените реальные пути. Затем:

```powershell
node --test src/test/js/miniapp.test.cjs
Get-FileHash target/ugk-schedule-0.0.1-SNAPSHOT.jar -Algorithm SHA256
scp -i "$env:USERPROFILE\.ssh\ugk-home-deploy" target/ugk-schedule-0.0.1-SNAPSHOT.jar deploy@192.168.1.50:/opt/ugk-schedule/app.jar.next
scp -i "$env:USERPROFILE\.ssh\ugk-home-deploy" deploy/deploy.sh deploy@192.168.1.50:/opt/ugk-schedule/deploy.sh
ssh -i "$env:USERPROFILE\.ssh\ugk-home-deploy" deploy@192.168.1.50 'sha256sum /opt/ugk-schedule/app.jar.next'
```

Сравните SHA256. Перед обновлением рабочей установки сделайте backup БД. Первый запуск не требует backup пустой базы, но требует правильных секретов.

```powershell
ssh -i "$env:USERPROFILE\.ssh\ugk-home-deploy" deploy@192.168.1.50 'bash /opt/ugk-schedule/deploy.sh'
```

Скрипт сохраняет прежний JAR в `app.jar.previous`, атомарно переименовывает следующий JAR, перезапускает службу и опрашивает `/api/public/levels`. При ошибке сервер может остаться с новым нерабочим JAR: автоматического rollback нет. Максимальное время проверки может быть больше двух минут: каждая из 60 попыток имеет timeout до пяти секунд плюс паузу.

На сервере:

```bash
sudo systemctl status ugk-schedule.service --no-pager
sudo journalctl -u ugk-schedule.service -n 100 --no-pager
curl --fail http://127.0.0.1:8080/api/public/levels
curl --fail https://schedule.example.com/api/public/levels
sudo ss -lntp
```

Администратора открывайте из разрешённой LAN/VPN по `https://schedule.example.com/admin`. Проверьте создание, изменение и удаление тестовой записи. Убедитесь, что с мобильного интернета админка даёт 403, а Mini App/API доступны. Java 8080 и PostgreSQL 5432 снаружи недоступны.

## 10. Подключить мессенджеры

### Telegram

- Создайте production-бота через BotFather и вставьте token в серверную `.env`.
- Укажите HTTPS URL `https://schedule.example.com/miniapp/schedule` для меню/Main Mini App. Кнопки бота используют MINIAPP_URL.
- Проверьте, что для этого token не работает другой polling-процесс и не установлен конфликтующий webhook. При смене режима проверьте getWebhookInfo и осознанно снимите webhook; не сбрасывайте ожидающие updates без необходимости.
- Перезапустите приложение после изменения `.env`, откройте `/start`, выберите группу, проверьте кнопку и запуск из меню без groupId.

### MAX

- В партнёрской платформе используйте production token именно связанного бота.
- Привяжите тот же HTTPS Mini App URL.
- Проверьте `/start`, выбор группы, open_app и запуск без start_param.
- Сертификат CA уже включён в JAR и применяется только к MAX. Не отключайте TLS verification при сетевой ошибке.

В репозитории polling реализован для обоих ботов. Переход на webhook требует отдельной реализации, проверки подлинности запросов и идемпотентности. Сначала устраните текущие ошибки обработки updates.

## 11. Резервные копии и проверка восстановления

Цель для небольшого сервиса: ежедневный backup, 14 локальных копий, отдельная зашифрованная копия вне сервера. Это исходная политика: ежедневный backup допускает потерю до суток изменений. Для меньшего RPO нужна более частая копия/PITR. Выберите допустимое время восстановления RTO и проверьте его практически.

На сервере установите root-owned `/usr/local/sbin/ugk-backup`:

```bash
#!/usr/bin/env bash
set -euo pipefail
umask 077
backup_dir=/var/backups/ugk-schedule
install -d -o root -g root -m 0700 "$backup_dir"
stamp=$(date +%F-%H%M%S)
partial="$backup_dir/$stamp.dump.partial"
final="$backup_dir/$stamp.dump"
/usr/sbin/runuser -u postgres -- /usr/bin/pg_dump -Fc --no-owner --no-acl ugk_schedule > "$partial"
test -s "$partial"
/usr/bin/pg_restore --list "$partial" > /dev/null
mv "$partial" "$final"
# Удаляет только обычные .dump старше 14 суток в фиксированном каталоге.
find "$backup_dir" -maxdepth 1 -type f -name '*.dump' -mtime +14 -delete
```

Используйте `sudo nano`, затем:

```bash
sudo chown root:root /usr/local/sbin/ugk-backup
sudo chmod 0700 /usr/local/sbin/ugk-backup
sudo /usr/local/sbin/ugk-backup
sudo crontab -e
```

Добавьте:

```cron
0 3 * * * /usr/local/sbin/ugk-backup
```

`pg_restore --list` не доказывает полноценное восстановление. Cron должен иметь наблюдение за ошибкой: настройте уведомление/мониторинг возраста последней успешной копии. Настройте отдельную задачу копирования dump во внешнее хранилище и проверяйте её результат. Локальная копия на том же диске не защищает от его отказа.

Секреты `.env`, конфигурацию Nginx/systemd и информацию о версии JAR храните отдельной зашифрованной резервной копией. SSH-приватные ключи не копируйте без необходимости. Сохраняйте SHA коммита каждой опубликованной версии.

### Проверка восстановления

На отдельном тестовом PostgreSQL создайте пустую БД `ugk_schedule_restore_test` с подходящей ролью, восстановите dump через pg_restore без `--clean`. В конфигурации тестового приложения укажите эту БД, оба token оставьте пустыми и выберите другой порт. Проверьте запуск Flyway, количество групп/занятий, предпочтения и административные операции. Никогда не запускайте восстановленную тестовую копию с production bot tokens.

## 12. Обновления, откат и мониторинг

Перед каждым релизом: пройти CI/SCA, оценить совместимость миграций, сделать проверенный backup, сохранить предыдущий JAR и конфигурацию. Сначала проверить релиз на staging.

Для отката JAR после подтверждения совместимости текущей схемы БД:

```bash
sudo systemctl stop ugk-schedule.service
sudo -u deploy cp /opt/ugk-schedule/app.jar.previous /opt/ugk-schedule/app.jar.next
sudo -u deploy mv -f /opt/ugk-schedule/app.jar.next /opt/ugk-schedule/app.jar
sudo systemctl start ugk-schedule.service
curl --fail --retry 20 --retry-connrefused --retry-delay 2 http://127.0.0.1:8080/api/public/levels
```

Не выполняйте rollback через deploy.sh: он перезапишет предыдущую копию. Откат JAR не откатывает Flyway. При несовместимой схеме нужен отдельный план восстановления БД; он может потерять изменения после backup. Не восстанавливайте dump поверх production без явного решения об этой потере.

Минимальный мониторинг:

- Внешняя HTTPS-проверка публичного API раз в минуту, уведомление при устойчивом сбое.
- Отдельная проверка ботов: API readiness не обнаружит зависший polling.
- CPU/RSS/heap/GC, место на диске, swap, загрузка канала, DB pool wait.
- Возраст последнего backup и копии вне сервера, ошибки восстановления.
- Срок сертификата и успешность renewal.
- Доля 5xx/429, p95 API, время реакции ботов.

Перезагрузите сервер в согласованное окно и подтвердите автозапуск, доступность API и ботов. Отдельно проведите нагрузочный тест по плану аудита. Не публикуйте Actuator/env/heapdump наружу при текущем `anyRequest().permitAll()`.

## 13. Автоматический deploy через GitHub Actions — необязательный этап

Сначала получите рабочий ручной деплой. Текущий workflow рассчитан на доступный по SSH сервер и ветку `master`.

### Если сервер доступен по SSH из GitHub-hosted runner

Сделайте отдельный ключ без passphrase для Actions, ограничьте его пользователем deploy и `restrict` в authorized_keys. Не используйте ключ своего административного пользователя. В домашней сети потребуется специально организованный SSH-доступ/VPN; основной ручной вариант этого не требует.

Создайте secrets:

- `VPS_HOST` — доступный runner адрес без https://; LAN-IP 192.168.* не подходит.
- `VPS_USER` — deploy.
- `VPS_PORT` — фактический SSH-порт (по умолчанию 22).
- `VPS_SSH_KEY` — полный приватный ключ Actions.
- `VPS_KNOWN_HOSTS` — проверенная строка host key.

Название `VPS_*` можно оставить даже для домашнего сервера: это имена переменных текущего workflow.

Host key получите через доверенный административный доступ:

```bash
sudo cat /etc/ssh/ssh_host_ed25519_key.pub
sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

known_hosts для порта 22:

```text
home.example.com ssh-ed25519 AAAA...REAL_PUBLIC_HOST_KEY...
```

Для другого порта:

```text
[home.example.com]:2222 ssh-ed25519 AAAA...REAL_PUBLIC_HOST_KEY...
```

Проверьте fingerprint независимым доверенным способом. Один `ssh-keyscan` через непроверенную сеть не аутентифицирует сервер. Не отключайте StrictHostKeyChecking.

Верните имя `.github/workflows/deploy.yml` только после настройки secrets и готовности сервера. Закрепите Actions SHA, добавьте JavaScript/SCA/secret checks и отдельный build job; ограничьте выдачу secrets deploy job. Текущий workflow пересылает JAR и выполняет deploy.sh; конфигурацию и backup он не обновляет. `concurrency` не даёт двум deploy выполняться одновременно, но не гарантирует публикацию каждого промежуточного push.

### Если сервер только в LAN/за CGNAT

Текущий SSH workflow без дополнительной сети не заработает. Сохраните ручной deploy через VPN или создайте outbound-соединение runner к вашей VPN с минимальными правами. Self-hosted runner — отдельный вариант, требующий изоляции: выполнение workflow даёт доступ к его машине. Не ставьте доверие ко всему private repository на одну общую LAN-машину; не выполняйте непроверенные PR на production runner. [GitHub: безопасность runners и Actions](https://docs.github.com/en/actions/reference/security/secure-use).

## 14. Проверка перед открытием доступа

- [ ] Блокеры аудита устранены и проверены тестами.
- [ ] GitHub Private, доступ минимальный, секреты и dump отсутствуют в файлах и проверенной истории.
- [ ] Запускается поддерживаемый Java 17, сборка/Java/JavaScript проверки успешны.
- [ ] PostgreSQL работает только локально; пароль уникальный, роль не superuser.
- [ ] Администратор имеет проверенный новый пароль; старые слабые учётные записи отключены.
- [ ] Java слушает loopback; снаружи доступны только нужные веб-порты.
- [ ] Админка закрыта для внешней сети, CSRF включён, cookie Secure/HttpOnly/SameSite проверены.
- [ ] HTTPS и renewal проверены с мобильной сети; IPv6/AAAA настроены корректно.
- [ ] Один poller на token; Telegram и MAX проходят реальные сценарии запуска.
- [ ] Backup автоматически создаётся, копируется вне сервера и реально восстанавливается.
- [ ] Диск/память/боты/сертификат наблюдаются; уведомления приходят при тестовом сбое.
- [ ] Перезагрузка сервера и целевая нагрузка проверены.
