# Task 7: Devflow spool demo

**Document ID:** `TASK-spool-contract-007`

## TASK-spool-contract-007.P1 Scope

Type: AFK

In `/Users/ct/dev/projects/devflow.spool`, update the external demo spool to the manifest-free contract and add observable `camel-snake-kebab` Maven dependency usage. Verify locally only; do not push.

## TASK-spool-contract-007.P2 Must implement exactly

- **TASK-spool-contract-007.MI1:** Read `devflow/TENETS.md`, `devflow/PHILOSOPHY.md`, `devflow/feat/spool-contract/proposal.md`, `devflow/feat/spool-contract/spool-contract.plan.md`, and both `devflow/feat/spool-contract/specs/*.delta.md` before editing or validating.
- **TASK-spool-contract-007.MI2:** Cwd for this task: `/Users/ct/dev/projects/devflow.spool`. Owned files are in that external repo only: its `spool.edn` deletion, `deps.edn`, README, source/tests needed for harmless `camel-snake-kebab` usage. Do not edit this repo except for durable task notes.
- **TASK-spool-contract-007.MI3:** Delete or stop shipping any external `spool.edn` per `SC-PROP-001.S8` and `SC-PROP-001.I8`.
- **TASK-spool-contract-007.MI4:** Add `camel-snake-kebab` as a top-level Maven dependency in `devflow.spool`'s `deps.edn :deps` and use it harmlessly from `skein.spools.devflow` so availability is observable.
- **TASK-spool-contract-007.MI5:** Rewrite the external README around Dependency information and Activation copy-paste snippets, including prerequisites and activation order, matching this feature's manifest-free contract.
- **TASK-spool-contract-007.MI6:** From the Skein worktree, verify with a disposable workspace whose `spools.local.edn` approves the local external checkout that `sync!` + `use!` activates the updated spool and exercises the Maven dependency. Never use or reload the canonical `.skein` world.
- **TASK-spool-contract-007.MI7:** Also prove `camel-snake-kebab` is resolvable on the plain test-JVM classpath via transitive local dep resolution of the LOCAL checkout (not the old pinned sha — `deps.edn :test` still pins `de735e7...`, which lacks your changes). Run from the Skein worktree (`/Users/ct/dev/projects/skein-src__spool-contract`, not this task's external cwd), overriding the dep to the local root, e.g.: `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -Sdeps '{:deps {io.github.codethread/devflow.spool {:local/root "/Users/ct/dev/projects/devflow.spool"}}}' -M -e "(require (quote skein.spools.devflow)) (require (quote camel-snake-kebab.core)) (println :test-jvm-load-ok)"` — adapt if needed (the config_test.clj:71-72 `spool-checkout-root` local-override is the reference pattern), but the hard requirement is: the load must resolve the local working copy and prove `camel-snake-kebab` arrives transitively. This validates the `docs/library-authoring.md` test-JVM boundary, not only the weaver `sync!` path.
- **TASK-spool-contract-007.MI8:** After validation is green, run self-review from the external repo cwd: `strand op agent review <your-task-id> --members 1 --harness review-gpt --cwd /Users/ct/dev/projects/devflow.spool --spawned-by <your-run-id>`, await it, action legitimate findings, rerun validation, and record the review verdict in a task note.
- **TASK-spool-contract-007.MI9:** Commit the task's work in `/Users/ct/dev/projects/devflow.spool` on its current/default branch with a conventional commit message. Do not push.

## TASK-spool-contract-007.P3 Done when

- **TASK-spool-contract-007.DW1:** Local disposable-workspace `sync!` + `use!` validation succeeds against `/Users/ct/dev/projects/devflow.spool` and records exact commands/results in a task note.
- **TASK-spool-contract-007.DW2:** Plain test-JVM load of `skein.spools.devflow` from the local external checkout (via the MI7 local-root override, run from the Skein worktree) proves `camel-snake-kebab` is transitively resolvable.
- **TASK-spool-contract-007.DW3:** Cross-vendor self-review has no unactioned legitimate findings, validation was rerun after review, and the verdict is recorded.
- **TASK-spool-contract-007.DW4:** The external repo has a local commit and no push was performed; set `status=implemented` only after validation/review are green.

## TASK-spool-contract-007.P4 Out of scope

- **TASK-spool-contract-007.OS1:** Do not push `devflow.spool`; coordinator fold-in owns external push.
- **TASK-spool-contract-007.OS2:** Do not bump this repo's `.skein/spools.edn` or `deps.edn :test` pins and do not update `spools/devflow.md:6`; coordinator fold-in owns AA6.
- **TASK-spool-contract-007.OS3:** Do not merge root spec deltas; coordinator fold-in owns AA7.

## TASK-spool-contract-007.P5 References

- **TASK-spool-contract-007.REF1:** `SC-PLAN-001.PH7`, `SC-PLAN-001.AA5`, `SC-PLAN-001.OP4`, `SC-PLAN-001.R6`.
- **TASK-spool-contract-007.REF2:** `SC-PROP-001.S8`, `SC-PROP-001.I8`, `SC-PROP-001.V3`.
- **TASK-spool-contract-007.REF3:** `docs/library-authoring.md`; `SPEC-004.C94a.3@spool-contract`.
