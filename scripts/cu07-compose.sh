#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
app="${CU07_APP_DIR:-$root/../Transformers-AS}"
project="${CU07_PROJECT:-cu07-external-sofia}"
case "$project" in cu07-external-*) ;; *) echo 'CU07 requires its dedicated disposable project prefix' >&2; exit 2;; esac
# Dedicated project; never attaches to a persistent database or named application volume.
exec docker compose --env-file /dev/null -p "$project" -f "$app/compose.e2e.yaml" -f "$app/compose.cu07-e2e.yaml" "$@"
