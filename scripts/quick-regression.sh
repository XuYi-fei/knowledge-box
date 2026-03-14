#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

echo "[1/4] backend compile (multi-module)"
mvn -q -pl backend/backend-app -am -DskipTests compile

echo "[2/4] backend focused regression tests"
mvn -q -pl backend/backend-app -am -Dtest=AgentCapabilityAssemblyServiceTests,AgentProfileBindingServiceTests -Dsurefire.failIfNoSpecifiedTests=false test

echo "[3/4] frontend build"
npm --prefix frontend run build

echo "[4/4] done"
echo "Quick regression passed."
