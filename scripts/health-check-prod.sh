#!/usr/bin/env bash
# Дождаться, пока uitrainer.service станет healthy.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOST=""
TIMEOUT_SEC=300
POLL_SEC=5
SERVICE="uitrainer.service"
ROOT_URL="http://127.0.0.1:8090/"
VERSION_URL="http://127.0.0.1:8090/api/version"

load_local_env() {
  local f="${ROOT}/deploy/deploy.local.env"
  if [[ -f "${f}" ]]; then
    set -a
    # shellcheck source=/dev/null
    source "${f}"
    set +a
  fi
}

load_local_env

usage() {
  cat <<'EOF'
Usage: health-check-prod.sh [options]

Ready:
  - systemctl is-active == active
  - journal: Started UiTrainerPlatformApplication с момента ActiveEnterTimestamp
  - soft: HTTP / (200/302/303); /api/version — предупреждение, если не 200

Options:
  --host USER@HOST   или $UITRAINER_DEPLOY_HOST
  --timeout SEC      default 300
  --poll SEC         default 5
  -h, --help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      HOST="${2:?--host requires USER@HOST}"
      shift 2
      ;;
    --timeout)
      TIMEOUT_SEC="${2:?--timeout requires seconds}"
      shift 2
      ;;
    --poll)
      POLL_SEC="${2:?--poll requires seconds}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

if [[ -z "${HOST}" ]]; then
  HOST="${UITRAINER_DEPLOY_HOST:?Set UITRAINER_DEPLOY_HOST=user@host}"
fi

ssh_opts=(-o BatchMode=yes -o ConnectTimeout=15)

remote() {
  ssh "${ssh_opts[@]}" "${HOST}" "$@"
}

service_state() {
  remote "systemctl is-active '${SERVICE}' 2>/dev/null || true" | tr -d '\r\n'
}

service_active_since() {
  remote "systemctl show '${SERVICE}' -p ActiveEnterTimestamp --value 2>/dev/null" | tr -d '\r'
}

journal_app_started_since() {
  local since="$1"
  [[ -n "${since}" ]] || return 1
  remote "journalctl -u '${SERVICE}' --since '${since}' --no-pager 2>/dev/null \
    | grep -E 'Started UiTrainerPlatformApplication' | tail -1" || true
}

http_root_ok() {
  remote "curl -fsS --connect-timeout 2 --max-time 8 -o /dev/null -w '%{http_code}' '${ROOT_URL}' 2>/dev/null | grep -qE '^(200|302|303)$'" || return 1
}

http_version_ok() {
  remote "curl -fsS --connect-timeout 2 --max-time 8 -o /dev/null -w '%{http_code}' '${VERSION_URL}' 2>/dev/null | grep -qE '^200$'" || return 1
}

dump_service() {
  remote "systemctl --no-pager --lines=20 status '${SERVICE}' || true; \
          journalctl -u '${SERVICE}' -n 50 --no-pager || true" >&2 || true
}

echo "==> Health check on ${HOST}"
echo "    timeout ${TIMEOUT_SEC}s, poll every ${POLL_SEC}s"

FAILED=0
deadline=$((SECONDS + TIMEOUT_SEC))
ready=0
active_since=""

while (( SECONDS < deadline )); do
  state="$(service_state)"

  if [[ "${state}" == "failed" ]]; then
    echo "    FAIL ${SERVICE} (systemd=failed)" >&2
    dump_service
    FAILED=1
    break
  fi

  if [[ "${state}" != "active" ]]; then
    active_since=""
    echo "    … ${SERVICE} systemd=${state:-unknown}"
    sleep "${POLL_SEC}"
    continue
  fi

  if [[ -z "${active_since}" ]]; then
    active_since="$(service_active_since)"
    if [[ -z "${active_since}" ]]; then
      echo "    … ${SERVICE} systemd=active, waiting for activation timestamp"
      sleep "${POLL_SEC}"
      continue
    fi
  fi

  started_line="$(journal_app_started_since "${active_since}")"
  if [[ -z "${started_line}" ]]; then
    echo "    … ${SERVICE} booting (no Started UiTrainerPlatformApplication since ${active_since})"
    sleep "${POLL_SEC}"
    continue
  fi

  if http_root_ok; then
    echo "    OK   ${SERVICE} (app started, HTTP / reachable)"
  else
    echo "    OK   ${SERVICE} (app started; HTTP / not confirmed)"
  fi
  if http_version_ok; then
    echo "    OK   /api/version"
  else
    echo "    WARN /api/version not 200" >&2
  fi
  ready=1
  break
done

if [[ "${ready}" -eq 0 && "${FAILED}" -eq 0 ]]; then
  state="$(service_state)"
  echo "    FAIL ${SERVICE} (timed out, systemd=${state:-unknown})" >&2
  dump_service
  FAILED=1
fi

if [[ "${FAILED}" -ne 0 ]]; then
  echo "==> Health check FAILED" >&2
  exit 1
fi

echo "==> Service healthy"
