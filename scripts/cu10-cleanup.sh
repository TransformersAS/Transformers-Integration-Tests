#!/usr/bin/env bash
# Respaldo de limpieza para cancelación/fallo del proceso principal en CI.
set -euo pipefail
cd "$(dirname "$0")/.."
export CU10_RUN_DIR="${CU10_RUN_DIR:-$PWD/target/cu10/${CU10_PROJECT:?}}"
export CU10_DOCKERFILE="$PWD/scripts/cu10-backend.Dockerfile"
case "$CU10_PROJECT" in cu10-external-*) ;; *) exit 2;; esac
if test ! -d "$CU10_RUN_DIR/app/backend/demo"; then exit 0; fi
bash scripts/cu10-compose.sh down --volumes --remove-orphans > "$CU10_RUN_DIR/workflow-cleanup.log" 2>&1
for kind in container volume network; do
  if test "$kind" = container; then
    docker container ls -a --filter "label=com.docker.compose.project=$CU10_PROJECT" --format '{{.ID}}' > "$CU10_RUN_DIR/workflow-remaining-$kind.txt"
  else
    docker "$kind" ls --filter "label=com.docker.compose.project=$CU10_PROJECT" --format '{{.ID}}' > "$CU10_RUN_DIR/workflow-remaining-$kind.txt"
  fi
  test ! -s "$CU10_RUN_DIR/workflow-remaining-$kind.txt"
done
