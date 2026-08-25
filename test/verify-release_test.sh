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

candidate_pin_block=$(sed -n '/^read_candidate_pin() {$/,/^}$/p' "$verify_release")
for required in \
  'candidate_pin=$(clojure -Sdeps' \
  '"$candidate_root/deps.edn"' \
  'core_url=$(printf' \
  'core_sha=$(printf' \
  'candidate root Millstrand pin must be an exact immutable coordinate' \
  'candidate Millstrand pin disagrees in alias'; do
  if [[ "$candidate_pin_block" != *"$required"* ]]; then
    printf 'verify-release candidate-pin selection probe failed; missing %s\n' \
      "$required" >&2
    exit 1
  fi
done
published_block=$(sed -n '/^else$/,/^fi$/p' "$verify_release")
if [[ "$published_block" == *historical_core* ||
      "$published_block" == *'core_sha="$historical'* ||
      "$published_block" == *'core_url="$historical'* ]]; then
  echo "verify-release published candidate-pin selection probe failed; published mode still uses historical core coordinates" >&2
  exit 1
fi
if ! grep -Fq 'read_candidate_pin "$candidate_root"' "$verify_release"; then
  echo "verify-release candidate-pin selection probe failed; both modes do not derive the candidate core pin" >&2
  exit 1
fi
published_clone_line=$(grep -n 'git clone --quiet --depth 1 --branch "$tag"' "$verify_release" | cut -d: -f1)
candidate_pin_call_line=$(grep -n '^read_candidate_pin "$candidate_root"$' "$verify_release" | cut -d: -f1)
core_clone_line=$(grep -n 'git clone --quiet "$core_url"' "$verify_release" | cut -d: -f1)
if (( candidate_pin_call_line <= published_clone_line || candidate_pin_call_line >= core_clone_line )); then
  echo "verify-release published candidate-pin ordering probe failed; pin is not derived from the cloned candidate before core resolution" >&2
  exit 1
fi
echo "verify-release candidate-pin selection and published parity: OK"

candidate_fixture=$(mktemp -d "${TMPDIR:-/tmp}/verify-release-candidate.XXXXXX")
trap 'rm -rf "$candidate_fixture"' EXIT
cp "$repo_root/deps.edn" "$candidate_fixture/deps.edn"
mkdir "$candidate_fixture/.git"

expect_candidate_failure() {
  local label=$1
  local needle=$2
  local mutation=$3
  local output status
  cp "$repo_root/deps.edn" "$candidate_fixture/deps.edn"
  python3 - "$candidate_fixture/deps.edn" "$mutation" <<'PY'
import pathlib
import sys

path, mutation = sys.argv[1:]
text = pathlib.Path(path).read_text(encoding="utf-8")
sha = "6f265f45f894859c74dfd7c6bf32a94c48cb32d0"
if mutation == "sha":
    text = text.replace(':git/sha "' + sha + '"', ':git/sha "not-a-sha"', 1)
elif mutation == "disagreement":
    marker = text.index(':test')
    before, after = text[:marker], text[marker:]
    after = after.replace(sha, 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 1)
    text = before + after
else:
    raise SystemExit("unknown mutation: " + mutation)
pathlib.Path(path).write_text(text, encoding="utf-8")
PY
  set +e
  output=$("$verify_release" --mode pre-tag --source-root "$candidate_fixture" \
    --core-release "$repo_root/release/msr04-release.json" \
    --kanban-release "$repo_root/release/msr05-release.json" 2>&1)
  status=$?
  set -e
  if [[ "$status" -eq 0 || "$output" != *"$needle"* ]]; then
    printf 'verify-release candidate conflict probe failed for %s (status %s):\n%s\n' \
      "$label" "$status" "$output" >&2
    exit 1
  fi
}

expect_candidate_failure sha "exact immutable coordinate" sha
expect_candidate_failure disagreement "disagrees in alias" disagreement
echo "verify-release candidate coordinate conflict probes: OK"

candidate_pin_probe=$(mktemp)
candidate_pin_fake_bin=$(mktemp -d "${TMPDIR:-/tmp}/verify-release-candidate-pin.XXXXXX")
trap 'rm -rf "$candidate_fixture" "$candidate_pin_probe" "$candidate_pin_fake_bin"' EXIT
{
  printf '%s\n' 'set -euo pipefail' \
    'die() { echo "verify-release: $*" >&2; exit 1; }'
  sed -n '/^read_candidate_pin() {$/,/^}$/p' "$verify_release"
  printf '%s\n' 'read_candidate_pin "$1"'
} >"$candidate_pin_probe"
chmod +x "$candidate_pin_probe"
printf '%s\n' '#!/usr/bin/env bash' \
  'printf "%s\\n" "https://github.com/codethread/millstrand.git" "6f265f45f894859c74dfd7c6bf32a94c48cb32d0" "diagnostic from candidate pin command"' \
  >"$candidate_pin_fake_bin/clojure"
chmod +x "$candidate_pin_fake_bin/clojure"
set +e
output=$(PATH="$candidate_pin_fake_bin:$PATH" "$candidate_pin_probe" "$candidate_fixture" 2>&1)
status=$?
set -e
if [[ "$status" -eq 0 || "$output" != *"candidate io.millstrand/millstrand pin is invalid"* ||
      "$output" != *"diagnostic from candidate pin command"* ]]; then
  printf 'verify-release candidate-pin diagnostic probe failed (status %s):\n%s\n' \
    "$status" "$output" >&2
  exit 1
fi
echo "verify-release candidate-pin diagnostic rejection probe: OK"

consumer_block=$(sed -n '/^cat >"\$consumer_root\/deps.edn" <<EOF$/,/^EOF$/p' "$verify_release")
expected_millhouse_sha="f1cdda3b46706b186f547251d285791be650d232"
if ! grep -Fq "millhouse_sha=\"$expected_millhouse_sha\"" "$verify_release"; then
  echo "verify-release consumer dependency probe failed; Millhouse pin changed" >&2
  exit 1
fi
if grep -Fq 'millhouse_identity_sha=' "$verify_release"; then
  echo "verify-release consumer dependency probe failed; identity has a redundant separate pin" >&2
  exit 1
fi
for root in workflow kanban identity; do
  root_block=$(printf '%s\n' "$consumer_block" | sed -n "/millhouse.spools\\/$root /,/deps\\/root/p")
  if [[ "$root_block" != *"millhouse.spools/$root"* ||
        "$root_block" != *':git/url "$millhouse_url"'* ||
        "$root_block" != *':git/sha "$millhouse_sha"'* ||
        "$root_block" != *":deps/root \"spools/$root\""* ]]; then
    printf 'verify-release consumer dependency probe failed; %s is not on the exact shared H1 closure\n' \
      "$root" >&2
    exit 1
  fi
done
if ! grep -Fq 'die "clean consumer dependency resolution failed: $classpath"' "$verify_release" || \
   ! grep -Fq 'die "clean consumer load failed: $smoke"' "$verify_release"; then
  echo "verify-release consumer dependency probe failed; closure does not fail loudly" >&2
  exit 1
fi
echo "verify-release consumer dependency closure: OK"

for required in \
  'candidate_coord/spools.edn' \
  'codethread/devflow-kanban-adapter' \
  'millhouse.spools/identity "spools/identity"' \
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

if ! grep -Fq 'rm -f "$tmp_root/agent-harness/.millstrand/config.local.json" \' "$verify_release" || \
   ! grep -Fq '"$tmp_root/agent-harness/.millstrand/spools.local.edn"' "$verify_release"; then
  echo "verify-release overlay probe failed; source-root local overlays are copied into the candidate" >&2
  exit 1
fi
if grep -Fq 'rm -f "$tmp_root/agent-harness/.millstrand/init.local.clj"' "$verify_release"; then
  echo "verify-release overlay probe failed; generated init.local.clj is removed" >&2
  exit 1
fi

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
