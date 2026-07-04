# Task 4: Maven-only deps policy

**Document ID:** `TASK-spool-contract-004`

## TASK-spool-contract-004.P1 Scope

Type: AFK

Implement the uniform Maven-only `deps.edn :deps` policy for every approved spool root and wire allowed Maven dependencies through sync-time runtime `add-libs`.

## TASK-spool-contract-004.P2 Must implement exactly

- **TASK-spool-contract-004.MI1:** Read `devflow/TENETS.md`, `devflow/PHILOSOPHY.md`, `devflow/feat/spool-contract/proposal.md`, `devflow/feat/spool-contract/spool-contract.plan.md`, and both `devflow/feat/spool-contract/specs/*.delta.md` before editing or validating.
- **TASK-spool-contract-004.MI2:** Owned files: `src/skein/api/weaver/alpha.clj` and `test/skein/spools_test.clj`. This task is sequenced after tasks 001 and 003 because it depends on the spike verdict and shares files with tasks 002/003.
- **TASK-spool-contract-004.MI3:** Replace `reject-unapproved-tools-deps!` / `shared-source-local-spool?` asymmetry with one policy applying to approved `:git` and `:local` spool roots from either `spools.edn` or `spools.local.edn` per `SPEC-004.C94a@spool-contract`.
- **TASK-spool-contract-004.MI4:** Allow only Maven coordinate maps containing `:mvn/version`; allow `:exclusions`, `:classifier`, and `:extension` alongside `:mvn/version` per `SPEC-004.C94a.1@spool-contract`.
- **TASK-spool-contract-004.MI5:** Fail sync before `add-libs` for source-bearing/path coordinates including `:git/url`, `:git/sha`, and `:local/root`; mutable versions ending `-SNAPSHOT` or equal to `RELEASE`/`LATEST`; and top-level `:mvn/repos` or `:mvn/local-repo`, per `SPEC-004.C94a.2@spool-contract`.
- **TASK-spool-contract-004.MI6:** Resolve allowed Maven dependencies during `sync!` through `clojure.repl.deps/add-libs`; ignore aliases and other non-rejected top-level keys outside `:paths` and `:deps` per `SPEC-004.C94a.3@spool-contract`.
- **TASK-spool-contract-004.MI7:** Rewrite the four dependency-consent tests from the C94a work and add cases for local-overlay tightening, mutable versions, repo redirection, allowed Maven refinement keys, ignored aliases, and per-spool dependency-policy failure shape.
- **TASK-spool-contract-004.MI8:** Use only disposable `--workspace` values in any weaver validation. Never use or reload the canonical `.skein` world.
- **TASK-spool-contract-004.MI9:** After validation is green, run self-review: `strand op agent review <your-task-id> --members 1 --harness review-gpt --cwd <your-cwd> --spawned-by <your-run-id>`, await it, action legitimate findings, rerun validation, and record the review verdict in a task note.
- **TASK-spool-contract-004.MI10:** Commit the task's work on branch `spool-contract` with a conventional commit message.

## TASK-spool-contract-004.P3 Done when

- **TASK-spool-contract-004.DW1:** Focused tests pass with `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test --shard A` (the repo test runner has no --focus flag; shard A runs skein.spools-test, see test/skein/test_runner.clj add-libs-shards).
- **TASK-spool-contract-004.DW2:** Tests demonstrate both shared and local-overlay approved spool roots follow the same Maven-only rule.
- **TASK-spool-contract-004.DW3:** Cross-vendor self-review has no unactioned legitimate findings, validation was rerun after review, and the verdict is recorded.
- **TASK-spool-contract-004.DW4:** The task commit exists on branch `spool-contract`; set `status=implemented` only after validation/review are green.

## TASK-spool-contract-004.P4 Out of scope

- **TASK-spool-contract-004.OS1:** Do not add the live daemon/runtime end-to-end Maven test in `test/skein/runtime_deps_test.clj`; task 005 owns that separate surface.
- **TASK-spool-contract-004.OS2:** Do not update docs or delete manifests; task 006 owns docs/orphan cleanup.
- **TASK-spool-contract-004.OS3:** Do not edit `/Users/ct/dev/projects/devflow.spool`; task 007 owns the external demo.

## TASK-spool-contract-004.P5 References

- **TASK-spool-contract-004.REF1:** `SC-PLAN-001.PH4`, `SC-PLAN-001.CM3`, `SC-PLAN-001.CM4`, `SC-PLAN-001.OP1`, `SC-PLAN-001.OP2`.
- **TASK-spool-contract-004.REF2:** `SC-PROP-001.S4`, `SC-PROP-001.S5`, `SC-PROP-001.S6`, `SC-PROP-001.D5`, `SC-PROP-001.D6`, `SC-PROP-001.D7`, `SC-PROP-001.V1`.
- **TASK-spool-contract-004.REF3:** `SPEC-004.C94a@spool-contract`, `SPEC-004.C94a.1@spool-contract`, `SPEC-004.C94a.2@spool-contract`, `SPEC-004.C94a.3@spool-contract`.
