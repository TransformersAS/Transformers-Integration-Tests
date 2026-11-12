#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export CU07_PROJECT="cu07-external-${CU07_RUN_ID:-$$}"
export CU07_ISOLATED=true
export BACKEND_BASE_URL="http://127.0.0.1:${CU08_E2E_PORT:-14300}"
export FRONTEND_BASE_URL="$BACKEND_BASE_URL"
export CU07_MAIL_URL="http://127.0.0.1:${CU07_MAIL_PORT:-18025}"
cleanup() { bash scripts/cu07-compose.sh down --volumes --remove-orphans; }
trap cleanup EXIT
bash scripts/cu07-compose.sh up -d --build --wait --wait-timeout 300
./mvnw -Dtest=BuyerRegistrationIntegrationTest verify
npx playwright test tests/e2e/buyer-registration.spec.ts
