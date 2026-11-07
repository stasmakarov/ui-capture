# Сверка путей и tools (M1)

Проверено по дереву `main` после 0.4.0. Файлы, которых не было видно в прошлом просмотре Origin, в репозитории есть и остаются в корне, потому что с них стартует `npm start`.

## tools

На месте и используются `package.json` / `bin/uitrainer.js`:

- `tools/cli.ts` — команды `studio`, `package`, `import`, `pack`, `open-url`, запись по URL
- `tools/capture/` — интерактивная запись, сценарии, разбор ошибок запуска
- `tools/pack/` — сборка одного `trainer.html`
- `tools/builder/`, `tools/pipeline/`, `tools/studio/`, `tools/package/` — сборка урока, студия, пакет `.lesson.zip`
- `bin/uitrainer.js` — обёртка, которая запускает `tools/cli.ts`

## Пути внутри урока

`lesson.json` хранит не сами файлы, а пути относительно каталога `lessons/<id>/`:

| Поле | Факт в записанных уроках |
| --- | --- |
| `screen.html` | `screens/<id>.html` |
| `screen.screenshot` | `original/<id>.png` |
| `step.audio` | `audio/<stepId>.wav`, если голос приложен; в уроках Википедии и TodoMVC поля нет |

`compare/` и `replay/` сборщик пишет рядом, но в `lesson.json` на них нет ссылок. В `.lesson.zip` они не входят. В пакет входят `lesson.json`, `screens/`, `original/`, `assets/`, `audio/` и `manifest.json` с SHA-256.

Уроки схемы 1 открываются как раньше. Если в JSON нет `schemaVersion`, `migrateLesson` считает его версией 1. Новая версия схемы не вводилась.
