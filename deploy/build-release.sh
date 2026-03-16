#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
RELEASE_NAME="knowledge-box-release-${TIMESTAMP}"
OUTPUT_BASE="${ROOT_DIR}/dist/releases"
PROFILE="production"
SKIP_BUILD=0
KEEP_DIR=0

usage() {
  cat <<USAGE
Usage: deploy/build-release.sh [options]

Options:
  --profile <name>       Frontend build profile, default: production
  --output-dir <path>    Release output base directory, default: dist/releases
  --skip-build           Reuse existing backend/frontend build artifacts
  --keep-dir             Keep unpacked release directory after creating tar.gz
  -h, --help             Show this help
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile)
      PROFILE="$2"
      shift 2
      ;;
    --output-dir)
      OUTPUT_BASE="$2"
      shift 2
      ;;
    --skip-build)
      SKIP_BUILD=1
      shift
      ;;
    --keep-dir)
      KEEP_DIR=1
      shift
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

BACKEND_JAR=$(find "${ROOT_DIR}/backend/backend-app/target" -maxdepth 1 -type f -name 'knowledge-box-backend-app-*.jar' ! -name '*.original' | sort | tail -n 1 || true)

if [[ ${SKIP_BUILD} -eq 0 ]]; then
  echo "[build-release] Building backend jar..."
  mvn -q -pl backend/backend-app -am -DskipTests package -f "${ROOT_DIR}/pom.xml"
  echo "[build-release] Building frontend dist with profile=${PROFILE}..."
  npm --prefix "${ROOT_DIR}/frontend" run build -- --profile "${PROFILE}"
  BACKEND_JAR=$(find "${ROOT_DIR}/backend/backend-app/target" -maxdepth 1 -type f -name 'knowledge-box-backend-app-*.jar' ! -name '*.original' | sort | tail -n 1 || true)
fi

if [[ -z "${BACKEND_JAR}" || ! -f "${BACKEND_JAR}" ]]; then
  echo "[build-release] Backend jar not found. Run without --skip-build or build backend first." >&2
  exit 1
fi

if [[ ! -d "${ROOT_DIR}/frontend/dist" ]]; then
  echo "[build-release] frontend/dist not found. Run without --skip-build or build frontend first." >&2
  exit 1
fi

mkdir -p "${OUTPUT_BASE}"
RELEASE_DIR="${OUTPUT_BASE}/${RELEASE_NAME}"
rm -rf "${RELEASE_DIR}"
mkdir -p "${RELEASE_DIR}"/{app,config,frontend,logs,deploy,tmp}

cp "${BACKEND_JAR}" "${RELEASE_DIR}/app/knowledge-box-backend.jar"
cp -R "${ROOT_DIR}/frontend/dist" "${RELEASE_DIR}/frontend/dist"
cp -R "${ROOT_DIR}/deploy/templates/." "${RELEASE_DIR}/deploy/"
cp -R "${ROOT_DIR}/deploy/bin/." "${RELEASE_DIR}/deploy/"

if [[ -d "${ROOT_DIR}/backend/uploads" ]]; then
  mkdir -p "${RELEASE_DIR}/app/uploads"
  cp -R "${ROOT_DIR}/backend/uploads/." "${RELEASE_DIR}/app/uploads/"
fi

if [[ -d "${ROOT_DIR}/tmp/yuque-batch" ]]; then
  mkdir -p "${RELEASE_DIR}/tmp/yuque-batch"
  cp -R "${ROOT_DIR}/tmp/yuque-batch/." "${RELEASE_DIR}/tmp/yuque-batch/"
fi

cat > "${RELEASE_DIR}/VERSION.txt" <<VERSION
release_name=${RELEASE_NAME}
built_at=$(date '+%Y-%m-%d %H:%M:%S %z')
frontend_profile=${PROFILE}
backend_jar=$(basename "${BACKEND_JAR}")
git_commit=$(git -C "${ROOT_DIR}" rev-parse HEAD)
VERSION

cat > "${RELEASE_DIR}/README.txt" <<README
Knowledge Box release bundle

1. Copy this directory or the tar.gz to the server.
2. Edit config/application-prod.yml and config/knowledge-box.env.
3. Ensure PostgreSQL, Redis and Java 21 are installed on the server.
4. Start with: ./deploy/start-backend.sh
5. Configure nginx with deploy/www.xuyifei.site.conf.example

Yuque bootstrap seeds are packaged under tmp/yuque-batch/bootstrap-seeds.
If config/knowledge-box.env enables bootstrap import, startup will import them automatically.
README

ARCHIVE_PATH="${OUTPUT_BASE}/${RELEASE_NAME}.tar.gz"
tar -C "${OUTPUT_BASE}" -czf "${ARCHIVE_PATH}" "${RELEASE_NAME}"

echo "[build-release] Release directory: ${RELEASE_DIR}"
echo "[build-release] Release archive:   ${ARCHIVE_PATH}"

if [[ ${KEEP_DIR} -eq 0 ]]; then
  rm -rf "${RELEASE_DIR}"
  echo "[build-release] Removed unpacked release directory (use --keep-dir to retain it)."
fi
