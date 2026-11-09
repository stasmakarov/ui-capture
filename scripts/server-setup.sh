#!/usr/bin/env bash
# Разовая подготовка Ubuntu: Java 21, PostgreSQL, user, dirs, systemd, app.env.
# Повторный запуск не ломает уже созданное. Сервис не стартует (jar ещё нет).
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HOST=""
DB_NAME="uitrainer"
DB_USER="uitrainer"
DB_PASSWORD=""
ENV_FILE=""
GENERATED_PASSWORD=0

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
Usage: server-setup.sh [options]

Options:
  --host USER@HOST     SSH (или $UITRAINER_DEPLOY_HOST)
  --db-password PASS   пароль роли PostgreSQL uitrainer (иначе сгенерировать при создании)
  --env-file PATH      локальный app.env → /etc/uitrainer/app.env
  -h, --help
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)
      HOST="${2:?--host requires USER@HOST}"
      shift 2
      ;;
    --db-password)
      DB_PASSWORD="${2:?--db-password requires a value}"
      shift 2
      ;;
    --env-file)
      ENV_FILE="${2:?--env-file requires a path}"
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

SERVER_IP="${HOST#*@}"

echo "==> Server setup on ${HOST}"

echo "==> Java 21"
if ! remote "test -x /usr/lib/jvm/java-21-openjdk-amd64/bin/java"; then
  echo "    installing openjdk-21-jre-headless"
  remote "DEBIAN_FRONTEND=noninteractive apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-21-jre-headless"
fi
remote "/usr/lib/jvm/java-21-openjdk-amd64/bin/java -version 2>&1 | head -1"

echo "==> PostgreSQL (native apt)"
if ! remote "command -v psql >/dev/null"; then
  remote "DEBIAN_FRONTEND=noninteractive apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq postgresql postgresql-contrib"
fi

sql_password() {
  printf '%s' "${DB_PASSWORD}" | sed "s/'/''/g"
}

ROLE_EXISTS="$(remote "sudo -u postgres psql -tAc \"SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'\"" | tr -d '[:space:]')"
if [[ "${ROLE_EXISTS}" != "1" ]]; then
  if [[ -z "${DB_PASSWORD}" ]]; then
    DB_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)"
    GENERATED_PASSWORD=1
  fi
  remote "sudo -u postgres psql -v ON_ERROR_STOP=1 -d postgres -c \"CREATE USER ${DB_USER} WITH PASSWORD '$(sql_password)';\""
elif [[ -n "${DB_PASSWORD}" ]]; then
  remote "sudo -u postgres psql -v ON_ERROR_STOP=1 -d postgres -c \"ALTER USER ${DB_USER} WITH PASSWORD '$(sql_password)';\""
fi

DB_EXISTS="$(remote "sudo -u postgres psql -tAc \"SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'\"" | tr -d '[:space:]')"
if [[ "${DB_EXISTS}" != "1" ]]; then
  remote "sudo -u postgres createdb -O ${DB_USER} ${DB_NAME}"
fi
remote "sudo -u postgres psql -v ON_ERROR_STOP=1 -d ${DB_NAME} -c \"GRANT ALL ON SCHEMA public TO ${DB_USER}; ALTER SCHEMA public OWNER TO ${DB_USER};\""

echo "==> System user and directories"
remote "
  id uitrainer &>/dev/null || useradd --system --home /opt/uitrainer --shell /usr/sbin/nologin uitrainer
  mkdir -p /opt/uitrainer /var/lib/uitrainer/files /etc/uitrainer
  chown uitrainer:uitrainer /opt/uitrainer /var/lib/uitrainer/files
  chmod 750 /var/lib/uitrainer/files /etc/uitrainer
"

echo "==> systemd unit"
scp "${ssh_opts[@]}" "${ROOT}/deploy/uitrainer.service" "${HOST}:/etc/systemd/system/uitrainer.service"
remote "systemctl daemon-reload && systemctl enable uitrainer.service"

echo "==> /etc/uitrainer/app.env"
if [[ -n "${ENV_FILE}" ]]; then
  scp "${ssh_opts[@]}" "${ENV_FILE}" "${HOST}:/etc/uitrainer/app.env"
elif remote "test -f /etc/uitrainer/app.env"; then
  echo "    already exists, not overwritten"
else
  if [[ -z "${DB_PASSWORD}" ]]; then
    echo "Need --db-password to generate app.env (role already exists)" >&2
    echo "Pass --db-password or --env-file" >&2
    exit 1
  fi
  tmp_env="$(mktemp)"
  sed -e "s|<password>|${DB_PASSWORD}|g" \
      -e "s|<SERVER_IP>|${SERVER_IP}|g" \
      "${ROOT}/deploy/app.env.example" > "${tmp_env}"
  scp "${ssh_opts[@]}" "${tmp_env}" "${HOST}:/etc/uitrainer/app.env"
  rm -f "${tmp_env}"
  echo "    uploaded from deploy/app.env.example"
fi
remote "chown root:uitrainer /etc/uitrainer/app.env && chmod 600 /etc/uitrainer/app.env"

echo "==> UFW 8090/tcp"
remote "ufw allow 8090/tcp comment uitrainer || true"

echo "==> Setup complete (service not started; deploy JAR next)"
if [[ "${GENERATED_PASSWORD}" -eq 1 ]]; then
  echo "    DB password (save it): ${DB_PASSWORD}"
fi
echo "    Admin UI: http://${SERVER_IP}:8090"
echo "    Check /etc/uitrainer/app.env (UITRAINER_PUBLIC_URL)"
echo "    Next: ./scripts/build-prod-jar.sh   or IDE task deployProd"
