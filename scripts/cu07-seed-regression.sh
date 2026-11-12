#!/usr/bin/env bash
# Public fixtures, restricted to the disposable CU07 project and schema.
set -euo pipefail
cd "$(dirname "$0")/.."
app="${CU07_APP_DIR:-$PWD/../Transformers-AS}"
mysql_container="$(bash scripts/cu07-compose.sh ps -q mysql)"
schema="$(bash scripts/cu07-compose.sh exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -N -B -u "$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT DATABASE()"')"
[[ "$schema" == cu08_e2e ]]
DEMO_PASSWORD='MarketplaceDemo123!' DB_PASSWORD=cu08_test_only DB_USER=cu08_test \
  DB_NAME=cu08_e2e MYSQL_CONTAINER="$mysql_container" bash "$app/scripts/cu20-demo-seed.sh"
bash scripts/cu07-compose.sh exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --default-character-set=utf8mb4 -u "$MYSQL_USER" "$MYSQL_DATABASE"' <<'SQL'
UPDATE user_accounts SET email_verified_at=COALESCE(email_verified_at,CURRENT_TIMESTAMP(6))
WHERE email IN ('demo@marketplace.local','soporte.demo@example.com','comprador.demo@example.com','vendedor.demo@example.com','vecino.demo@example.com');
INSERT INTO categories(name,active) SELECT 'Hogar',TRUE FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name='Hogar');
-- The public CU20 seed assumes store 2 is free. Avoid that assumption on repeated runs.
INSERT INTO stores(name,owner_account_id)
SELECT 'Tienda vecina fixture CU07',a.id FROM user_accounts a
WHERE a.email='vecino.demo@example.com' AND NOT EXISTS (SELECT 1 FROM stores s WHERE s.owner_account_id=a.id);
SQL
