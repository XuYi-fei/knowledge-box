#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
PROFILE="production"
SKIP_BUILD=0
SKIP_RESTART=0
DRY_RUN=0
MIRROR_TMP=0
DEPLOY_FRONTEND=1
DEPLOY_BACKEND=1

SSH_HOST="${KB_DEPLOY_HOST:-124.221.214.211}"
SSH_USER="${KB_DEPLOY_USER:-ubuntu}"
SSH_PORT="${KB_DEPLOY_PORT:-22}"
REMOTE_BASE="${KB_DEPLOY_REMOTE_BASE:-/home/ubuntu/repos/knowledge-box}"

usage() {
  cat <<USAGE
Usage: deploy/deploy-remote-flat.sh [options]

Options:
  --profile <name>         Frontend build profile, default: production
  --frontend-only          Deploy frontend dist only
  --backend-only           Deploy backend jar/config/tmp only
  --skip-build             Reuse existing local jar/dist
  --skip-restart           Upload files only, do not restart remote backend
  --host <host>            SSH host, default: ${SSH_HOST}
  --user <user>            SSH user, default: ${SSH_USER}
  --port <port>            SSH port, default: ${SSH_PORT}
  --remote-base <path>     Remote base dir, default: ${REMOTE_BASE}
  --dry-run                Print planned ssh/rsync commands without executing
  --mirror-tmp             Use rsync --delete for remote tmp/yuque-batch
  -h, --help               Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile)
      PROFILE="$2"
      shift 2
      ;;
    --frontend-only)
      DEPLOY_FRONTEND=1
      DEPLOY_BACKEND=0
      shift
      ;;
    --backend-only)
      DEPLOY_FRONTEND=0
      DEPLOY_BACKEND=1
      shift
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --skip-restart)
      SKIP_RESTART=1
      shift
      ;;
    --host)
      SSH_HOST="$2"
      shift 2
      ;;
    --user)
      SSH_USER="$2"
      shift 2
      ;;
    --port)
      SSH_PORT="$2"
      shift 2
      ;;
    --remote-base)
      REMOTE_BASE="$2"
      shift 2
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --mirror-tmp)
      MIRROR_TMP=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[deploy-remote-flat] Unknown option: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "[deploy-remote-flat] Missing required command: $1" >&2
    exit 1
  fi
}

require_command ssh
require_command rsync
if [[ ${SKIP_BUILD} -eq 0 ]]; then
  if [[ ${DEPLOY_BACKEND} -eq 1 ]]; then
    require_command mvn
  fi
  if [[ ${DEPLOY_FRONTEND} -eq 1 ]]; then
    require_command npm
  fi
fi

BACKEND_JAR=$(find "${ROOT_DIR}/backend/backend-app/target" -maxdepth 1 -type f -name 'knowledge-box-backend-app-*.jar' ! -name '*.original' | sort | tail -n 1 || true)

if [[ ${SKIP_BUILD} -eq 0 && ${DEPLOY_BACKEND} -eq 1 ]]; then
  echo "[deploy-remote-flat] Building backend jar..."
  mvn -q -pl backend/backend-app -am -DskipTests package -f "${ROOT_DIR}/pom.xml"
  BACKEND_JAR=$(find "${ROOT_DIR}/backend/backend-app/target" -maxdepth 1 -type f -name 'knowledge-box-backend-app-*.jar' ! -name '*.original' | sort | tail -n 1 || true)
fi

if [[ ${SKIP_BUILD} -eq 0 && ${DEPLOY_FRONTEND} -eq 1 ]]; then
  echo "[deploy-remote-flat] Building frontend dist with profile=${PROFILE}..."
  npm --prefix "${ROOT_DIR}/frontend" run build -- --profile "${PROFILE}"
fi

if [[ ${DEPLOY_BACKEND} -eq 1 && ( -z "${BACKEND_JAR}" || ! -f "${BACKEND_JAR}" ) ]]; then
  echo "[deploy-remote-flat] Backend jar not found. Run without --skip-build or build backend first." >&2
  exit 1
fi

if [[ ${DEPLOY_FRONTEND} -eq 1 && ! -d "${ROOT_DIR}/frontend/dist" ]]; then
  echo "[deploy-remote-flat] frontend/dist not found. Run without --skip-build or build frontend first." >&2
  exit 1
fi

if [[ ${DEPLOY_BACKEND} -eq 1 && ! -d "${ROOT_DIR}/tmp/yuque-batch" ]]; then
  echo "[deploy-remote-flat] tmp/yuque-batch not found. Bootstrap import data would be missing on remote." >&2
  exit 1
fi

SSH_TARGET="${SSH_USER}@${SSH_HOST}"
SSH_OPTS=(-p "${SSH_PORT}")
RSYNC_RSH="ssh -p ${SSH_PORT}"
RSYNC_OPTS=(-az --compress --human-readable)
if [[ ${DRY_RUN} -eq 1 ]]; then
  RSYNC_OPTS+=(--dry-run --itemize-changes)
fi

REMOTE_DIST_DIR="${REMOTE_BASE}/dist"
REMOTE_TMP_DIR="${REMOTE_BASE}/tmp"
REMOTE_BOOTSTRAP_DIR="${REMOTE_BASE}/backend/bootstrap"
REMOTE_DEPLOY_BIN_DIR="${REMOTE_BASE}/deploy/bin"
REMOTE_CONFIG_DIR="${REMOTE_BASE}/config"
REMOTE_LOG_DIR="${REMOTE_BASE}/logs"
REMOTE_UPLOADS_DIR="${REMOTE_BASE}/uploads"
LOCAL_CONFIG_DIR="${ROOT_DIR}/config"
LOCAL_APP_PROD_CONFIG="${LOCAL_CONFIG_DIR}/application-prod.yml"
LOCAL_ENV_CONFIG="${LOCAL_CONFIG_DIR}/knowledge-box.env"

print_cmd() {
  printf '[dry-run] %q' "$1"
  shift
  for arg in "$@"; do
    printf ' %q' "$arg"
  done
  printf '\n'
}

if [[ ${DRY_RUN} -eq 1 ]]; then
  echo "[deploy-remote-flat] Dry run mode. Commands below are planned only and will not reach the remote host."
  REMOTE_MKDIRS=("'${REMOTE_BASE}'")
  if [[ ${DEPLOY_FRONTEND} -eq 1 ]]; then
    REMOTE_MKDIRS+=("'${REMOTE_DIST_DIR}'")
  fi
  if [[ ${DEPLOY_BACKEND} -eq 1 ]]; then
    REMOTE_MKDIRS+=("'${REMOTE_TMP_DIR}'" "'${REMOTE_BOOTSTRAP_DIR}'" "'${REMOTE_DEPLOY_BIN_DIR}'" "'${REMOTE_CONFIG_DIR}'" "'${REMOTE_LOG_DIR}'" "'${REMOTE_UPLOADS_DIR}'")
  fi
  print_cmd ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "mkdir -p ${REMOTE_MKDIRS[*]}"
  if [[ ${DEPLOY_FRONTEND} -eq 1 ]]; then
    print_cmd rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
      --delete "${ROOT_DIR}/frontend/dist/" "${SSH_TARGET}:${REMOTE_DIST_DIR}/"
  fi
  if [[ ${DEPLOY_BACKEND} -eq 1 ]]; then
    print_cmd rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
      "${BACKEND_JAR}" "${SSH_TARGET}:${REMOTE_BASE}/knowledge-box-backend.jar"
    TMP_PREVIEW_OPTS=("${RSYNC_OPTS[@]}")
    if [[ ${MIRROR_TMP} -eq 1 ]]; then
      TMP_PREVIEW_OPTS+=(--delete)
    fi
    print_cmd rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
      "${ROOT_DIR}/deploy/bin/start-backend-flat.sh" \
      "${ROOT_DIR}/deploy/bin/stop-backend-flat.sh" \
      "${SSH_TARGET}:${REMOTE_DEPLOY_BIN_DIR}/"
    print_cmd rsync "${TMP_PREVIEW_OPTS[@]}" -e "${RSYNC_RSH}" \
      "${ROOT_DIR}/tmp/yuque-batch/" "${SSH_TARGET}:${REMOTE_TMP_DIR}/yuque-batch/"
    print_cmd rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
      "${ROOT_DIR}/backend/bootstrap/" "${SSH_TARGET}:${REMOTE_BOOTSTRAP_DIR}/"
    if [[ -f "${LOCAL_APP_PROD_CONFIG}" ]]; then
      print_cmd rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
        "${LOCAL_APP_PROD_CONFIG}" \
        "${SSH_TARGET}:${REMOTE_CONFIG_DIR}/application-prod.yml"
    else
      print_cmd rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
        "${ROOT_DIR}/deploy/templates/application-prod.yml" \
        "${SSH_TARGET}:${REMOTE_CONFIG_DIR}/application-prod.yml.example"
    fi
    if [[ -f "${LOCAL_ENV_CONFIG}" ]]; then
      print_cmd rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
        "${LOCAL_ENV_CONFIG}" \
        "${SSH_TARGET}:${REMOTE_CONFIG_DIR}/knowledge-box.env"
    else
      print_cmd rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
        "${ROOT_DIR}/deploy/templates/knowledge-box.env.example" \
        "${SSH_TARGET}:${REMOTE_CONFIG_DIR}/knowledge-box.env.example"
    fi
  fi
  if [[ ${DEPLOY_BACKEND} -eq 1 && ${SKIP_RESTART} -eq 0 ]]; then
    print_cmd ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "bash -s <<'EOF' ... EOF"
  fi
  exit 0
fi

echo "[deploy-remote-flat] Ensuring remote directories..."
REMOTE_MKDIRS=("'${REMOTE_BASE}'")
if [[ ${DEPLOY_FRONTEND} -eq 1 ]]; then
  REMOTE_MKDIRS+=("'${REMOTE_DIST_DIR}'")
fi
if [[ ${DEPLOY_BACKEND} -eq 1 ]]; then
  REMOTE_MKDIRS+=("'${REMOTE_TMP_DIR}'" "'${REMOTE_BOOTSTRAP_DIR}'" "'${REMOTE_DEPLOY_BIN_DIR}'" "'${REMOTE_CONFIG_DIR}'" "'${REMOTE_LOG_DIR}'" "'${REMOTE_UPLOADS_DIR}'")
fi
ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "mkdir -p ${REMOTE_MKDIRS[*]}"

if [[ ${DEPLOY_FRONTEND} -eq 1 ]]; then
  echo "[deploy-remote-flat] Syncing frontend dist -> ${SSH_TARGET}:${REMOTE_DIST_DIR}"
  rsync "${RSYNC_OPTS[@]}" --delete -e "${RSYNC_RSH}" \
    "${ROOT_DIR}/frontend/dist/" "${SSH_TARGET}:${REMOTE_DIST_DIR}/"
fi

if [[ ${DEPLOY_BACKEND} -eq 1 ]]; then
  echo "[deploy-remote-flat] Syncing backend jar -> ${SSH_TARGET}:${REMOTE_BASE}/knowledge-box-backend.jar"
  rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
    "${BACKEND_JAR}" "${SSH_TARGET}:${REMOTE_BASE}/knowledge-box-backend.jar"

  echo "[deploy-remote-flat] Syncing tmp/yuque-batch -> ${SSH_TARGET}:${REMOTE_TMP_DIR}/yuque-batch"
  TMP_RSYNC_OPTS=("${RSYNC_OPTS[@]}")
  if [[ ${MIRROR_TMP} -eq 1 ]]; then
    TMP_RSYNC_OPTS+=(--delete)
  fi
  rsync "${TMP_RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
    "${ROOT_DIR}/tmp/yuque-batch/" "${SSH_TARGET}:${REMOTE_TMP_DIR}/yuque-batch/"

  echo "[deploy-remote-flat] Syncing backend/bootstrap -> ${SSH_TARGET}:${REMOTE_BOOTSTRAP_DIR}"
  rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
    "${ROOT_DIR}/backend/bootstrap/" "${SSH_TARGET}:${REMOTE_BOOTSTRAP_DIR}/"

  echo "[deploy-remote-flat] Syncing flat deploy scripts..."
  rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
    "${ROOT_DIR}/deploy/bin/start-backend-flat.sh" \
    "${ROOT_DIR}/deploy/bin/stop-backend-flat.sh" \
    "${SSH_TARGET}:${REMOTE_DEPLOY_BIN_DIR}/"

  echo "[deploy-remote-flat] Syncing config files..."
  if [[ -f "${LOCAL_APP_PROD_CONFIG}" ]]; then
    rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
      "${LOCAL_APP_PROD_CONFIG}" \
      "${SSH_TARGET}:${REMOTE_CONFIG_DIR}/application-prod.yml"
  else
    rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
      "${ROOT_DIR}/deploy/templates/application-prod.yml" \
      "${SSH_TARGET}:${REMOTE_CONFIG_DIR}/application-prod.yml.example"
  fi

  if [[ -f "${LOCAL_ENV_CONFIG}" ]]; then
    rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
      "${LOCAL_ENV_CONFIG}" \
      "${SSH_TARGET}:${REMOTE_CONFIG_DIR}/knowledge-box.env"
  else
    rsync "${RSYNC_OPTS[@]}" -e "${RSYNC_RSH}" \
      "${ROOT_DIR}/deploy/templates/knowledge-box.env.example" \
      "${SSH_TARGET}:${REMOTE_CONFIG_DIR}/knowledge-box.env.example"
  fi
fi

if [[ ${DEPLOY_BACKEND} -eq 0 ]]; then
  echo "[deploy-remote-flat] Frontend-only deploy completed. Backend restart skipped."
  exit 0
fi

if [[ ${SKIP_RESTART} -eq 1 || ${DRY_RUN} -eq 1 ]]; then
  echo "[deploy-remote-flat] Upload complete. Restart skipped."
  exit 0
fi

echo "[deploy-remote-flat] Restarting remote backend..."
ssh "${SSH_OPTS[@]}" "${SSH_TARGET}" "bash -s" <<EOF
set -euo pipefail
cd '${REMOTE_BASE}'
chmod +x deploy/bin/start-backend-flat.sh deploy/bin/stop-backend-flat.sh
if [[ ! -f config/application-prod.yml && -f config/application-prod.yml.example ]]; then
  cp config/application-prod.yml.example config/application-prod.yml
fi
if [[ ! -f config/knowledge-box.env ]]; then
  if [[ -f config/knowledge-box.env.example ]]; then
    cp config/knowledge-box.env.example config/knowledge-box.env
  fi
  echo '[deploy-remote-flat] Missing config/knowledge-box.env on remote. Initialized it from config/knowledge-box.env.example. Fill DB_PASSWORD and other secret fields first, then rerun deploy.' >&2
  exit 1
fi
deploy/bin/stop-backend-flat.sh || true
deploy/bin/start-backend-flat.sh --daemon
EOF

echo "[deploy-remote-flat] Remote deploy completed."
echo "[deploy-remote-flat] Frontend dist: ${REMOTE_DIST_DIR}"
echo "[deploy-remote-flat] Backend jar:   ${REMOTE_BASE}/knowledge-box-backend.jar"
echo "[deploy-remote-flat] Bootstrap dir: ${REMOTE_TMP_DIR}/yuque-batch/bootstrap-seeds"
