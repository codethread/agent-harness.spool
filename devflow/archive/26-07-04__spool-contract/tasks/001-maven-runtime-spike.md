# Task 1: Maven runtime spike

**Document ID:** `TASK-spool-contract-001`

## TASK-spool-contract-001.P1 Scope

Type: AFK

Run a blocking disposable-workspace weaver experiment proving whether `clojure.repl.deps/add-libs` can resolve `:mvn/version` coordinates inside the live weaver launch model before PH4/PH5/PH7 proceed. If the spike fails, stop and route the feature back to the coordinator for contract reconsideration; do not redesign the contract in this task.

## TASK-spool-contract-001.P2 Must implement exactly

- **TASK-spool-contract-001.MI1:** Owned files: no product source files are owned. You may add a short findings artifact under this feature if useful, and you must leave exact commands/results as durable task notes.
- **TASK-spool-contract-001.MI2:** Read `devflow/TENETS.md`, `devflow/PHILOSOPHY.md`, `devflow/feat/spool-contract/proposal.md`, `devflow/feat/spool-contract/spool-contract.plan.md`, and both `devflow/feat/spool-contract/specs/*.delta.md` before running the spike.
- **TASK-spool-contract-001.MI3:** Use only explicit disposable `--workspace` values. Never use or reload the canonical `.skein` world and never edit workspace config files in the canonical repo world.
- **TASK-spool-contract-001.MI4:** Prove sync-time dynamic Maven loading for an ordinary `:mvn/version` coordinate through the live weaver/runtime path described by `SPEC-004.C94a.3@spool-contract`.
- **TASK-spool-contract-001.MI5:** Cover network fetch, warm `~/.m2` cache behavior, offline failure shape, and pre-`add-libs` implementability of mutable-version rejection (`-SNAPSHOT`, `RELEASE`, `LATEST`) plus repo-redirection rejection (`:mvn/repos`, `:mvn/local-repo`) from `SPEC-004.C94a.2@spool-contract`.
- **TASK-spool-contract-001.MI6:** Record progress with `strand update <your-task-id> --attr progress=...`; record spike commands/results with `strand op agent note <your-task-id> "..." --by <your-run-id>`.
- **TASK-spool-contract-001.MI7:** Commit policy: if you create or edit any file, commit the task's work on branch `spool-contract` with a conventional commit message. If the task produces notes only, report that there was no file commit to make.

## TASK-spool-contract-001.P3 Done when

- **TASK-spool-contract-001.DW1:** Durable notes include exact commands, workspace paths, success/failure output summaries, and a clear proceed/stop verdict.
- **TASK-spool-contract-001.DW2:** The verdict explicitly states whether PH4/PH5/PH7 may proceed under the sync-time Maven `add-libs` contract.
- **TASK-spool-contract-001.DW3:** Any optional findings file is committed on branch `spool-contract`.
- **TASK-spool-contract-001.DW4:** Set `--attr status=implemented` only after the spike verdict is recorded and any committed artifact is in git.

## TASK-spool-contract-001.P4 Out of scope

- **TASK-spool-contract-001.OS1:** Do not implement production Maven dependency policy or tests; that belongs to tasks 004 and 005.
- **TASK-spool-contract-001.OS2:** Do not choose a startup-time fallback or modify the feature contract if dynamic Maven loading fails.

## TASK-spool-contract-001.P5 References

- **TASK-spool-contract-001.REF1:** `SC-PLAN-001.PH1`, `SC-PLAN-001.OP1`, `SC-PLAN-001.R1`.
- **TASK-spool-contract-001.REF2:** `SC-PROP-001.Q1`, `SC-PROP-001.Q2`.
- **TASK-spool-contract-001.REF3:** `SPEC-004.C94a.2@spool-contract`, `SPEC-004.C94a.3@spool-contract`.
