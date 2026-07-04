# Task 9: Coordinator fold-in

**Document ID:** `TASK-spool-contract-009`

## TASK-spool-contract-009.P1 Scope

Type: HITL

Coordinator-owned fold-in after AFK work: external push, dual pin update, root spec delta merge, and post-pin validation. This is intentionally non-delegable AFK work until the coordinator/human confirms external publication and merge timing.

## TASK-spool-contract-009.P2 Must implement exactly

- **TASK-spool-contract-009.MI1:** Read `devflow/TENETS.md`, `devflow/PHILOSOPHY.md`, `devflow/feat/spool-contract/proposal.md`, `devflow/feat/spool-contract/spool-contract.plan.md`, and both `devflow/feat/spool-contract/specs/*.delta.md` before editing or validating.
- **TASK-spool-contract-009.MI2:** Owned by coordinator, not an unattended AFK worker by default.
- **TASK-spool-contract-009.MI3:** Push the reviewed `/Users/ct/dev/projects/devflow.spool` commit externally, then update both in-repo pins for `codethread/devflow.spool`: `.skein/spools.edn` and `deps.edn :test` extra-deps per `SC-PLAN-001.AA6`.
- **TASK-spool-contract-009.MI4:** Update the hardcoded blob SHA link in `spools/devflow.md:6` after the external push per `SC-PLAN-001.AA6`.
- **TASK-spool-contract-009.MI5:** Merge `devflow/feat/spool-contract/specs/repl-api.delta.md` into `devflow/specs/repl-api.md` and `devflow/feat/spool-contract/specs/daemon-runtime.delta.md` into `devflow/specs/daemon-runtime.md`; mark both deltas `Status: Merged`; update `devflow/README.md` spec index if it lists spec status, per `SC-PLAN-001.AA7` and `SC-PLAN-001.PH8`.
- **TASK-spool-contract-009.MI6:** Rerun post-pin validation: `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test` (at minimum ensure `test/skein/config_test.clj` validates the new pinned `devflow.spool`), `(cd cli && go test ./...)`, and `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:smoke`.
- **TASK-spool-contract-009.MI7:** Confirm `git status --short` shows no generated SQLite/runtime artifacts.

## TASK-spool-contract-009.P3 Done when

- **TASK-spool-contract-009.DW1:** External `devflow.spool` publication is complete and both local pin consumers reference the published commit.
- **TASK-spool-contract-009.DW2:** Root specs contain the `C94a@spool-contract` semantics and no longer describe manifest behavior as shipped contract.
- **TASK-spool-contract-009.DW3:** Both delta files are marked `Status: Merged`.
- **TASK-spool-contract-009.DW4:** Post-pin Clojure, Go, and smoke validation pass and generated artifacts are clean.

## TASK-spool-contract-009.P4 Out of scope

- **TASK-spool-contract-009.OS1:** Do not treat this as a normal AFK task until the coordinator supplies the external push/fold-in go-ahead.
- **TASK-spool-contract-009.OS2:** Do not redo implementation slices from tasks 001-008 except to resolve integration failures found during fold-in validation.

## TASK-spool-contract-009.P5 References

- **TASK-spool-contract-009.REF1:** `SC-PLAN-001.AA6`, `SC-PLAN-001.AA7`, `SC-PLAN-001.PH8`, `SC-PLAN-001.PH9`, `SC-PLAN-001.OP5`.
