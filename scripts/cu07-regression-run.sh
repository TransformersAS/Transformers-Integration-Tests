#!/usr/bin/env bash
# Run every external suite against the CU07 revision without masking either exit status.
set -euo pipefail
cd "$(dirname "$0")/.."
export CU07_PROJECT="${CU07_PROJECT:-cu07-external-regression-$$}"
export CU07_ISOLATED=true
export BACKEND_BASE_URL=http://127.0.0.1:18087
export FRONTEND_BASE_URL="http://127.0.0.1:${CU08_E2E_PORT:-14300}"
export CU07_MAIL_URL="http://127.0.0.1:${CU07_MAIL_PORT:-18025}"
export LOGISTICS_WEBHOOK_SECRET=cu07-disposable-logistics-only
cleanup() { bash scripts/cu07-compose.sh down --volumes --remove-orphans; }
trap cleanup EXIT
bash scripts/cu07-compose.sh -f "$PWD/scripts/cu07-regression.yaml" up -d --build --wait --wait-timeout 300
bash scripts/cu07-seed-regression.sh
mkdir -p target/cu07-regression
if ./mvnw verify; then http_status=0; else http_status=$?; fi
# These suites share the fictitious demo cart. Reset it between runners, in this disposable schema only.
schema="$(bash scripts/cu07-compose.sh exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u "$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT DATABASE()"')"
[[ "$schema" == cu08_e2e ]]
bash scripts/cu07-compose.sh exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"' <<'SQL'
DELETE i FROM cart_items i JOIN carts c ON c.id=i.cart_id
JOIN user_accounts a ON a.id=c.account_id WHERE a.email='demo@marketplace.local';
SQL
if npx playwright test; then e2e_status=0; else e2e_status=$?; fi
printf '{"http_exit_code":%s,"e2e_exit_code":%s}\n' "$http_status" "$e2e_status" > target/cu07-regression/status.json
if (( http_status != 0 || e2e_status != 0 )); then exit 1; fi
