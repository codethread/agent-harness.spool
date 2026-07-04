# Task 2: Remove manifest machinery

**Document ID:** `TASK-spool-contract-002`

## TASK-spool-contract-002.P1 Scope

Type: AFK

Remove the runtime `spool.edn` manifest contract and rewrite the focused manifest tests so approved spool behavior is driven by `spools.edn` approval, explicit load/call options, `:after`, and consumer-owned `:spools` guards.

## TASK-spool-contract-002.P2 Must implement exactly

- **TASK-spool-contract-002.MI1:** Read `devflow/TENETS.md`, `devflow/PHILOSOPHY.md`, `devflow/feat/spool-contract/proposal.md`, `devflow/feat/spool-contract/spool-contract.plan.md`, and both `devflow/feat/spool-contract/specs/*.delta.md` before editing or validating.
- **TASK-spool-contract-002.MI2:** Owned files: `src/skein/api/weaver/alpha.clj` and `test/skein/spools_test.clj`. Do not edit docs, root specs, deltas, `.skein` config, or unrelated tests.
- **TASK-spool-contract-002.MI3:** Remove `manifest-keys`, `normalize-manifest!`, `read-spool-manifest`, manifest-invalid/coordinate-mismatch behavior, unmet-needs computation, manifest-shaped sync result keys, and provides/unmet-needs `use!` gates per `SPEC-003.P5@spool-contract` and `SPEC-004.C93`/`SPEC-004.C94` delta clauses.
- **TASK-spool-contract-002.MI4:** Ensure existing `spool.edn` files are ignored by runtime code; do not add warning or migration behavior.
- **TASK-spool-contract-002.MI5:** Rewrite former manifest tests in `test/skein/spools_test.clj` to assert sync results are no longer shaped by `spool.edn` and activation is driven by explicit `:spools`, `:after`, load, and `:call` options.
- **TASK-spool-contract-002.MI6:** Keep approved-spool `spools.edn` grammar tests around `test/skein/spools_test.clj:129-295` green, including approved local roots, sha-pinned git, `:git/tag`, `:deps/root`, and overlay cases.
- **TASK-spool-contract-002.MI7:** Use only disposable `--workspace` values in any weaver validation. Never use or reload the canonical `.skein` world.
- **TASK-spool-contract-002.MI8:** After validation is green, run self-review: `strand op agent review <your-task-id> --members 1 --harness review-gpt --cwd <your-cwd> --spawned-by <your-run-id>`, await it, action legitimate findings, rerun validation, and record the review verdict in a task note.
- **TASK-spool-contract-002.MI9:** Commit the task's work on branch `spool-contract` with a conventional commit message.

## TASK-spool-contract-002.P3 Done when

- **TASK-spool-contract-002.DW1:** Focused Clojure tests pass with `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test --shard A` (the repo test runner has no --focus flag; shard A runs skein.spools-test, see test/skein/test_runner.clj add-libs-shards).
- **TASK-spool-contract-002.DW2:** Approved-spool grammar tests in `test/skein/spools_test.clj:129-295` remain green.
- **TASK-spool-contract-002.DW3:** Cross-vendor self-review has no unactioned legitimate findings, validation was rerun after review, and the verdict is recorded.
- **TASK-spool-contract-002.DW4:** The task commit exists on branch `spool-contract` and the strand has `status=implemented` only after validation/review are green.

## TASK-spool-contract-002.P4 Out of scope

- **TASK-spool-contract-002.OS1:** Do not implement `:required?` throwing changes; task 003 owns that surviving guard behavior.
- **TASK-spool-contract-002.OS2:** Do not implement Maven-only dependency policy; task 004 owns `SPEC-004.C94a@spool-contract`.
- **TASK-spool-contract-002.OS3:** Do not delete shipped `spool.edn` files or update docs; task 006 owns the orphan sweep.

## TASK-spool-contract-002.P5 References

- **TASK-spool-contract-002.REF1:** `SC-PLAN-001.PH2`, `SC-PLAN-001.OP2`.
- **TASK-spool-contract-002.REF2:** `SC-PROP-001.S1`, `SC-PROP-001.I1`, `SC-PROP-001.I2`, `SC-PROP-001.I3`, `SC-PROP-001.I11`.
- **TASK-spool-contract-002.REF3:** `DELTA-repl-api-spool-contract-001.P2`, `DELTA-repl-api-spool-contract-001.P3`; `DELTA-daemon-runtime-spool-contract-001.P2` clauses `SPEC-004.C93` and `SPEC-004.C94`.
