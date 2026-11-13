#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
case "${CU10_PROJECT:?}" in cu10-external-*) ;; *) exit 2;; esac
: "${CU10_RUN_DIR:?}"
export CU10_BACKEND_CONTEXT="$CU10_RUN_DIR/app/backend/demo"
export CU10_FRONTEND_CONTEXT="$CU10_RUN_DIR/app/frontend"
exec docker compose --env-file /dev/null -p "$CU10_PROJECT" -f "$root/scripts/cu10-environment.yaml" "$@"
