#!/usr/bin/env bash
set -euo pipefail

BASE_DIR=$(cd "$(dirname "$0")/.." && pwd)
APP_DIR="${BASE_DIR}/app"
CONFIG_DIR="${BASE_DIR}/config"
LOG_DIR="${BASE_DIR}/logs"
TMP_DIR="${BASE_DIR}/tmp"
PID_FILE="${LOG_DIR}/backend.pid"
RUN_MODE="foreground"

usage() {
  cat <<'USAGE'
Usage: deploy/start-backend.sh [options]

Options:
  --daemon              Start in background and write logs/backend.pid
  --pid-file <path>     Override pid file path, default: logs/backend.pid
  -h, --help            Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --daemon)
      RUN_MODE="daemon"
      shift
      ;;
    --pid-file)
      PID_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[start-backend] Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

mkdir -p "${LOG_DIR}" "${TMP_DIR}"

if [[ ! -f "${APP_DIR}/knowledge-box-backend.jar" ]]; then
  echo "[start-backend] Missing app/knowledge-box-backend.jar" >&2
  exit 1
fi

# Force a UTF-8 process locale so non-ASCII env values (for example KB_MAIL_FROM_PERSONAL)
# are decoded correctly by the Java process even when the host defaults to LANG=C/POSIX.
if [[ -z "${LANG:-}" || "${LANG}" == "C" || "${LANG}" == "POSIX" ]]; then
  export LANG=C.UTF-8
fi
if [[ -z "${LC_ALL:-}" || "${LC_ALL}" == "C" || "${LC_ALL}" == "POSIX" ]]; then
  export LC_ALL=C.UTF-8
fi

if [[ -f "${CONFIG_DIR}/knowledge-box.env" ]]; then
  # Export sourced env vars so Spring placeholders are visible to the java process.
  set -a
  # shellcheck disable=SC1091
  source "${CONFIG_DIR}/knowledge-box.env"
  set +a
fi

export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}
export KB_STORAGE_LOCAL_BASE_PATH=${KB_STORAGE_LOCAL_BASE_PATH:-${APP_DIR}/uploads}
export KB_STORAGE_PUBLIC_BASE_URL=${KB_STORAGE_PUBLIC_BASE_URL:-https://www.xuyifei.site/uploads}
export KB_DOCUMENT_BOOTSTRAP_ENABLED=${KB_DOCUMENT_BOOTSTRAP_ENABLED:-true}
export KB_DOCUMENT_BOOTSTRAP_SEED_DIRECTORY=${KB_DOCUMENT_BOOTSTRAP_SEED_DIRECTORY:-${TMP_DIR}/yuque-batch/bootstrap-seeds}
export KB_DOCUMENT_BOOTSTRAP_SEED_DIRECTORY_PATTERN=${KB_DOCUMENT_BOOTSTRAP_SEED_DIRECTORY_PATTERN:-*.seed.json}
export KB_DOCUMENT_BOOTSTRAP_SEED_DIRECTORY_RECURSIVE=${KB_DOCUMENT_BOOTSTRAP_SEED_DIRECTORY_RECURSIVE:-true}
export KB_DOCUMENT_BOOTSTRAP_FAIL_FAST=${KB_DOCUMENT_BOOTSTRAP_FAIL_FAST:-false}

JAVA_OPTS_DEFAULT=(
  "-Xms512m"
  "-Xmx1536m"
  "-XX:MaxMetaspaceSize=320m"
  "-XX:+UseG1GC"
  "-XX:MaxGCPauseMillis=200"
  "-Djava.security.egd=file:/dev/./urandom"
)

if [[ -n "${JAVA_OPTS:-}" ]]; then
  # shellcheck disable=SC2206
  JAVA_OPTS_ARRAY=(${JAVA_OPTS})
else
  JAVA_OPTS_ARRAY=("${JAVA_OPTS_DEFAULT[@]}")
fi

JAR_PATH="${APP_DIR}/knowledge-box-backend.jar"
JAVA_CMD=(
  java
  "${JAVA_OPTS_ARRAY[@]}"
  -jar "${JAR_PATH}"
  --spring.config.additional-location="file:${CONFIG_DIR}/"
)

if [[ "${RUN_MODE}" == "daemon" ]]; then
  if [[ -f "${PID_FILE}" ]]; then
    EXISTING_PID=$(cat "${PID_FILE}" 2>/dev/null || true)
    if [[ -n "${EXISTING_PID}" ]] && kill -0 "${EXISTING_PID}" 2>/dev/null; then
      echo "[start-backend] Backend already running. pid=${EXISTING_PID} pidFile=${PID_FILE}" >&2
      exit 1
    fi
    rm -f "${PID_FILE}"
  fi

  nohup "${JAVA_CMD[@]}" >> "${LOG_DIR}/backend.out.log" 2>&1 &
  PID=$!
  echo "${PID}" > "${PID_FILE}"
  echo "[start-backend] Started in background. pid=${PID} pidFile=${PID_FILE} log=${LOG_DIR}/backend.out.log"
  exit 0
fi

echo "[start-backend] Starting in foreground. log=${LOG_DIR}/backend.out.log profile=${SPRING_PROFILES_ACTIVE} bootstrapDir=${KB_DOCUMENT_BOOTSTRAP_SEED_DIRECTORY}" >&2
exec "${JAVA_CMD[@]}" >> "${LOG_DIR}/backend.out.log" 2>&1
