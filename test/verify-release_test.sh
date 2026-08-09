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

for required in \
  'candidate_coord/spools.edn' \
  'codethread/devflow-kanban-adapter' \
  'local candidate root' \
  'dissoc :git/tag' \
  'init.local.clj' \
  'build_core_binary' \
  'go build' \
  'core_root/bin/$name' \
  'MILL_BIN:-' \
  'STRAND_BIN:-'; do
  if ! grep -Fq "$required" "$verify_release"; then
    printf 'verify-release projection probe failed; missing %s\n' "$required" >&2
    exit 1
  fi
done

if grep -Fq 'cat >"$weaver_workspace/spools.edn"' "$verify_release"; then
  echo "verify-release projection probe failed; workspace spools are still hard-coded" >&2
  exit 1
fi

if grep -Fq 'source_root/../' "$verify_release" || \
   grep -Fq 'command -v mill' "$verify_release" || \
   grep -Fq 'command -v strand' "$verify_release"; then
  echo "verify-release binary probe failed; fallback binaries are not pinned-core builds" >&2
  exit 1
fi

echo "verify-release candidate workspace projection: OK"
