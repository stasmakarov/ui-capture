# Платформа (Jmix)

Заготовка сервера. В M2 здесь появится проект Jmix 2 (Gradle, не npm): роли, FileStorage, приём пакета `.lesson.zip` и черновик версии урока.

Проверка пакета на Java идёт по `lesson-schema/schema/lesson.schema.json`. Её порождает TypeScript (`npm run schema`), отдельную модель урока в JPA не заводим.

Локальная база для разработки описана в `docker-compose.yml` в корне репозитория.
