#!/bin/sh
# Скелет регистрации uicapture:// на macOS. Обмен ticket на токен — этап M2.
# Ссылка из админки Jmix: uicapture://new?course=…&server=…&ticket=…
set -eu
echo "Схема: uicapture"
echo "Команда: uicapture://new?course=…&server=…&ticket=…"
echo "Шаблон обработчика: $(CDPATH= cd -- "$(dirname "$0")" && pwd)/Info.plist"
echo "Разобрать ссылку без регистрации схемы:"
echo "  npx ui-capture open-url 'uicapture://new?course=COURSE&server=https%3A%2F%2Flearn.example&ticket=TICKET'"
if [ "$(uname -s)" != "Darwin" ]; then
  echo "Регистрация схемы доступна только на macOS. На этой машине скрипт ничего не записывает."
  exit 0
fi
echo "Установка приложения-обработчика в M2. Сейчас схема только описана, в Launch Services не регистрируется."
