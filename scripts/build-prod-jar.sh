#!/usr/bin/env bash
# Собрать production bootJar платформы и по умолчанию задеплоить по SSH.
#
# Usage:
#   ./scripts/build-prod-jar.sh
#   ./scripts/build-prod-jar.sh --skip-deploy
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${ROOT}/dist/prod"
DO_CLEAN=1
DO_DEPLOY=1
DEPLOY_ARGS=()

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
Usage: build-prod-jar.sh [options]

Собрать bootJar с Vaadin productionMode, скопировать в dist/prod,
затем по умолчанию вызвать deploy-prod-jar.sh.

Options:
  --no-clean        без Gradle clean (по умолчанию clean)
  --out DIR         каталог артефакта (default: <repo>/dist/prod)
  --skip-deploy     только сборка
  --host USER@HOST  в deploy-prod-jar.sh
  --remote DIR      в deploy-prod-jar.sh
  --skip-health     в deploy-prod-jar.sh
  --timeout SEC     в health-check-prod.sh
  --poll SEC        в health-check-prod.sh
  -h, --help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-clean)
      DO_CLEAN=0
      shift
      ;;
    --clean)
      DO_CLEAN=1
      shift
      ;;
    --out)
      OUT_DIR="${2:?--out requires a directory}"
      shift 2
      ;;
    --skip-deploy)
      DO_DEPLOY=0
      shift
      ;;
    --skip-health)
      DEPLOY_ARGS+=("$1")
      shift
      ;;
    --host|--remote|--timeout|--poll)
      DEPLOY_ARGS+=("$1" "${2:?$1 requires a value}")
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

VERSION="$(read_version)"
EXPECTED_JAR="uitrainer-platform-${VERSION}.jar"
EXPECTED_PATH="${ROOT}/platform-backend/build/libs/${EXPECTED_JAR}"
GRADLEW="${ROOT}/platform-backend/gradlew"

echo "==> Output: ${OUT_DIR}"
mkdir -p "${OUT_DIR}"
rm -f "${OUT_DIR}"/*.jar

if [[ "${DO_CLEAN}" -eq 1 ]]; then
  echo "==> Building uitrainer-platform ${VERSION} (clean -Pvaadin.productionMode=true)"
  "${GRADLEW}" --no-daemon -p "${ROOT}/platform-backend" clean bootJar -Pvaadin.productionMode=true
else
  echo "==> Building uitrainer-platform ${VERSION} (-Pvaadin.productionMode=true)"
  "${GRADLEW}" --no-daemon -p "${ROOT}/platform-backend" bootJar -Pvaadin.productionMode=true
fi

shopt -s nullglob
for jar in "${ROOT}/platform-backend/build/libs/uitrainer-platform-"*.jar; do
  base="$(basename "${jar}")"
  if [[ "${base}" == *-plain.jar ]]; then
    rm -f "${jar}"
    continue
  fi
  if [[ "${base}" != "${EXPECTED_JAR}" ]]; then
    echo "    removing stale ${base}"
    rm -f "${jar}"
  fi
done
shopt -u nullglob

if [[ ! -f "${EXPECTED_PATH}" ]]; then
  echo "Expected boot JAR not found: ${EXPECTED_PATH}" >&2
  exit 1
fi

cp -f "${EXPECTED_PATH}" "${OUT_DIR}/${EXPECTED_JAR}"
echo "    copied ${EXPECTED_JAR}"

echo
echo "==> Production JAR ready in ${OUT_DIR}:"
ls -lh "${OUT_DIR}"/*.jar

if [[ "${DO_DEPLOY}" -eq 1 ]]; then
  echo
  echo "==> Deploying to production"
  "${ROOT}/scripts/deploy-prod-jar.sh" --from "${OUT_DIR}" --skip-build "${DEPLOY_ARGS[@]+"${DEPLOY_ARGS[@]}"}"
else
  cat <<EOF

Deploy skipped (--skip-deploy). To deploy later:
  ./scripts/deploy-prod-jar.sh --from ${OUT_DIR}
EOF
fi
