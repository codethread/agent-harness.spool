# Task 2: Registration-time validation and help detail rendering

**Document ID:** `TASK-ArgspecSub-002`

## TASK-ArgspecSub-002.P1 Scope

Type: AFK

Wire the parser's shared `:subcommands` validator into op registration and prove the help projection renders subcommands, per SPEC-004-D004 (devflow/feat/argspec-subcommands/specs/daemon-runtime.delta.md).

## TASK-ArgspecSub-002.P2 Must implement exactly

- **TASK-ArgspecSub-002.MI1:** `skein.api.weaver.alpha` `register-op!`/`replace-op!` call the shared validator **only when the supplied `:arg-spec` declares `:subcommands`** (SPEC-004-D004.C3). Malformed subcommand structure fails loudly at registration naming the op and violation. Arg-specs without `:subcommands` — including opaque non-parser metadata maps (see existing opaque arg-spec test in `test/skein/weaver_test.clj`) — register exactly as today.
- **TASK-ArgspecSub-002.MI2:** Verify `op!` needs no change for routing (parse already runs when `:arg-spec` present) and `op-detail`/`op-help-handler` need no change beyond `explain` (task 1) — add integration tests rather than assume: register a test op with `:subcommands`, invoke it through `op!` (happy path + unknown subcommand becomes a loud parse-phase error, handler not called), and assert `help <op>` detail includes the subcommand rendering (SPEC-004-D004.C1/C2).
- **TASK-ArgspecSub-002.MI3:** Update the SPEC-004.C63 doc surface only if code comments/docstrings in `skein.api.weaver.alpha` describe the arg-spec metadata; root spec merge happens at feature finish, not in this task.

## TASK-ArgspecSub-002.P3 Done when

- **TASK-ArgspecSub-002.DW1:** `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test` green, including new registration-failure and help-detail tests.

## TASK-ArgspecSub-002.P4 Out of scope

- **TASK-ArgspecSub-002.OS1:** Batteries migration (task 3); root spec merges; Go changes.

## TASK-ArgspecSub-002.P5 References

- **TASK-ArgspecSub-002.REF1:** devflow/feat/argspec-subcommands/specs/daemon-runtime.delta.md, specs/cli.delta.md, plan A2/PH1.
