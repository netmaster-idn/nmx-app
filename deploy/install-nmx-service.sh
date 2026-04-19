#!/usr/bin/env bash
set -euo pipefail

APP_USER="${APP_USER:-nmx}"
APP_GROUP="${APP_GROUP:-nmx}"
APP_HOME="${APP_HOME:-/opt/nmx}"
APP_DIR="${APP_DIR:-/opt/nmx/app}"
ENV_DIR="${ENV_DIR:-/etc/nmx}"
SERVICE_NAME="${SERVICE_NAME:-nmx}"
GATEWAY_SERVICE_NAME="${GATEWAY_SERVICE_NAME:-whatsapp-gateway}"
JAR_TARGET="${APP_DIR}/nmx.jar"
GATEWAY_TARGET_DIR="${APP_DIR}/whatsapp-gateway"
WHATSAPP_DATA_DIR="/var/lib/nmx/whatsapp"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_DIR="${REPO_ROOT}/target"

find_jar() {
  find "${TARGET_DIR}" -maxdepth 1 -type f -name 'nmx-*.jar' ! -name '*.original' | sort | tail -n 1
}

echo "Building application..."
(cd "${REPO_ROOT}" && ./mvnw clean package -DskipTests)

JAR_PATH="$(find_jar)"
if [[ -z "${JAR_PATH}" ]]; then
  echo "JAR file not found in target/ after build." >&2
  exit 1
fi

echo "Preparing directories..."
sudo mkdir -p \
  "${APP_DIR}" \
  /var/lib/nmx/uploads/company-logos \
  /var/log/nmx \
  "${ENV_DIR}" \
  "${WHATSAPP_DATA_DIR}/sessions" \
  "${WHATSAPP_DATA_DIR}/runtime"

if ! getent group "${APP_GROUP}" >/dev/null 2>&1; then
  sudo groupadd --system "${APP_GROUP}"
fi

if ! id -u "${APP_USER}" >/dev/null 2>&1; then
  sudo useradd --system --home "${APP_HOME}" --shell /usr/sbin/nologin --gid "${APP_GROUP}" "${APP_USER}"
fi

echo "Copying Spring Boot artifact..."
sudo install -m 0644 "${JAR_PATH}" "${JAR_TARGET}"
sudo install -m 0644 "${SCRIPT_DIR}/nmx.service" "/etc/systemd/system/${SERVICE_NAME}.service"

if [[ ! -f "${ENV_DIR}/nmx.env" ]]; then
  sudo install -m 0644 "${SCRIPT_DIR}/nmx.env.example" "${ENV_DIR}/nmx.env"
fi

echo "Syncing WhatsApp gateway source..."
sudo mkdir -p "${APP_DIR}"
tar \
  --exclude='whatsapp-gateway/node_modules' \
  --exclude='whatsapp-gateway/storage' \
  --exclude='whatsapp-gateway/.wwebjs_cache' \
  --exclude='whatsapp-gateway/bootstrap-start.log' \
  -cf - -C "${REPO_ROOT}" whatsapp-gateway | sudo tar -xf - -C "${APP_DIR}"

sudo install -m 0644 "${SCRIPT_DIR}/whatsapp-gateway.service" "/etc/systemd/system/${GATEWAY_SERVICE_NAME}.service"
if [[ ! -f "${ENV_DIR}/whatsapp-gateway.env" ]]; then
  sudo install -m 0644 "${SCRIPT_DIR}/whatsapp-gateway.env.example" "${ENV_DIR}/whatsapp-gateway.env"
fi

sudo chown -R "${APP_USER}:${APP_GROUP}" "${APP_HOME}" /var/lib/nmx /var/log/nmx

if command -v npm >/dev/null 2>&1; then
  echo "Installing WhatsApp gateway dependencies..."
  (cd "${GATEWAY_TARGET_DIR}" && sudo -u "${APP_USER}" npm install --omit=dev --no-fund --no-audit)
else
  echo "npm not found. Install Node.js + npm on the server, then run 'cd ${GATEWAY_TARGET_DIR} && npm install --omit=dev --no-fund --no-audit'." >&2
fi

echo "Reloading systemd..."
sudo systemctl daemon-reload
sudo systemctl enable "${GATEWAY_SERVICE_NAME}" "${SERVICE_NAME}"
sudo systemctl restart "${GATEWAY_SERVICE_NAME}"
sudo systemctl restart "${SERVICE_NAME}"

echo "Done. Check service status with:"
echo "  sudo systemctl status ${SERVICE_NAME}"
echo "  sudo systemctl status ${GATEWAY_SERVICE_NAME}"
