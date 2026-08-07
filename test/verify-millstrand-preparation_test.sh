#!/usr/bin/env bash
set -euo pipefail

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
validator="$repo_root/bin/validate-consumer-preparation"
manifest="$repo_root/docs/operations/millstrand-cutover-preparation.json"
tmp_root=$(mktemp -d "${TMPDIR:-/tmp}/consumer-preparation-test.XXXXXX")
trap 'rm -rf "$tmp_root"' EXIT

expect_failure() {
  local label=$1
  local needle=$2
  local candidate=$3
  local output status
  set +e
  output=$($validator "$candidate" 2>&1)
  status=$?
  set -e
  if [[ "$status" -eq 0 || "$output" != *"$needle"* ]]; then
    printf 'consumer-preparation %s probe failed (status %s):\n%s\n' "$label" "$status" "$output" >&2
    exit 1
  fi
}

python3 - "$manifest" "$tmp_root/duplicate.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    document = json.load(handle)
document["release-inputs"].append(dict(document["release-inputs"][0]))
with open(sys.argv[2], "w", encoding="utf-8") as handle:
    json.dump(document, handle)
PY
expect_failure duplicate "duplicate release input card before lookup" "$tmp_root/duplicate.json"

python3 - "$manifest" "$tmp_root/unknown.json" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    document = json.load(handle)
document["release-inputs"][0]["card"] = "MSR-99"
with open(sys.argv[2], "w", encoding="utf-8") as handle:
    json.dump(document, handle)
PY
expect_failure unknown "unknown release input card before lookup" "$tmp_root/unknown.json"

echo "verify-millstrand-preparation manifest probes: OK"
