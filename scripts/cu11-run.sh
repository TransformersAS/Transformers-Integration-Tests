#!/usr/bin/env bash
# Only CU11. No Git operations, production credentials, legacy payment switch or shared database.
set -euo pipefail
cd "$(dirname "$0")/.."
sha=dd72119d55b36684474d1b9d07874ec19cb7ce11
mkdir -p target/cu11
if [[ -z "${CU11_APP_DIR:-}" ]]; then
  app="$PWD/target/cu11/app-$sha"
  if [[ ! -d "$app" ]]; then
    curl --fail --location --silent --show-error --max-time 180 "https://codeload.github.com/TransformersAS/Transformers-AS/tar.gz/$sha" -o target/cu11/app.tar.gz
    mkdir -p "$app"
    tar -xzf target/cu11/app.tar.gz --strip-components=1 -C "$app"
  fi
  export CU11_APP_DIR="$app"
fi
export CU11_PROJECT="${CU11_PROJECT:-cu11-external-$(date +%s)-$$}"
export CU11_PORT="${CU11_PORT:-14311}"
export CU11_ISOLATED=true CU11_REFUND_MODE=OK
export BACKEND_BASE_URL="http://127.0.0.1:$CU11_PORT" FRONTEND_BASE_URL="http://127.0.0.1:$CU11_PORT"
export E2E_EMAIL=cu11-ui@example.test E2E_PASSWORD='Cu11ExternalPassword123!'
cleanup() {
  result=$?
  trap - EXIT
  cleanup_status=0
  bash scripts/cu11-compose.sh down --volumes --remove-orphans > target/cu11/cleanup.log 2>&1 || cleanup_status=$?
  remaining="$(docker ps -aq --filter "label=com.docker.compose.project=$CU11_PROJECT")"
  networks="$(docker network ls -q --filter "label=com.docker.compose.project=$CU11_PROJECT")"
  if [[ -n "$remaining" || -n "$networks" ]]; then cleanup_status=1; fi
  printf '{"exit_code":%s,"resources_removed":%s}\n' "$cleanup_status" "$([[ $cleanup_status == 0 ]] && echo true || echo false)" > target/cu11/cleanup.json
  if (( cleanup_status != 0 )); then result=1; fi
  exit "$result"
}
trap cleanup EXIT
bash scripts/cu11-compose.sh config --quiet
python3 - "$sha" <<'PY'
import hashlib,json,os,sys
from pathlib import Path
app=Path(os.environ['CU11_APP_DIR'])
files=['backend/demo/src/main/java/com/transformersas/marketplace/orders/infrastructure/web/controller/OrderController.java',
       'backend/demo/src/main/java/com/transformersas/marketplace/orders/application/usecase/CancelOrderUseCase.java',
       'frontend/src/app/pedidos/components/mis-pedidos.component.html']
Path('target/cu11/provenance.json').write_text(json.dumps({'expected_application_revision':sys.argv[1],'source_files_sha256':{p:hashlib.sha256((app/p).read_bytes()).hexdigest() for p in files}},indent=2))
PY
bash scripts/cu11-compose.sh up -d --build --wait --wait-timeout 300 > target/cu11/startup.log 2>&1
failures=0
run_http() {
  local suite="$1" name="com.transformersas.integration.orders.$1"
  local output="target/cu11/http-$CU11_REFUND_MODE"
  mkdir -p "$output"
  rm -f "target/surefire-reports/TEST-$name.xml" "target/surefire-reports/$name.txt"
  if ./mvnw -B -Dtest="$suite" verify > "$output/command.log" 2>&1; then status=0; else status=$?; failures=1; fi
  for file in "target/surefire-reports/TEST-$name.xml" "target/surefire-reports/$name.txt"; do
    if [[ -f "$file" ]]; then cp "$file" "$output/"; fi
  done
  printf '%s\n' "$status" > "$output/exit-code"
  echo "CU11 HTTP ($CU11_REFUND_MODE) exit=$status"
}
run_http BuyerOrdersIntegrationTest
export PLAYWRIGHT_JSON_OUTPUT_NAME="$PWD/target/cu11/e2e.json"
if npx playwright test tests/e2e/checkout-order-cancellation.spec.ts --reporter=line,json > target/cu11/e2e.log 2>&1; then e2e_status=0; else e2e_status=$?; failures=1; fi
echo "CU11 E2E exit=$e2e_status"
for mode in PENDING UNAVAILABLE; do
  export CU11_REFUND_MODE="$mode"
  if bash scripts/cu11-compose.sh up -d --no-deps --wait --wait-timeout 180 backend > "target/cu11/restart-$mode.log" 2>&1; then
    run_http BuyerOrderRefundIntegrationTest
  else
    failures=1
    echo "CU11 infrastructure failure in $mode"
  fi
done
python3 scripts/cu11-results.py
exit "$failures"
