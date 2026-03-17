#!/usr/bin/env bash
set -euo pipefail

BASE_DIR=$(cd "$(dirname "$0")/../.." && pwd)
LOG_DIR="${BASE_DIR}/logs"
PID_FILE="${LOG_DIR}/backend.pid"
JAR_PATH="${BASE_DIR}/knowledge-box-backend.jar"
STOP_TIMEOUT_SECONDS=${KB_BACKEND_STOP_TIMEOUT_SECONDS:-30}
STOP_POLL_INTERVAL_SECONDS=${KB_BACKEND_STOP_POLL_INTERVAL_SECONDS:-1}
FORCE_KILL_ON_TIMEOUT=${KB_BACKEND_FORCE_KILL_ON_TIMEOUT:-true}

list_backend_pids() {
  pgrep -f "${JAR_PATH}" || true
}

wait_for_backend_exit() {
  local waited=0
  local current_pids

  while true; do
    current_pids=$(list_backend_pids | tr '\n' ' ' | xargs echo -n)
    if [[ -z "${current_pids}" ]]; then
      rm -f "${PID_FILE}"
      echo "[stop-backend-flat] Backend process stopped."
      return 0
    fi

    if (( waited >= STOP_TIMEOUT_SECONDS )); then
      if [[ "${FORCE_KILL_ON_TIMEOUT}" == "true" ]]; then
        kill -9 ${current_pids} 2>/dev/null || true
        sleep 1
        current_pids=$(list_backend_pids | tr '\n' ' ' | xargs echo -n)
        if [[ -z "${current_pids}" ]]; then
          rm -f "${PID_FILE}"
          echo "[stop-backend-flat] Backend process force killed after timeout."
          return 0
        fi
      fi

      echo "[stop-backend-flat] Backend still running after ${STOP_TIMEOUT_SECONDS}s: ${current_pids}" >&2
      return 1
    fi

    sleep "${STOP_POLL_INTERVAL_SECONDS}"
    waited=$((waited + STOP_POLL_INTERVAL_SECONDS))
  done
}

SENT_TERM=0

if [[ -f "${PID_FILE}" ]]; then
  PID=$(cat "${PID_FILE}" 2>/dev/null || true)
  if [[ -n "${PID}" ]] && kill -0 "${PID}" 2>/dev/null; then
    kill "${PID}"
    SENT_TERM=1
    echo "[stop-backend-flat] Sent TERM to pid=${PID} via pid file."
  else
    rm -f "${PID_FILE}"
  fi
fi

PIDS=$(list_backend_pids | tr '\n' ' ' | xargs echo -n)
if [[ -n "${PIDS}" ]]; then
  kill ${PIDS} 2>/dev/null || true
  SENT_TERM=1
  echo "[stop-backend-flat] Sent TERM to: ${PIDS}"
fi

if [[ "${SENT_TERM}" -eq 0 ]]; then
  echo "[stop-backend-flat] No backend process found for ${JAR_PATH}."
  exit 0
fi

wait_for_backend_exit
