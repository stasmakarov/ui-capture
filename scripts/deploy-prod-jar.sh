#!/usr/bin/env bash
# Выкатить production JAR на Ubuntu по SSH: stop → bak → scp → start → health.
# При провале health-check — откат на app.jar.bak.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FROM_DIR="${ROOT}/dist/prod"
HOST=""
REMOTE=""
VERSION=""
SKIP_STOP=0
SKIP_START=0
SKIP_BUILD=0
DO_HEALTH=1
HEALTH_ARGS=()
SERVICE="uitrainer.service"
REMOTE_JAR="app.jar"
REMOTE_BAK="app.jar.bak"

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
Usage: deploy-prod-jar.sh [options]

Остановить uitrainer.service, сохранить app.jar.bak, скопировать JAR,
запустить сервис и проверить health-check-prod.sh.

Хост только из UITRAINER_DEPLOY_HOST или --host (user@ip). Без зашитого IP.

Options:
  --host USER@HOST   SSH (или $UITRAINER_DEPLOY_HOST)
  --remote DIR       каталог на сервере (default: /opt/uitrainer)
  --from DIR         локальный каталог JAR (default: dist/prod)
  --version X.Y.Z    суффикс JAR (default: из platform-backend/build.gradle)
  --skip-stop
  --skip-start
  --skip-build       не собирать, если JAR нет — ошибка
  --skip-health
  --timeout SEC
  --poll SEC
  -h, --help
EOF
}

read_version() {
  local build_gradle="${ROOT}/platform-backend/build.gradle"
  local version
  version="$(sed -nE "s/^version[[:space:]]*=[[:space:]]*['\"]([^'\"]+)['\"].*/\1/p" "${build_gradle}" | head -1)"
  if [[ -z "${version}" ]]; then
    echo "Could not read version from ${build_gradle}" >&2
    return 1
  fi
  printf '%s\n' "${version}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      HOST="${2:?--host requires USER@HOST}"
      shift 2
      ;;
    --remote)
      REMOTE="${2:?--remote requires a directory}"
      shift 2
      ;;
    --from)
      FROM_DIR="${2:?--from requires a directory}"
      shift 2
      ;;
    --version)
      VERSION="${2:?--version requires X.Y.Z}"
      shift 2
      ;;
    --skip-stop)
      SKIP_STOP=1
      shift
      ;;
    --skip-start)
      SKIP_START=1
      shift
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --skip-health)
      DO_HEALTH=0
      shift
      ;;
    --timeout|--poll)
      HEALTH_ARGS+=("$1" "${2:?$1 requires a value}")
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
REMOTE="${REMOTE:-${UITRAINER_DEPLOY_REMOTE:-/opt/uitrainer}}"

if [[ -z "${VERSION}" ]]; then
  VERSION="$(read_version)"
fi

LOCAL_JAR="uitrainer-platform-${VERSION}.jar"

if [[ "${SKIP_BUILD}" -eq 0 ]]; then
  echo "==> Building current working tree → ${FROM_DIR}/${LOCAL_JAR}"
  "${ROOT}/scripts/build-prod-jar.sh" --skip-deploy --out "${FROM_DIR}"
elif [[ ! -f "${FROM_DIR}/${LOCAL_JAR}" ]]; then
  echo "Missing local JAR: ${FROM_DIR}/${LOCAL_JAR}" >&2
  echo "Build first: ./scripts/build-prod-jar.sh --skip-deploy" >&2
  exit 1
fi

if [[ ! -f "${FROM_DIR}/${LOCAL_JAR}" ]]; then
  echo "Expected JAR still missing after build: ${FROM_DIR}/${LOCAL_JAR}" >&2
  exit 1
fi

verify_production_jar() {
  local jar_path="$1"
  local build_info
  build_info="$(unzip -p "${jar_path}" 'META-INF/VAADIN/config/flow-build-info.json' 2>/dev/null || true)"
  if [[ -z "${build_info}" ]]; then
    echo "Refusing to deploy ${jar_path}: missing Vaadin flow-build-info.json" >&2
    echo "Build with: ./scripts/build-prod-jar.sh --skip-deploy" >&2
    exit 1
  fi
  if ! grep -q '"productionMode"[[:space:]]*:[[:space:]]*true' <<<"${build_info}"; then
    echo "Refusing to deploy ${jar_path}: Vaadin productionMode is not true (dev bootJar)." >&2
    echo "Build with: ./scripts/build-prod-jar.sh --skip-deploy" >&2
    exit 1
  fi
}

verify_production_jar "${FROM_DIR}/${LOCAL_JAR}"

ssh_opts=(-o BatchMode=yes -o ConnectTimeout=15)

echo "==> Deploy uitrainer ${VERSION}"
echo "    host:   ${HOST}"
echo "    remote: ${REMOTE}"
echo "    from:   ${FROM_DIR}/${LOCAL_JAR}"

if [[ "${SKIP_STOP}" -eq 0 ]]; then
  echo "==> Stop ${SERVICE}"
  ssh "${ssh_opts[@]}" "${HOST}" "systemctl stop ${SERVICE} || true"
else
  echo "==> Skip stop"
fi

echo "==> Backup and copy JAR"
ssh "${ssh_opts[@]}" "${HOST}" "
  if [[ -f ${REMOTE}/${REMOTE_JAR} ]]; then
    cp -f ${REMOTE}/${REMOTE_JAR} ${REMOTE}/${REMOTE_BAK}
  fi
"
scp "${ssh_opts[@]}" "${FROM_DIR}/${LOCAL_JAR}" "${HOST}:${REMOTE}/${REMOTE_JAR}"
ssh "${ssh_opts[@]}" "${HOST}" "chown uitrainer:uitrainer ${REMOTE}/${REMOTE_JAR} && ls -lh ${REMOTE}/${REMOTE_JAR}"

if [[ "${SKIP_START}" -eq 0 ]]; then
  echo "==> Start ${SERVICE}"
  ssh "${ssh_opts[@]}" "${HOST}" "systemctl start ${SERVICE}"
else
  echo "==> Skip start"
fi

rollback() {
  echo "==> ROLLBACK to ${REMOTE_BAK}" >&2
  ssh "${ssh_opts[@]}" "${HOST}" "
    if [[ -f ${REMOTE}/${REMOTE_BAK} ]]; then
      systemctl stop ${SERVICE} || true
      cp -f ${REMOTE}/${REMOTE_BAK} ${REMOTE}/${REMOTE_JAR}
      chown uitrainer:uitrainer ${REMOTE}/${REMOTE_JAR}
      systemctl start ${SERVICE}
      echo ROLLBACK to ${REMOTE_BAK}
    else
      echo 'No ${REMOTE_BAK} to restore' >&2
      exit 1
    fi
  "
}

if [[ "${DO_HEALTH}" -eq 1 && "${SKIP_START}" -eq 0 ]]; then
  echo
  echo "==> Health check"
  if ! "${ROOT}/scripts/health-check-prod.sh" --host "${HOST}" "${HEALTH_ARGS[@]+"${HEALTH_ARGS[@]}"}"; then
    if ssh "${ssh_opts[@]}" "${HOST}" "test -f ${REMOTE}/${REMOTE_BAK}"; then
      rollback
    else
      echo "Health check failed and ${REMOTE_BAK} is missing" >&2
    fi
    exit 1
  fi
elif [[ "${SKIP_START}" -eq 0 ]]; then
  echo
  echo "Health check skipped. Verify later with:"
  echo "  ./scripts/health-check-prod.sh --host ${HOST}"
fi

echo "==> Done"
