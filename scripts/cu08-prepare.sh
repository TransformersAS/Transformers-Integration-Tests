#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export CU08_E2E_PORT="${CU08_E2E_PORT:-14308}"
export CU08_MAIL_PORT="${CU08_MAIL_PORT:-18028}"
export CU08_GUARD_ENABLED=false
unset CU08_OBSERVED_ORIGIN
bash scripts/cu08-compose.sh up -d --build --wait --wait-timeout 300
curl --fail --silent "http://127.0.0.1:$CU08_E2E_PORT/api/auth/csrf" > /dev/null
origin_log="$(bash scripts/cu08-compose.sh exec -T backend cat /tmp/cu08-origin/origin.log)"
export CU08_OBSERVED_ORIGIN="$(printf '%s\n' "$origin_log" | sed -n 's/^origin=\([^ ]*\) forwarded=true status=200$/\1/p' | tail -1)"
python3 -c 'import ipaddress,os; ip=ipaddress.ip_address(os.environ["CU08_OBSERVED_ORIGIN"]); assert ip.is_private and not ip.is_unspecified and not ip.is_multicast'
mkdir -p target/cu08
printf 'CU08_OBSERVED_ORIGIN=%s\n' "$CU08_OBSERVED_ORIGIN" > target/cu08/origin.env
printf '%s\n' "$origin_log" > target/cu08/origin-observation.log
export CU08_GUARD_ENABLED=true
bash scripts/cu08-compose.sh up -d --wait --wait-timeout 180
schema="$(bash scripts/cu08-compose.sh exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u "$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT DATABASE()"')"
[[ "$schema" == cu08_e2e ]]
bash scripts/cu08-compose.sh exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"' <<'SQL'
UPDATE user_accounts SET email_verified_at=COALESCE(email_verified_at,CURRENT_TIMESTAMP(6)) WHERE email='demo@marketplace.local';
SQL
