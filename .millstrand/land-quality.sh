#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
cd "$repo_root"

echo "== Agent Harness local landing quality =="
git diff --check
make quality
make identity-check
echo "release-check is owned by the coordinated cutover gate"
echo "local landing quality: clean"
