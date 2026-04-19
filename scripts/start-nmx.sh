#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
TARGET_DIR="${REPO_ROOT}/target"
LOGS_DIR="${REPO_ROOT}/logs"

BUILD=0
DAEMON=0
PROFILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build)
      BUILD=1
      shift
      ;;
    --daemon)
      DAEMON=1
      shift
      ;;
    --profile)
      PROFILE="${2:-}"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      echo "Usage: $0 [--build] [--daemon] [--profile PROFILE]" >&2
      exit 1
      ;;
  esac
done

find_jar() {
  find "${TARGET_DIR}" -maxdepth 1 -type f -name 'nmx-*.jar' ! -name '*.original' | sort | tail -n 1
}

if [[ ${BUILD} -eq 1 || ! -d "${TARGET_DIR}" || -z "$(find_jar)" ]]; then
  echo "Building NMX JAR..."
  (cd "${REPO_ROOT}" && ./mvnw clean package -DskipTests)
fi

JAR_PATH="$(find_jar)"
if [[ -z "${JAR_PATH}" ]]; then
  echo "JAR file not found in target/. Run with --build or check the Maven build." >&2
  exit 1
fi

JAVA_ARGS=()
if [[ -n "${PROFILE}" ]]; then
  JAVA_ARGS+=("-Dspring.profiles.active=${PROFILE}")
fi
JAVA_ARGS+=("-jar" "${JAR_PATH}")

if [[ ${DAEMON} -eq 1 ]]; then
  mkdir -p "${LOGS_DIR}"
  nohup java "${JAVA_ARGS[@]}" > "${LOGS_DIR}/nmx-stdout.log" 2> "${LOGS_DIR}/nmx-stderr.log" &
  echo $! > "${LOGS_DIR}/nmx.pid"
  echo "NMX started in background. PID: $(cat "${LOGS_DIR}/nmx.pid")"
  echo "Logs: ${LOGS_DIR}/nmx-stdout.log and ${LOGS_DIR}/nmx-stderr.log"
  exit 0
fi

echo "Starting NMX from $(basename "${JAR_PATH}")..."
exec java "${JAVA_ARGS[@]}"
