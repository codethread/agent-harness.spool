# Task 8: Full validation

**Document ID:** `TASK-spool-contract-008`

## TASK-spool-contract-008.P1 Scope

Type: AFK

Run the pre-pin full validation gate for completed in-repo and local external demo work, confirming the worktree is ready for coordinator fold-in. Post-pin revalidation remains coordinator-owned.

## TASK-spool-contract-008.P2 Must implement exactly

- **TASK-spool-contract-008.MI1:** Read `devflow/TENETS.md`, `devflow/PHILOSOPHY.md`, `devflow/feat/spool-contract/proposal.md`, `devflow/feat/spool-contract/spool-contract.plan.md`, and both `devflow/feat/spool-contract/specs/*.delta.md` before editing or validating.
- **TASK-spool-contract-008.MI2:** Owned files: none expected. This is a validation/reporting task; only commit if you must remove generated artifacts and that cleanup is appropriate for git.
- **TASK-spool-contract-008.MI3:** Run `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test` from the Skein worktree.
- **TASK-spool-contract-008.MI4:** Run `(cd cli && go test ./...)` from the Skein worktree.
- **TASK-spool-contract-008.MI5:** Run `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:smoke` from the Skein worktree.
- **TASK-spool-contract-008.MI6:** Confirm `git status --short` in the Skein worktree shows no generated SQLite/runtime metadata artifacts. Clean generated artifacts if needed and safe.
- **TASK-spool-contract-008.MI7:** Use only disposable workspaces for any ad hoc runtime investigation. Never use or reload the canonical `.skein` world.
- **TASK-spool-contract-008.MI8:** Record all commands, pass/fail summaries, cleanup, and the explicit note that post-pin revalidation happens at coordinator fold-in.

## TASK-spool-contract-008.P3 Done when

- **TASK-spool-contract-008.DW1:** Clojure tests, Go CLI tests, and smoke validation pass with commands/results recorded in task notes.
- **TASK-spool-contract-008.DW2:** `git status --short` is free of generated SQLite/runtime metadata artifacts; any remaining changes are intentional feature changes from prior tasks.
- **TASK-spool-contract-008.DW3:** Notes explicitly state that `.skein/spools.edn`, `deps.edn :test` pin bump validation, `spools/devflow.md:6`, and root spec merge validation are deferred to coordinator fold-in.
- **TASK-spool-contract-008.DW4:** Set `status=implemented` only after the full pre-pin gate is green.

## TASK-spool-contract-008.P4 Out of scope

- **TASK-spool-contract-008.OS1:** Do not push external repos.
- **TASK-spool-contract-008.OS2:** Do not bump pins, update `spools/devflow.md`, or merge root specs; task 009/coordinator fold-in owns those human/coordinator actions.
- **TASK-spool-contract-008.OS3:** Do not paper over failing validation by changing feature scope; report exact failures if the gate is not green.

## TASK-spool-contract-008.P5 References

- **TASK-spool-contract-008.REF1:** `SC-PLAN-001.PH9` pre-pin portion, `SC-PLAN-001.V1`, `SC-PLAN-001.V3`, `SC-PLAN-001.V4`.
- **TASK-spool-contract-008.REF2:** `SC-PLAN-001.OP5`, `SC-PLAN-001.R6`.
