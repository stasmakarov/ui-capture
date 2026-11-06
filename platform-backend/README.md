# Платформа ui-capture (Jmix 2.8, Vaadin Flow, PostgreSQL)

Бэкенд и админка платформы (этап M2, версия 0.6.0). Как поднять окружение, демо-логины и как связать студию с сервером, описано в корневом [README](../README.md#платформа-m2-админка-jmix-публикация-из-студии).

## Запуск

### Локальный PostgreSQL (без Docker)

На машине должен быть PostgreSQL (например 16) на `localhost:5432` и admin-роль `jmix` / `jmix`
(SUPERUSER или как минимум `CREATEDB` + `CREATEROLE`).

Дальше достаточно запуска с профилем `dev` — роль `uicapture`, базы `uicapture` и `uicapture_test`
создаются автоматически до инициализации DataSource/Liquibase:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'   # http://localhost:8090, trainer/trainer
```

В IntelliJ IDEA: Active profiles = `dev` (или VM/Program arguments `--spring.profiles.active=dev`).

Параметры admin-подключения (по умолчанию уже в `application-dev.properties`):

| Свойство | Env (пример) | По умолчанию |
|---|---|---|
| `uicapture.dev.db-admin.url` | `UICAPTURE_DEV_DBADMIN_URL` | `jdbc:postgresql://localhost:5432/postgres` |
| `uicapture.dev.db-admin.username` | `UICAPTURE_DEV_DBADMIN_USERNAME` | `jmix` |
| `uicapture.dev.db-admin.password` | `UICAPTURE_DEV_DBADMIN_PASSWORD` | `jmix` |

URL/логин/пароль приложения по-прежнему: `main.datasource.*` / `UIC_DB_URL`, `UIC_DB_USER`, `UIC_DB_PASSWORD`
(по умолчанию `uicapture` / `uicapture` на `localhost:5432/uicapture`).

Если admin-подключение не удалось, в лог пишется предупреждение, и старт продолжается
(нужно создать БД вручную или поправить `uicapture.dev.db-admin.*`).

### Через Docker Compose

```bash
docker compose up -d                                        # из корня репозитория (Postgres 16, уже с БД uicapture)
./gradlew bootRun --args='--spring.profiles.active=dev'     # http://localhost:8090, trainer/trainer
./gradlew build                                             # сборка и тесты (база uicapture_test)
```

## Устройство

- `entity/`:
  - `Lesson`: урок; `externalId` = `id` из lesson.json, автор, статус, курс, текущая и опубликованная версии.
  - `LessonVersion`: неизменяемая версия; `lesson.json` и `manifest.json` хранятся в JSONB, пакет и `trainer.html` лежат в FileStorage.
  - `LessonAsset`: каждый файл пакета в FileStorage вместе с его SHA-256.
  - `StepRef`: проекция шагов для отчётов.
  - `LifecycleTransition`: журнал переходов.
  - `StudioToken`: в базе хранится только SHA-256 токена.
  - `StudioTicket`: одноразовый тикет для «Записать в студии».
  - `Course`: курс (название, код, описание, владелец) и состав уроков. `CourseRevision` — заглушка под следующие этапы.
- `service/`:
  - `LessonPackageReader` проверяет `.lesson.zip` по тому же контракту, что `tools/package/lessonPackage.ts`.
  - `LessonImportService` создаёт черновик или новую версию.
  - `LessonLifecycleService` ведёт ЖЦ: draft → review → published → archived, а также review → draft с обязательным комментарием; автор-тренер может утвердить свой урок сам.
  - `StudioTokenService` выпускает токены и тикеты.
- `api/StudioApiController`: REST `/api/lessons/import`, `/api/studio/exchange`, `/api/studio/whoami`. Работает без сессии и CSRF, вызывающий определяется по Bearer-токену.
- `security/`: ресурсные роли `admin`, `reviewer`, `trainer`, `student` (плюс `ui-minimal`) и row-level роли `trainer-own-data` (свои уроки, токены и курсы, где пользователь — владелец) и `student-published` (только опубликованное). Ученик экраны курсов не открывает.
- `view/`: экраны «Курсы» (список и карточка с уроками курса, «Записать в студии» для этого курса), «Уроки» (фильтр по статусу, «Записать в студии»), карточка урока (версии, журнал, шаги, кнопки переходов), предпросмотр (`trainer.html` в изолированном iframe), «Мои токены студии».
- `app/DemoDataInitializer`: демо-пользователи и демо-курс в профилях `dev` и `test`.
- `dev/DevDatabaseEnvironmentPostProcessor`: в профиле `dev` создаёт роль/БД через admin JDBC до DataSource и Liquibase (регистрация в `META-INF/spring.factories`; Spring Boot 3.5 подхватывает EPP именно оттуда).

Схема БД задаётся Liquibase: `src/main/resources/io/uicapture/platform/liquibase/`.
