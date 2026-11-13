#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export CU10_PROJECT="${CU10_PROJECT:-cu10-external-$(date -u +%Y%m%d%H%M%S)-$$}"
export CU10_RUN_DIR="${CU10_RUN_DIR:-$PWD/target/cu10/$CU10_PROJECT}"
test ! -e "$CU10_RUN_DIR" || { echo 'La evidencia ya existe'; exit 2; }
mkdir -p "$CU10_RUN_DIR"
export CU10_ISOLATED=true
export CU10_DOCKERFILE="$PWD/scripts/cu10-backend.Dockerfile"
export BACKEND_BASE_URL="http://127.0.0.1:${CU10_PORT:-14310}"
export FRONTEND_BASE_URL="$BACKEND_BASE_URL"
cleanup() {
  local result="$1"
  trap - EXIT INT TERM
  if test ! -f "$CU10_RUN_DIR/status.json"; then result=1; else
    python3 -c 'import json,sys; r=json.load(open(sys.argv[1])); assert r["http_exit_code"] == r["e2e_exit_code"] == 0' "$CU10_RUN_DIR/status.json" 2> "$CU10_RUN_DIR/result-check.log" || result=1
  fi
  if test ! -f "$CU10_RUN_DIR/summary.json"; then python3 scripts/cu10-results.py > "$CU10_RUN_DIR/summary-generation.log" 2>&1 || result=1; fi
  bash scripts/cu10-compose.sh logs --no-color > "$CU10_RUN_DIR/containers.log" 2>&1 || result=1
  bash scripts/cu10-compose.sh down --volumes --remove-orphans > "$CU10_RUN_DIR/cleanup.log" 2>&1 || result=1
  for kind in container volume network; do
    if test "$kind" = container; then
      docker container ls -a --filter "label=com.docker.compose.project=$CU10_PROJECT" --format '{{.ID}}' > "$CU10_RUN_DIR/remaining-$kind.txt" 2> "$CU10_RUN_DIR/cleanup-$kind.log" || result=1
    else
      docker "$kind" ls --filter "label=com.docker.compose.project=$CU10_PROJECT" --format '{{.ID}}' > "$CU10_RUN_DIR/remaining-$kind.txt" 2> "$CU10_RUN_DIR/cleanup-$kind.log" || result=1
    fi
    test ! -s "$CU10_RUN_DIR/remaining-$kind.txt" || result=1
  done
  printf '%s\n' "$result" > "$CU10_RUN_DIR/exit-code.txt"
  exit "$result"
}
# La descarga no crea recursos Docker.
bash scripts/cu10-source.sh
{ java -version; node --version; docker version --format '{{.Server.Version}}'; } > "$CU10_RUN_DIR/toolchain.log" 2>&1
shasum -a 256 scripts/cu10-* backend-tests/cu10/pom.xml backend-tests/cu10/src/test/java/cu10/*.java playwright.cu10.config.ts tests/cu10-e2e/*.ts tests/cu10-e2e/assets/* > "$CU10_RUN_DIR/harness.sha256"
trap 'cleanup "$?"' EXIT INT TERM
bash scripts/cu10-compose.sh config > "$CU10_RUN_DIR/compose.yaml"
bash scripts/cu10-compose.sh up -d --build --wait --wait-timeout 420 > "$CU10_RUN_DIR/startup.log" 2>&1
python3 scripts/cu10-fixture.py sql 'SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank;' > "$CU10_RUN_DIR/migrations.log"
python3 scripts/cu10-fixture.py sql "SELECT COUNT(*) FROM flyway_schema_history WHERE version='47' AND success=1;" | python3 -c 'import sys; assert sys.stdin.read().strip()=="1"'
python3 scripts/cu10-fixture.py > "$CU10_RUN_DIR/fixture.json"
if ./mvnw -B -f backend-tests/cu10/pom.xml test > "$CU10_RUN_DIR/http.log" 2>&1; then http=0; else http=$?; fi
if npx playwright test --config=playwright.cu10.config.ts > "$CU10_RUN_DIR/e2e.log" 2>&1; then e2e=0; else e2e=$?; fi
printf '{"http_exit_code":%s,"e2e_exit_code":%s}\n' "$http" "$e2e" > "$CU10_RUN_DIR/status.json"
python3 scripts/cu10-results.py
(( http == 0 && e2e == 0 ))
