# Task 6: Docs orphan sweep

**Document ID:** `TASK-spool-contract-006`

## TASK-spool-contract-006.P1 Scope

Type: AFK

Rewrite in-repo shared-spool guidance for the finalized manifest-free Maven-only policy, delete the shipped in-repo manifest orphan, and record RFC-018 as rejected/mooted.

## TASK-spool-contract-006.P2 Must implement exactly

- **TASK-spool-contract-006.MI1:** Read `devflow/TENETS.md`, `devflow/PHILOSOPHY.md`, `devflow/feat/spool-contract/proposal.md`, `devflow/feat/spool-contract/spool-contract.plan.md`, and both `devflow/feat/spool-contract/specs/*.delta.md` before editing or validating.
- **TASK-spool-contract-006.MI2:** Owned files: `docs/writing-shared-spools.md`, `AGENTS.md`, `CLAUDE.md` symlink target as applicable, `spools/README.md`, `devflow/README.md`, `devflow/rfcs/2026-07-03-spool-needs-shipped-namespaces.md`, and `spools/agents/spool.edn` deletion. Do not edit product source, tests, root specs, deltas, or external repos.
- **TASK-spool-contract-006.MI3:** Rewrite shared-spool docs around README Dependency information and Activation snippets per `SC-PROP-001.S7` and `DELTA-repl-api-spool-contract-001.P3`.
- **TASK-spool-contract-006.MI4:** Document the finalized Maven-only dependency policy from `SPEC-004.C94a@spool-contract`, including exact-source spool approval, Maven-only `deps.edn :deps`, source-bearing dependency rejection, mutable version rejection, repo-redirection rejection, ignored aliases, and weaver-wide runtime loading.
- **TASK-spool-contract-006.MI5:** Remove manifest, `:needs`, `:provides`, `:unmet-needs`, `:provides-unloadable`, and coordinate-mismatch guidance from live docs. Historical/archive/spec-delta references may remain only when clearly intentional.
- **TASK-spool-contract-006.MI6:** Delete `spools/agents/spool.edn` so former needs gating cannot become a silent no-op.
- **TASK-spool-contract-006.MI7:** Update RFC-018 so `Status` and `RFC-018.OUT1` read `Rejected` / mooted by `spool-contract`.
- **TASK-spool-contract-006.MI8:** Use only disposable `--workspace` values if any examples are smoke-tested. Never use or reload the canonical `.skein` world.
- **TASK-spool-contract-006.MI9:** After validation is green, run self-review: `strand op agent review <your-task-id> --members 1 --harness review-gpt --cwd <your-cwd> --spawned-by <your-run-id>`, await it, action legitimate findings, rerun validation, and record the review verdict in a task note.
- **TASK-spool-contract-006.MI10:** Commit the task's work on branch `spool-contract` with a conventional commit message.

## TASK-spool-contract-006.P3 Done when

- **TASK-spool-contract-006.DW1:** `rg "spool.edn|manifest|unmet-needs|provides-unloadable|coordinate-mismatch" docs spools devflow AGENTS.md CLAUDE.md` has only intentional historical/archive/spec-delta references, and those are recorded in the task note.
- **TASK-spool-contract-006.DW2:** `rg -n "Status|OUT1" devflow/rfcs/2026-07-03-spool-needs-shipped-namespaces.md` confirms both `Status` and `RFC-018.OUT1` read Rejected/mooted by `spool-contract`.
- **TASK-spool-contract-006.DW3:** Docs examples match the new `spools.edn` + `sync!` + `use!` contract and Maven-only policy.
- **TASK-spool-contract-006.DW4:** Cross-vendor self-review has no unactioned legitimate findings, validation was rerun after review, and the verdict is recorded.
- **TASK-spool-contract-006.DW5:** The task commit exists on branch `spool-contract`; set `status=implemented` only after validation/review are green.

## TASK-spool-contract-006.P4 Out of scope

- **TASK-spool-contract-006.OS1:** Do not merge feature deltas into root specs; coordinator fold-in owns `SC-PLAN-001.AA7`.
- **TASK-spool-contract-006.OS2:** Do not edit `/Users/ct/dev/projects/devflow.spool`; task 007 owns the external demo.
- **TASK-spool-contract-006.OS3:** Do not bump `.skein/spools.edn`, `deps.edn`, or `spools/devflow.md`; coordinator fold-in owns AA6.

## TASK-spool-contract-006.P5 References

- **TASK-spool-contract-006.REF1:** `SC-PLAN-001.PH6`, `SC-PLAN-001.AA3`, `SC-PLAN-001.AA4`, `SC-PLAN-001.OP3`, `SC-PLAN-001.V5`.
- **TASK-spool-contract-006.REF2:** `SC-PROP-001.S7`, `SC-PROP-001.S9`, `SC-PROP-001.I5`, `SC-PROP-001.I6`, `SC-PROP-001.I7`, `SC-PROP-001.I9`, `SC-PROP-001.I10`.
- **TASK-spool-contract-006.REF3:** `DELTA-repl-api-spool-contract-001.P3`; `SPEC-004.C94a@spool-contract`.
