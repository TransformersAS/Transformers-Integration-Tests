#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
export CU08_NGINX_FILE="$root/scripts/cu08-nginx.conf"
app="${CU08_APP_DIR:-$root/../Transformers-AS}"
project="${CU08_PROJECT:?Set the disposable CU08 project}"
case "$project" in cu08-external-*) ;; *) exit 2;; esac
exec docker compose --env-file /dev/null -p "$project" -f "$app/compose.e2e.yaml" -f "$root/scripts/cu08-environment.yaml" "$@"
