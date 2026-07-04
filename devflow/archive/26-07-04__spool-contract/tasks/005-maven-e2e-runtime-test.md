# Task 5: Maven e2e runtime test

**Document ID:** `TASK-spool-contract-005`

## TASK-spool-contract-005.P1 Scope

Type: AFK

Add live daemon/runtime coverage proving an approved spool's Maven dependency is available after `sync!` and can be used by an activated spool namespace.

## TASK-spool-contract-005.P2 Must implement exactly

- **TASK-spool-contract-005.MI1:** Read `devflow/TENETS.md`, `devflow/PHILOSOPHY.md`, `devflow/feat/spool-contract/proposal.md`, `devflow/feat/spool-contract/spool-contract.plan.md`, and both `devflow/feat/spool-contract/specs/*.delta.md` before editing or validating.
- **TASK-spool-contract-005.MI2:** Owned file: `test/skein/runtime_deps_test.clj`. Do not edit `src/skein/api/weaver/alpha.clj`, `test/skein/spools_test.clj`, docs, or external repos.
- **TASK-spool-contract-005.MI3:** Add a deterministic runtime test for `SPEC-004.C94a.3@spool-contract` using a small Maven dependency declared in an approved spool root's top-level `deps.edn :deps`.
- **TASK-spool-contract-005.MI4:** The test must exercise the live weaver/runtime `sync!` path and activation of a spool namespace that uses the Maven dependency; it must not only prove plain test-JVM classpath resolution.
- **TASK-spool-contract-005.MI5:** Isolate all weaver state with explicit disposable workspaces. Do not start test weavers through implicit repo discovery or any user-owned workspace, and never use/reload the canonical `.skein` world.
- **TASK-spool-contract-005.MI6:** Keep Maven/cache assumptions deterministic enough for local and CI runs; if the test relies on an existing test helper pattern, follow it rather than inventing a parallel runtime harness.
- **TASK-spool-contract-005.MI7:** Record progress and validation notes with strand commands; commit the task's work on branch `spool-contract` with a conventional commit message.

## TASK-spool-contract-005.P3 Done when

- **TASK-spool-contract-005.DW1:** Focused runtime dependency tests pass with `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test --shard B` (the repo test runner has no --focus flag; shard B runs skein.runtime-deps-test last because it poisons the tools.deps basis, see test/skein/test_runner.clj add-libs-shards).
- **TASK-spool-contract-005.DW2:** The test fails if the Maven dependency is not added through `sync!` before activation.
- **TASK-spool-contract-005.DW3:** The task commit exists on branch `spool-contract`; set `status=implemented` only after validation is green.

## TASK-spool-contract-005.P4 Out of scope

- **TASK-spool-contract-005.OS1:** Do not change Maven dependency policy implementation; task 004 owns behavior.
- **TASK-spool-contract-005.OS2:** Do not update docs or external `devflow.spool`; tasks 006 and 007 own those slices.

## TASK-spool-contract-005.P5 References

- **TASK-spool-contract-005.REF1:** `SC-PLAN-001.PH5`, `SC-PLAN-001.AA2`, `SC-PLAN-001.V1`, `SC-PLAN-001.V2`.
- **TASK-spool-contract-005.REF2:** `SC-PROP-001.V2`.
- **TASK-spool-contract-005.REF3:** `SPEC-004.C94a.3@spool-contract`.
