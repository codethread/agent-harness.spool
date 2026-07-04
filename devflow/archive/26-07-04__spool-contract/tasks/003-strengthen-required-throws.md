# Task 3: Strengthen required use throws

**Document ID:** `TASK-spool-contract-003`

## TASK-spool-contract-003.P1 Scope

Type: AFK

Strengthen the surviving consumer-owned `use! :spools` guard so `:required? true` throws for `:not-approved`, `:not-synced`, and `:sync-failed`, while non-required activation still returns skip maps.

## TASK-spool-contract-003.P2 Must implement exactly

- **TASK-spool-contract-003.MI1:** Read `devflow/TENETS.md`, `devflow/PHILOSOPHY.md`, `devflow/feat/spool-contract/proposal.md`, `devflow/feat/spool-contract/spool-contract.plan.md`, and both `devflow/feat/spool-contract/specs/*.delta.md` before editing or validating.
- **TASK-spool-contract-003.MI2:** Owned files: `src/skein/api/weaver/alpha.clj` and `test/skein/spools_test.clj`. This task is sequenced after task 002 because the same files are shared.
- **TASK-spool-contract-003.MI3:** Update `skip-use` / `use!` behavior for surviving `:spools` skip reasons `:not-approved`, `:not-synced`, and `:sync-failed` according to `SPEC-004.C94` in the daemon-runtime delta and the matching REPL API delta.
- **TASK-spool-contract-003.MI4:** Add or update focused tests proving non-required `use!` still returns skip maps for all three reasons.
- **TASK-spool-contract-003.MI5:** Add or update focused tests proving `:required? true` throws for all three reasons with useful `ex-data` identifying the skip reason/spool context.
- **TASK-spool-contract-003.MI6:** Use only disposable `--workspace` values in any weaver validation. Never use or reload the canonical `.skein` world.
- **TASK-spool-contract-003.MI7:** After validation is green, run self-review: `strand op agent review <your-task-id> --members 1 --harness review-gpt --cwd <your-cwd> --spawned-by <your-run-id>`, await it, action legitimate findings, rerun validation, and record the review verdict in a task note. This task mutates the same critical `use!` path as 002 but lands after 002's review, so it carries its own.
- **TASK-spool-contract-003.MI8:** Record progress and validation notes with strand commands; commit the task's work on branch `spool-contract` with a conventional commit message.

## TASK-spool-contract-003.P3 Done when

- **TASK-spool-contract-003.DW1:** Focused tests pass with `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test --shard A` (the repo test runner has no --focus flag; shard A runs skein.spools-test, see test/skein/test_runner.clj add-libs-shards).
- **TASK-spool-contract-003.DW2:** Required and non-required behavior is covered for `:not-approved`, `:not-synced`, and `:sync-failed`.
- **TASK-spool-contract-003.DW3:** The task commit exists on branch `spool-contract`; set `status=implemented` only after validation is green.

## TASK-spool-contract-003.P4 Out of scope

- **TASK-spool-contract-003.OS1:** Do not reintroduce manifest-only skip reasons such as `:unmet-needs` or `:provides-unloadable`.
- **TASK-spool-contract-003.OS2:** Do not implement Maven-only dependency policy; task 004 owns it.

## TASK-spool-contract-003.P5 References

- **TASK-spool-contract-003.REF1:** `SC-PLAN-001.PH3`, `SC-PLAN-001.CM2`, `SC-PLAN-001.OP2`.
- **TASK-spool-contract-003.REF2:** `SC-PROP-001.S3`, `SC-PROP-001.I3`.
- **TASK-spool-contract-003.REF3:** `DELTA-daemon-runtime-spool-contract-001.P2` clause `SPEC-004.C94`; `DELTA-repl-api-spool-contract-001.P2` `use!` option paragraphs.
