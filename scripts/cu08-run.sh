#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export CU08_PROJECT="${CU08_PROJECT:-cu08-external-$$}"
export CU08_E2E_PORT="${CU08_E2E_PORT:-14308}"
export CU08_MAIL_PORT="${CU08_MAIL_PORT:-18028}"
export BACKEND_BASE_URL="http://127.0.0.1:$CU08_E2E_PORT"
export FRONTEND_BASE_URL="$BACKEND_BASE_URL"
export CU08_MAIL_URL="http://127.0.0.1:$CU08_MAIL_PORT"
export CU08_ISOLATED=true
cleanup() { bash scripts/cu08-compose.sh down --volumes --remove-orphans; }
trap cleanup EXIT
bash scripts/cu08-prepare.sh
set -a
source target/cu08/origin.env
set +a
export CU08_GUARD_ENABLED=true
mkdir -p target/cu08
if ./mvnw -Dtest=AccountSecurityIntegrationTest verify; then http_status=0; else http_status=$?; fi
if npx playwright test tests/e2e/account-security.spec.ts tests/e2e/cu08-access.spec.ts; then e2e_status=0; else e2e_status=$?; fi
printf '{"http_exit_code":%s,"e2e_exit_code":%s}\n' "$http_status" "$e2e_status" > target/cu08/status.json
if (( http_status != 0 || e2e_status != 0 )); then exit 1; fi
