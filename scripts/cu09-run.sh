#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export CU09_PROJECT="${CU09_PROJECT:-cu09-external-$$}"
export CU09_ISOLATED=true
export CU09_E2E_PORT="${CU09_E2E_PORT:-14309}"
export CU09_MAIL_PORT="${CU09_MAIL_PORT:-18039}"
export BACKEND_BASE_URL="http://127.0.0.1:$CU09_E2E_PORT"
export FRONTEND_BASE_URL="$BACKEND_BASE_URL"
export CU09_MAIL_URL="http://127.0.0.1:$CU09_MAIL_PORT"
mkdir -p target/cu09
cleanup() {
  local result="$1"
  trap - EXIT
  bash scripts/cu09-compose.sh logs --no-color > target/cu09/containers.log 2>&1 || true
  if bash scripts/cu09-compose.sh down --volumes --remove-orphans > target/cu09/cleanup.log 2>&1; then
    docker ps -a --filter "label=com.docker.compose.project=$CU09_PROJECT" --format '{{.Names}}' > target/cu09/remaining-containers.txt
    docker volume ls --filter "label=com.docker.compose.project=$CU09_PROJECT" --format '{{.Name}}' > target/cu09/remaining-volumes.txt
    docker network ls --filter "label=com.docker.compose.project=$CU09_PROJECT" --format '{{.Name}}' > target/cu09/remaining-networks.txt
    for resource in containers volumes networks; do test ! -s "target/cu09/remaining-$resource.txt" || result=1; done
  else result=1; fi
  exit "$result"
}
trap 'cleanup "$?"' EXIT
bash scripts/cu09-source.sh
bash scripts/cu09-compose.sh config --quiet
bash scripts/cu09-compose.sh up -d --build --wait --wait-timeout 300 > target/cu09/startup.log 2>&1
python3 scripts/cu09-fixture.py sql 'SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank;' > target/cu09/migrations.log
if ./mvnw -f backend-tests/cu09/pom.xml -Dtest=PersonalProfileExternalTest test > target/cu09/http.log 2>&1; then http=0; else http=$?; fi
if npx playwright test --config=playwright.cu09.config.ts > target/cu09/e2e.log 2>&1; then e2e=0; else e2e=$?; fi
printf '{"http_exit_code":%s,"e2e_exit_code":%s}\n' "$http" "$e2e" > target/cu09/status.json
(( http == 0 && e2e == 0 ))
