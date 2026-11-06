#!/bin/sh
# Совместимость: прежнее имя скрипта. Регистрирует uitrainer:// и алиас uicapture://.
exec "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/register-uitrainer-url.sh" "$@"
