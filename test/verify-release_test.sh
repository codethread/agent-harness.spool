#!/usr/bin/env bash
set -euo pipefail

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
verify_release="$repo_root/bin/verify-release"

for option in --mode --source-root --repository --tag --sha --core-release --kanban-release --help; do
  set +e
  if [[ "$option" == "--help" ]]; then
    output=$("$verify_release" --help --help 2>&1)
  else
    output=$("$verify_release" "$option" first "$option" second 2>&1)
  fi
  status=$?
  set -e
  if [[ "$status" -ne 1 || "$output" != *"duplicate option: $option"* ]]; then
    printf 'verify-release duplicate probe failed for %s (status %s):\n%s\n' \
      "$option" "$status" "$output" >&2
    exit 1
  fi
done

echo "verify-release duplicate-option probes: OK"
