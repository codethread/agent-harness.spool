#!/usr/bin/env bash
set -euo pipefail

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
verify_release="$repo_root/bin/verify-release"

bash -n "$verify_release"
for option in --mode --source-root --repository --tag --sha --core-release --kanban-release --help; do
  set +e
  if [[ "$option" == --help ]]; then
    output=$("$verify_release" --help --help 2>&1)
  else
    output=$("$verify_release" "$option" first "$option" second 2>&1)
  fi
  status=$?
  set -e
  [[ "$status" -eq 1 && "$output" == *"duplicate option: $option"* ]] || {
    printf 'duplicate-option probe failed for %s (status %s):\n%s\n' "$option" "$status" "$output" >&2
    exit 1
  }
done

for required in \
  'refs/tags/$tag^{}' \
  'git clone --quiet --depth 1 --branch "$tag"' \
  'candidate_root="$tmp_root/candidate"' \
  'candidate workspace is missing deps.edn' \
  'candidate root Millstrand pin is not the exact release coordinate' \
  'pin is not the exact Millhouse release coordinate' \
  'mill_cmd weaver start --workspace "$workspace_root"' \
  '"$mill_bin" weaver stop --workspace "$workspace_root"'; do
  grep -Fq "$required" "$verify_release" || {
    printf 'release verifier contract probe is missing %s\n' "$required" >&2
    exit 1
  }
done
if grep -Eq 'spools\.edn|:roots|:millstrand/source-root|weaver restart|marker-proof|historical_core' "$verify_release"; then
  echo 'release verifier retains a removed manifest/family/root/restart assumption' >&2
  exit 1
fi
[[ $(grep -Fc 'mill_cmd weaver start --workspace "$workspace_root"' "$verify_release") -eq 1 ]] || {
  echo 'release verifier must start its disposable Weaver exactly once' >&2
  exit 1
}

fixture=$(mktemp -d "${TMPDIR:-/tmp}/verify-release-test.XXXXXX")
trap 'rm -rf "$fixture"' EXIT
cp -R "$repo_root/." "$fixture/candidate"

expect_failure() {
  local label=$1
  local needle=$2
  local mutation=$3
  cp "$repo_root/deps.edn" "$fixture/candidate/deps.edn"
  cp "$repo_root/.millstrand/deps.edn" "$fixture/candidate/.millstrand/deps.edn"
  python3 - "$fixture/candidate/$mutation" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
text = text.replace("71c0ed3d80fcad090b74a704a8eb165a3fad996e",
                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 1)
path.write_text(text, encoding="utf-8")
PY
  set +e
  output=$("$verify_release" --mode pre-tag --source-root "$fixture/candidate" \
    --core-release "$repo_root/release/msr04-release.json" \
    --kanban-release "$repo_root/release/msr05-release.json" 2>&1)
  status=$?
  set -e
  [[ "$status" -ne 0 && "$output" == *"$needle"* ]] || {
    printf '%s probe failed (status %s):\n%s\n' "$label" "$status" "$output" >&2
    exit 1
  }
}

expect_failure root-pin 'candidate root Millstrand pin is not the exact release coordinate' deps.edn
expect_failure workspace-pin 'candidate workspace Batteries pin is not aligned with Millstrand' .millstrand/deps.edn

cp "$repo_root/deps.edn" "$fixture/candidate/deps.edn"
cp "$repo_root/.millstrand/deps.edn" "$fixture/candidate/.millstrand/deps.edn"
printf '' >"$fixture/candidate/deps.edn"
set +e
output=$("$verify_release" --mode pre-tag --source-root "$fixture/candidate" \
  --core-release "$repo_root/release/msr04-release.json" \
  --kanban-release "$repo_root/release/msr05-release.json" 2>&1)
status=$?
set -e
[[ "$status" -ne 0 && "$output" == *'deps.edn is empty'* ]] || {
  printf 'empty-EDN probe failed (status %s):\n%s\n' "$status" "$output" >&2
  exit 1
}

cp "$repo_root/deps.edn" "$fixture/candidate/deps.edn"
cp "$repo_root/.millstrand/deps.edn" "$fixture/candidate/.millstrand/deps.edn"
printf '\n{}\n' >>"$fixture/candidate/deps.edn"
set +e
output=$("$verify_release" --mode pre-tag --source-root "$fixture/candidate" \
  --core-release "$repo_root/release/msr04-release.json" \
  --kanban-release "$repo_root/release/msr05-release.json" 2>&1)
status=$?
set -e
[[ "$status" -ne 0 && "$output" == *'contains trailing EDN forms'* ]] || {
  printf 'trailing-EDN probe failed (status %s):\n%s\n' "$status" "$output" >&2
  exit 1
}

expect_alias_failure() {
  local source=$1
  local mutation=$2
  local dep_key=$3
  local coordinate=$4
  cp "$repo_root/deps.edn" "$fixture/candidate/deps.edn"
  cp "$repo_root/.millstrand/deps.edn" "$fixture/candidate/.millstrand/deps.edn"
  python3 - "$fixture/candidate/$mutation" "$dep_key" "$coordinate" <<'PY'
import pathlib, re, sys
path = pathlib.Path(sys.argv[1])
dep_key = sys.argv[2]
coordinate = sys.argv[3]
text = path.read_text(encoding="utf-8").rstrip()
config = ''':conflict
           {%s
            {%s
             {:git/url "https://github.com/codethread/millstrand.git"
              :git/sha "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
              :deps/root "."}}}
''' % (dep_key, coordinate)
if re.search(r":aliases\s*{", text):
    text = re.sub(r"(:aliases\s*{)", r"\1" + config, text, count=1)
else:
    text = text[:-1] + "\n :aliases {" + config + "}}"
path.write_text(text + "\n", encoding="utf-8")
PY
  set +e
  output=$("$verify_release" --mode pre-tag --source-root "$fixture/candidate" \
    --core-release "$repo_root/release/msr04-release.json" \
    --kanban-release "$repo_root/release/msr05-release.json" 2>&1)
  status=$?
  set -e
  [[ "$status" -ne 0 && "$output" == *"candidate $source $coordinate pin disagrees in alias :conflict ($dep_key)"* ]] || {
    printf '%s %s alias probe failed (status %s):\n%s\n' "$source" "$dep_key" "$status" "$output" >&2
    exit 1
  }
}

for dep_key in :deps :extra-deps :override-deps :default-deps :replace-deps; do
  expect_alias_failure root deps.edn "$dep_key" io.millstrand/millstrand
  expect_alias_failure workspace .millstrand/deps.edn "$dep_key" io.millstrand/millstrand
done
expect_alias_failure root deps.edn :deps millstrand.spools/batteries
expect_alias_failure workspace .millstrand/deps.edn :extra-deps millhouse.spools/workflow

echo 'verify-release boundary probes: OK'
