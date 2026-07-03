# Task 1: Git coordinate grammar and normalization

**Document ID:** `SGD-TASK-001`

## SGD-TASK-001.P1 Scope

Type: AFK

Extend the approved-spool config grammar in `src/skein/api/weaver/alpha.clj` with the git coordinate kind from [SGD-SPEC-RA-001.D1](../specs/repl-api.delta.md). Grammar/normalization only — no fetching (task 2 adds it).

## SGD-TASK-001.P2 Must implement exactly

- **SGD-TASK-001.MI1:** A spool entry is EITHER a local entry (exactly `{:local/root <non-blank string>}`, unchanged) OR a git entry. Mixed kind keys or unknown keys fail loudly with ex-info + data matching the existing error style in `validate-approved-spool-entry!` (src/skein/api/weaver/alpha.clj:88).
- **SGD-TASK-001.MI2:** Git entry keys: `:git/url` required non-blank string (never interpreted, passed to git as-is later); `:git/sha` required, exactly 40 lowercase hex chars (reject short/uppercase/non-hex loudly); `:git/tag` optional non-blank string; `:deps/root` optional non-blank string that must be a relative path with no leading `/`, no `~`, and no `..` segments; `:deps/root` is only valid on git entries.
- **SGD-TASK-001.MI3:** Normalized git entries carry `:kind :git`, the raw `:git/*` and `:deps/root` values, `:source` as today, and `:root` = `<cache-base>/skein/spools/<sha>` plus `/<deps-root>` when present, where cache-base is `XDG_CACHE_HOME` env when set and non-blank, else `<user.home>/.cache`. Normalized local entries gain `:kind :local` and keep every existing key exactly as-is.
- **SGD-TASK-001.MI4:** Do not change `sync-approved-spool!` behavior beyond compiling; a git entry with no cache dir surfaces as the existing `:missing-root` sync outcome for now.
- **SGD-TASK-001.MI5:** The cache-base lookup must be its own private redefinable fn (mirror `skein.api.peers.alpha/state-root`, src/skein/api/peers/alpha.clj:16-20) so tests can isolate it via `alter-var-root` — the suite runs in one JVM and cannot mutate real env per test (see test/skein/peers_test.clj:46-53 for the pattern).
- **SGD-TASK-001.MI6:** Define the per-kind sync-outcome map shape: outcome maps (success and `sync-failed`) carry `:kind` and only kind-relevant source fields — `:local/root` for local entries; `:git/url`/`:git/sha` (plus `:git/tag`/`:deps/root` when present) for git entries. Never emit nil-stuffed keys of the other kind. Adjust `sync-failed` and the success result in `sync-approved-spool!` (src/skein/api/weaver/alpha.clj:160-167, 205-209) accordingly.

## SGD-TASK-001.P3 Done when

- **SGD-TASK-001.DW1:** New tests in `test/skein/spools_test.clj` (mirroring its fixture style) cover: valid git entry normalization incl. cache-base honoring (isolated by rebinding the MI5 fn via `alter-var-root`, not real env mutation); `:deps/root` appended to `:root`, rejected when absolute or containing `..`, and rejected on a `:local/root` entry; bad sha (short/uppercase/non-hex), blank url, unknown keys, and mixed `:local/root`+`:git/url` all throwing with informative ex-data; per-kind outcome shape from MI6 (git outcomes carry no `:local/root` key); existing local-entry tests pass unchanged.
- **SGD-TASK-001.DW2:** `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test` fully green.

## SGD-TASK-001.P4 Out of scope

- **SGD-TASK-001.OS1:** Fetching, tag verification, manifest handling, docs, spec merging.

## SGD-TASK-001.P5 References

- **SGD-TASK-001.REF1:** [specs/repl-api.delta.md](../specs/repl-api.delta.md) D1; [plan](../spool-git-distribution.plan.md) PH1; `devflow/TENETS.md` TEN-003/TEN-004; `src/skein/api/weaver/alpha.clj:68-158`; `test/skein/spools_test.clj`.
