# Task 3: Manifest parsing and use! gating

**Document ID:** `SGD-TASK-003`

## SGD-TASK-003.P1 Scope

Type: AFK

Add the optional `spool.edn` manifest with loud validation, unmet-needs reporting at `sync!`, and activation gating at `use!`, per [SGD-SPEC-RA-001.D2/D3](../specs/repl-api.delta.md) and [SGD-SPEC-DR-001.D3/D4](../specs/daemon-runtime.delta.md). Builds on tasks 1–2. Design intent (RFC-017): no integer version levels, no transitive auto-fetch — the manifest is a contract lint + activation gate, not a package manager.

## SGD-TASK-003.P2 Must implement exactly

- **SGD-TASK-003.MI1:** Optional `spool.edn` at the spool's effective `:root` (after `:deps/root`). Grammar — map with only these keys, all optional; unknown keys fail: `:coordinate` symbol (when present and ≠ the spools.edn key → `:failed` / `:coordinate-mismatch` with expected/actual); `:provides` set or vector of namespace symbols; `:needs` map of coordinate symbol → nil OR `{:suggest {:git/url <non-blank string>}}`; `:docs` string or map of namespace symbol → string. Malformed manifest → `:failed` / `:manifest-invalid`, root NOT loaded.
- **SGD-TASK-003.MI2:** Valid manifests parse during `sync-approved-spool!`; normalized manifest included in the successful sync result under `:manifest`.
- **SGD-TASK-003.MI3:** After all entries sync, `sync-approved-spools` computes needs satisfaction: each needed coordinate must be in the approved set AND have a successful sync. Violations recorded on the needing spool's result as `:unmet-needs [{:lib <coord> :reason :not-approved|:sync-failed :suggest <map-or-nil>}]`. Status stays `:loaded`; no fetch is ever triggered by a need.
- **SGD-TASK-003.MI4:** `use!` gating extending `use-spool-skip` semantics: any lib in the module's `:spools` whose latest sync result has non-empty `:unmet-needs` → skip reason `:unmet-needs` with the list in the data. When a lib's manifest declares `:provides`, verify each declared namespace requires cleanly inside the spool classloader (mirror existing module-load classloader handling — plan risk SGD-PLAN-001.R2); failure → skip reason `:provides-unloadable` with namespace and error message. `:required? true` surfaces these exactly like existing required-module failures. Check order: approval/sync → unmet-needs → provides → `:after`.

## SGD-TASK-003.P3 Done when

- **SGD-TASK-003.DW1:** Tests in `test/skein/spools_test.clj` cover: manifest parsed and exposed in sync result; absent manifest = no `:manifest` key and prior behavior; each malformed shape → `:manifest-invalid`; coordinate mismatch → `:coordinate-mismatch`; needs satisfied across two local fixture spools; needing an unapproved coordinate → `:unmet-needs` `:not-approved` with `:suggest` passed through; needing a failed-sync spool → `:unmet-needs` `:sync-failed`; `use!` skip on `:unmet-needs`; `use!` `:required? true` throws; provides satisfied loads; provides naming a nonexistent ns → `:provides-unloadable` skip.
- **SGD-TASK-003.DW2:** `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test` fully green.

## SGD-TASK-003.P4 Out of scope

- **SGD-TASK-003.OS1:** CLI changes, transitive fetch, docs, spec merging.

## SGD-TASK-003.P5 References

- **SGD-TASK-003.REF1:** Both spec deltas; [plan](../spool-git-distribution.plan.md) PH3 + R2; `src/skein/api/weaver/alpha.clj` (`use-spool-skip`, `validate-use-opts!`, `use!`); RFC-017.NG2.
