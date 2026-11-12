#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
case "${CU11_PROJECT:?Set a disposable CU11 project}" in cu11-external-*) ;; *) echo 'Only cu11-external-* projects are allowed' >&2; exit 2;; esac
exec docker compose --env-file /dev/null -p "$CU11_PROJECT" -f "$root/scripts/cu11-environment.yaml" "$@"
