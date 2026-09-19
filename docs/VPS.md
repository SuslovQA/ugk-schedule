# Запуск на VPS и GitHub Actions

Инструкция рассчитана на чистый VPS с **Ubuntu 24.04 LTS**, доступом по SSH и sudo,
доменом с A-записью на IP сервера (если есть AAAA, IPv6 тоже должен вести на VPS).
Практический старт: 1 CPU, 2 ГБ RAM, 10 ГБ диска. Команды ниже выполняются на VPS
под вашим административным пользователем, кроме явно отмеченных локальных действий.
Замените `schedule.example.com`, `VPS_IP` и примеры паролей своими значениями.

Схема: GitHub собирает и тестирует JAR на Java 17 → отправляет его по SSH →
systemd перезапускает приложение → workflow проверяет API и PostgreSQL.
Nginx обслуживает HTTPS, Java слушает только 127.0.0.1:8080, база работает локально.
Docker и Maven на VPS не нужны. Перезапуск вызывает короткий перерыв в обслуживании.

## 1. Установить пакеты

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless postgresql nginx curl ufw openssh-server certbot python3-certbot-nginx
sudo systemctl enable --now postgresql nginx ssh
java -version
```

Откройте SSH **до** включения firewall; при нестандартном порте замените 22.
Оставьте текущую SSH-сессию открытой и проверьте вход из второй сессии.

```bash
sudo ufw allow 22/tcp
sudo ufw allow 'Nginx Full'
sudo ufw enable
sudo ufw status
```

В firewall провайдера также разрешите входящие TCP 22 (или свой SSH-порт), 80 и 443.
Порты 8080 и 5432 наружу открывать не нужно. GitHub-hosted runner должен иметь
сетевой доступ к SSH. Не ограничивайте SSH только своим домашним IP без отдельного
решения для доступа runner.

## 2. Создать базу

```bash
sudo -u postgres psql
```

В консоли PostgreSQL:

```sql
CREATE ROLE ugk_schedule LOGIN;
\password ugk_schedule
CREATE DATABASE ugk_schedule OWNER ugk_schedule;
\q
```

Команда `\password` запросит новый пароль дважды. Сохраните его для `.env`.
Проверьте TCP-подключение, которое использует приложение:

```bash
psql -h localhost -U ugk_schedule -d ugk_schedule -W -c 'SELECT 1;'
```

Flyway создаст таблицы при первом запуске. Отдельный SQL-импорт не нужен.

## 3. Создать пользователей и каталоги

`deploy` загружает JAR; `ugk-schedule` запускает приложение без sudo.

```bash
sudo adduser --disabled-password --gecos '' deploy
sudo useradd --system --user-group --home-dir /opt/ugk-schedule --shell /usr/sbin/nologin ugk-schedule
sudo install -d -o deploy -g ugk-schedule -m 755 /opt/ugk-schedule
sudo install -d -o deploy -g deploy -m 700 /home/deploy/.ssh
sudo touch /home/deploy/.ssh/authorized_keys
sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
sudo chmod 600 /home/deploy/.ssh/authorized_keys
```

На **своём компьютере** создайте отдельный ключ для Actions (пустая passphrase):

```bash
ssh-keygen -t ed25519 -f github-actions-ugk -C github-actions-ugk
```

Откройте `github-actions-ugk.pub` и добавьте его содержимое одной строкой в
`/home/deploy/.ssh/authorized_keys` на VPS, используя `sudo nano`.
Перед `ssh-ed25519` можно добавить `restrict `, чтобы запретить forwarding и PTY.
Приватный файл `github-actions-ugk` не добавляйте в Git и не копируйте на VPS.

## 4. Установить конфигурацию приложения и systemd

На **своём компьютере**, из корня этого репозитория, скопируйте шаблоны под
своим административным SSH-пользователем (`ADMIN_USER`):

```bash
scp deploy/ugk-schedule.service deploy/nginx.conf deploy/app.env.example ADMIN_USER@VPS_IP:/tmp/
```

Для нестандартного порта добавьте `-P ПОРТ` к scp; для ssh используется `-p ПОРТ`.
Далее снова на VPS:

```bash
sudo install -o root -g ugk-schedule -m 640 /tmp/app.env.example /opt/ugk-schedule/.env
sudo nano /opt/ugk-schedule/.env
```

Обязательно заполните `DB_PASSWORD`, `APP_ADMIN_PASSWORD` и `MINIAPP_URL`.
Пароль администратора задайте **до первого запуска**. Приложение создаёт
администратора, только если такого имени нет в базе; последующее изменение
`APP_ADMIN_PASSWORD` не меняет пароль существующей учётной записи.

`.env` читается приложением как Java properties: `KEY=value`, без `export`
и внешних кавычек. Для паролей проще использовать длинные случайные строки
из букв и цифр: обратные слеши в Java properties имеют специальное значение.
Файл хранится только на сервере и не передаётся через Actions.
Пустые токены отключают соответствующие боты. Для Telegram задайте
`TELEGRAM_BOT_TOKEN`; для MAX — `MAX_BOT_TOKEN`, а HTTPS Mini App URL также
укажите в партнёрской платформе MAX. Сертификат MAX уже включён в JAR.
Не запускайте другую копию бота с тем же токеном, в том числе локально.
Текущая реализация использует long polling; этот деплой не добавляет webhooks.

```bash
sudo install -o root -g root -m 644 /tmp/ugk-schedule.service /etc/systemd/system/ugk-schedule.service
sudo systemctl daemon-reload
sudo systemctl enable ugk-schedule.service
sudo visudo -f /etc/sudoers.d/ugk-schedule-deploy
```

В sudoers добавьте ровно одну строку:

```text
deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart ugk-schedule.service
```

```bash
sudo chmod 440 /etc/sudoers.d/ugk-schedule-deploy
sudo visudo -c
```

Пока не запускайте сервис: JAR появится при первом деплое. Порт 8080 фиксирован
в unit и проверке workflow; `SERVER_PORT` из `.env` его не переопределяет.
Java ограничена heap 512 МБ, но суммарное потребление процесса будет больше.
Unit и Nginx устанавливаются один раз; после их изменения в Git повторите
копирование и установку вручную, затем `daemon-reload`/перезапуск сервиса или reload Nginx.

## 5. Настроить Nginx и HTTPS

```bash
sudo install -o root -g root -m 644 /tmp/nginx.conf /etc/nginx/sites-available/ugk-schedule
sudo nano /etc/nginx/sites-available/ugk-schedule
```

Замените `schedule.example.com` своим доменом в `server_name`.

```bash
sudo ln -s /etc/nginx/sites-available/ugk-schedule /etc/nginx/sites-enabled/ugk-schedule
sudo nginx -t
sudo systemctl reload nginx
sudo certbot --nginx -d schedule.example.com --redirect
sudo systemctl status certbot.timer --no-pager
sudo certbot renew --dry-run
```

Для выдачи сертификата DNS должен уже указывать на VPS, а порт 80 быть доступен.
Certbot запросит email и согласие с условиями, добавит HTTPS и перенаправление.
До первого деплоя ответ 502 от Nginx ожидаем: Java ещё не запущена.
Не добавляйте глобальный `X-Frame-Options: DENY`: Mini App открывается внутри мессенджера.

## 6. Настроить GitHub Secrets

В репозитории откройте **Settings → Secrets and variables → Actions → New repository secret**.

| Secret | Значение |
| --- | --- |
| `VPS_HOST` | IP или SSH-домен VPS, без `https://` |
| `VPS_USER` | `deploy` |
| `VPS_PORT` | SSH-порт; можно не создавать, тогда 22 |
| `VPS_SSH_KEY` | Полное содержимое приватного `github-actions-ugk`, включая BEGIN/END |
| `VPS_KNOWN_HOSTS` | Проверенная строка ключа SSH-сервера, см. ниже |

Получите публичный host key через доверенную консоль провайдера или уже
проверенное административное SSH-соединение **на VPS**:

```bash
sudo cat /etc/ssh/ssh_host_ed25519_key.pub
sudo ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub
```

Для `VPS_KNOWN_HOSTS` возьмите первые два поля публичного ключа и поставьте
перед ними **точное значение `VPS_HOST`**:

```text
203.0.113.10 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA...реальный_ключ...
```

При нестандартном порте, например 2222:

```text
[203.0.113.10]:2222 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA...реальный_ключ...
```

Не копируйте эти примеры буквально. Workflow использует строгую проверку
ключа сервера; после переустановки VPS обновите secret, предварительно проверив
новый ключ через консоль провайдера. Не отключайте host key checking.

## 7. Первый и последующие деплои

Добавьте файлы этой настройки в Git и отправьте коммит в `master`.
Откройте **Actions → Build and deploy**. Workflow выполнит:

1. Checkout и установку Java 17 с кешем Maven.
2. `mvn --batch-mode --no-transfer-progress clean verify`, включая тесты.
3. Загрузку JAR в `/opt/ugk-schedule/app.jar.next` по SSH.
4. Сохранение старого JAR в `app.jar.previous`, замену JAR и restart systemd.
5. До 60 попыток проверки `/api/public/levels` (пауза 2 секунды, timeout запроса 5 секунд).

При ошибке сборки деплой не начнётся. При ошибке проверки запуска workflow станет
красным; автоматического отката нет, поскольку Flyway мог уже изменить базу.
Одновременные деплои сериализованы; GitHub может заменять ожидающий запуск более
новым, поэтому не каждый промежуточный push обязательно попадёт на VPS.
Можно повторить запуск кнопкой **Run workflow**, выбрав `master`; другие ветки
пропускаются. При первом старте миграции применятся автоматически.

Проверка на VPS:

```bash
sudo systemctl status ugk-schedule.service --no-pager
sudo journalctl -u ugk-schedule.service -n 100 --no-pager
curl --fail http://127.0.0.1:8080/api/public/levels
curl --fail https://schedule.example.com/api/public/levels
```

Откройте `https://schedule.example.com/admin` и войдите с настроенным паролем.
Mini App: `https://schedule.example.com/miniapp/schedule`.
После перезагрузки VPS systemd запустит приложение автоматически.

## 8. Обслуживание, резервные копии и откат

```bash
# Наблюдать логи (Ctrl+C завершает просмотр)
sudo journalctl -u ugk-schedule.service -f
# После изменения .env
sudo systemctl restart ugk-schedule.service
```

Перед релизами с миграциями сохраняйте backup базы и копию `.env` в защищённом
хранилище вне VPS. Например, под административным пользователем на VPS:

```bash
mkdir -p "$HOME/backups"
chmod 700 "$HOME/backups"
(umask 077; sudo -u postgres pg_dump -Fc ugk_schedule > "$HOME/backups/ugk_schedule-$(date +%F-%H%M%S).dump")
```

Настройте регулярное выполнение и перенос копий с сервера отдельно. Проверяйте
восстановление в отдельную базу через `pg_restore` прежде, чем полагаться на backup.

Ручной откат JAR **после проверки совместимости со схемой БД**, на VPS:

```bash
sudo systemctl stop ugk-schedule.service
sudo -u deploy cp /opt/ugk-schedule/app.jar.previous /opt/ugk-schedule/app.jar.next
sudo -u deploy mv -f /opt/ugk-schedule/app.jar.next /opt/ugk-schedule/app.jar
sudo systemctl start ugk-schedule.service
curl --fail --retry 20 --retry-connrefused --retry-delay 2 http://127.0.0.1:8080/api/public/levels
```

Предыдущий JAR существует только после второго деплоя и перезаписывается при
следующем. Откат JAR не откатывает миграции Flyway; для несовместимых изменений
нужен отдельный план восстановления базы. Новый push в `master` снова обновит JAR.

## 9. Частые проблемы

| Симптом | Что проверить |
| --- | --- |
| `Permission denied (publickey)` | `VPS_USER`, приватный ключ, соответствующий ключ в authorized_keys, владельца и права `.ssh` |
| `Host key verification failed` | Совпадение host/порта в known_hosts и проверенный ключ VPS |
| SSH timeout | IP, SSH-порт, firewall VPS и провайдера, доступность из GitHub |
| sudo просит пароль | Точную строку sudoers, имя пользователя и путь `/usr/bin/systemctl` |
| Readiness check failed / 502 | `journalctl`, DB_PASSWORD, запуск PostgreSQL, права `.env`, наличие JAR |
| Старый пароль admin | Пароль уже сохранён в БД; `.env` задаёт только первоначального пользователя |
| Бот конфликтует / отвечает нестабильно | Вторую копию с тем же токеном, сетевой доступ к API мессенджера |
| Java завершена OOM killer | Память VPS и heap в unit, `journalctl -k` |

Эта инструкция разворачивает текущую функциональность приложения. Указанные в
README задачи по проверке Mini App init-data, аудиту и webhook-интеграциям
остаются отдельными изменениями приложения.

Справка: [actions/checkout](https://github.com/actions/checkout),
[actions/setup-java](https://github.com/actions/setup-java),
[Certbot](https://certbot.eff.org/instructions?ws=nginx&os=snap).
