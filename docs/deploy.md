# Деплой платформы uitrainer на Ubuntu

systemd + Spring Boot jar. HTTPS и nginx в этом документе только как опция на потом — сейчас HTTP по IP, порт 8090.

**Пилот без HTTPS:** пароли админки и токены студии ходят по сети открытым текстом. Для закрытой сети терпимо. Перед выходом наружу нужен HTTPS.

## Требования

- Ubuntu-сервер с SSH по ключу (ключ в ssh-agent).
- На машине сборки: JDK 21, bash. Node для деплоя платформы не нужен.
- Mac: Temurin/Zulu 21 в PATH. Windows: Git Bash или WSL — те же скрипты.
- Хост **не** зашит в репозиторий: `UITRAINER_DEPLOY_HOST=user@ip`.

## Первичная настройка

```bash
cp deploy/deploy.local.env.example deploy/deploy.local.env
# прописать UITRAINER_DEPLOY_HOST=root@<SERVER_IP>
./scripts/server-setup.sh
```

Скрипт идемпотентен. Ставит OpenJDK 21, родной PostgreSQL (роль и БД `uitrainer`), пользователя `uitrainer`, каталоги `/opt/uitrainer` и `/var/lib/uitrainer/files`, unit `uitrainer.service` (enable, без start), `/etc/uitrainer/app.env`, UFW 8090/tcp.

Проверьте на сервере `UITRAINER_PUBLIC_URL=http://<SERVER_IP>:8090` — это адрес в ссылке `uitrainer://…&server=…`.

Реальный `app.env` и `deploy.local.env` не коммитить.

## Первый деплой

Из IntelliJ: Gradle `platform-backend` → `deploy` → `deployProd`. В Run Configuration можно задать `UITRAINER_DEPLOY_HOST`, либо положиться на `deploy/deploy.local.env`.

Из терминала:

```bash
./scripts/build-prod-jar.sh
```

Скрипт собирает `uitrainer-platform-<version>.jar` (`rootProject.name` в `platform-backend/settings.gradle`) с `-Pvaadin.productionMode=true`, кладёт в `dist/prod/` и выкатывает: stop → `app.jar.bak` → scp → start → health-check.

Только сборка: `./scripts/build-prod-jar.sh --skip-deploy` или Gradle `buildProdJar`.

Дождитесь `Service healthy`. Админка: `http://<SERVER_IP>:8090`.

Профили `dev` и `local` на сервере **не** включать. Демо-пользователи (`trainer` и остальные) создаются только в `dev`/`test`. На проде после Liquibase есть `admin` / `admin`. Преподавателя создайте кнопкой в кабинете администратора.

## Обновление

Снова `deployProd` / `build-prod-jar.sh`. Предыдущий jar остаётся в `/opt/uitrainer/app.jar.bak`. Если health-check падает, скрипт сам откатывает bak, печатает `ROLLBACK to app.jar.bak` и выходит с ошибкой.

## Откат вручную

```bash
ssh "$UITRAINER_DEPLOY_HOST" 'systemctl stop uitrainer && cp /opt/uitrainer/app.jar.bak /opt/uitrainer/app.jar && systemctl start uitrainer'
./scripts/health-check-prod.sh
```

## Логи

```bash
ssh "$UITRAINER_DEPLOY_HOST" 'journalctl -u uitrainer -f'
ssh "$UITRAINER_DEPLOY_HOST" "journalctl -u uitrainer --since '10 min ago'"
```

Health смотрит `systemctl is-active` и строку `Started UiTrainerPlatformApplication` в journal с момента текущего запуска, плюс мягкий HTTP на `/` и `/api/version`.

## Бэкап и восстановление

```bash
ssh "$UITRAINER_DEPLOY_HOST" 'sudo -u postgres pg_dump -U uitrainer uitrainer' > backup.sql
# FileStorage:
ssh "$UITRAINER_DEPLOY_HOST" 'tar -C /var/lib/uitrainer -czf - files' > files.tar.gz
```

Восстановление: остановить сервис, `psql -U uitrainer uitrainer < backup.sql`, распаковать `files` в `/var/lib/uitrainer/files`, `chown -R uitrainer:uitrainer`, старт.

## nginx (не ставится скриптами)

Если позже повесите :80 на jar:

```
proxy_pass http://127.0.0.1:8090;
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
proxy_set_header Host $host;
proxy_set_header X-Forwarded-Proto $scheme;
```

При HTTPS уберите `SERVER_SERVLET_SESSION_COOKIE_SECURE=false` (или выставьте true) и смените `UITRAINER_PUBLIC_URL` на `https://…`.

## Сквозная проверка после деплоя

1. `deployProd` → логин `admin`/`admin` с Windows по `http://IP:8090`.
2. Создать преподавателя. Загрузить релиз студии (`.exe` + `latest.yml`) если есть артефакт CI.
3. «Скачать студию» → установка NSIS.
4. «Записать в студии» → `uitrainer://` с `server=UITRAINER_PUBLIC_URL` → обмен тикета.
5. Короткий урок → «Опубликовать на сервер» → черновик в админке.

Известные риски не из этого задания: SmartScreen на неподписанном exe, Chromium Playwright может качаться отдельно, зависание публикации без курса.
