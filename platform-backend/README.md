# Платформа ui-capture (Jmix 2.8, Vaadin Flow, PostgreSQL)

Бэкенд и админка платформы (этап M2, версия 0.6.0). Как поднять окружение, демо-логины и как связать студию с сервером, описано в корневом [README](../README.md#платформа-m2-админка-jmix-публикация-из-студии).

```bash
docker compose up -d                                        # из корня репозитория
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
  - `Course`, `CourseRevision`: заглушки под M3.
- `service/`:
  - `LessonPackageReader` проверяет `.lesson.zip` по тому же контракту, что `tools/package/lessonPackage.ts`.
  - `LessonImportService` создаёт черновик или новую версию.
  - `LessonLifecycleService` ведёт ЖЦ: draft → review → published → archived, а также review → draft с обязательным комментарием; автор-тренер может утвердить свой урок сам.
  - `StudioTokenService` выпускает токены и тикеты.
- `api/StudioApiController`: REST `/api/lessons/import`, `/api/studio/exchange`, `/api/studio/whoami`. Работает без сессии и CSRF, вызывающий определяется по Bearer-токену.
- `security/`: ресурсные роли `admin`, `reviewer`, `trainer`, `student` (плюс `ui-minimal`) и row-level роли `trainer-own-data` (свои уроки и токены) и `student-published` (только опубликованное).
- `view/`: экраны «Уроки» (фильтр по статусу, «Записать в студии»), карточка урока (версии, журнал, шаги, кнопки переходов), предпросмотр (`trainer.html` в изолированном iframe), «Мои токены студии».
- `app/DemoDataInitializer`: демо-пользователи и демо-курс в профилях `dev` и `test`.

Схема БД задаётся Liquibase: `src/main/resources/io/uicapture/platform/liquibase/`.
