#!/usr/bin/env bash
set -euo pipefail

BASE_DIR=$(cd "$(dirname "$0")/.." && pwd)
LOG_DIR="${BASE_DIR}/logs"
APP_DIR="${BASE_DIR}/app"
PID_FILE="${LOG_DIR}/backend.pid"
JAR_PATH="${APP_DIR}/knowledge-box-backend.jar"

if [[ -f "${PID_FILE}" ]]; then
  PID=$(cat "${PID_FILE}" 2>/dev/null || true)
  if [[ -n "${PID}" ]] && kill -0 "${PID}" 2>/dev/null; then
    kill "${PID}"
    rm -f "${PID_FILE}"
    echo "[stop-backend] Sent TERM to pid=${PID} via pid file."
    exit 0
  fi
  rm -f "${PID_FILE}"
fi

PIDS=$(pgrep -f "${JAR_PATH}" || true)
if [[ -z "${PIDS}" ]]; then
  echo "[stop-backend] No backend process found for ${JAR_PATH}."
  exit 0
fi

kill ${PIDS}
echo "[stop-backend] Sent TERM to: ${PIDS}"
