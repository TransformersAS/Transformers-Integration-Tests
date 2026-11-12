#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
project="${CU09_PROJECT:?Indica el proyecto desechable CU09}"
case "$project" in cu09-external-*) ;; *) exit 2;; esac
app="$root/target/cu09/app"
export CU08_E2E_PORT="${CU09_E2E_PORT:-14309}"
exec docker compose --env-file /dev/null -p "$project" -f "$app/compose.e2e.yaml" -f "$app/compose.cu09-e2e.yaml" -f "$root/scripts/cu09-environment.yaml" "$@"
