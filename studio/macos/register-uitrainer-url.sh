#!/bin/sh
# Скелет регистрации uitrainer:// на macOS.
# Ссылка с платформы Jmix: uitrainer://new?course=…&server=…&ticket=…
set -eu
echo "Схема: uitrainer"
echo "Команда: uitrainer://new?course=…&server=…&ticket=…"
echo "Шаблон обработчика: $(CDPATH= cd -- "$(dirname "$0")" && pwd)/Info.plist"
echo "Разобрать ссылку без регистрации схемы:"
echo "  npx uitrainer open-url 'uitrainer://new?course=COURSE&server=https%3A%2F%2Flearn.example&ticket=TICKET'"
if [ "$(uname -s)" != "Darwin" ]; then
  echo "Регистрация схемы доступна только на macOS. На этой машине скрипт ничего не записывает."
  exit 0
fi
echo "Установка приложения-обработчика в M2. Сейчас схема только описана, в Launch Services не регистрируется."
