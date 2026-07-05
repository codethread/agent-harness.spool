# Task 1: Parser :subcommands in parse/explain with shared validator

**Document ID:** `TASK-ArgspecSub-001`

## TASK-ArgspecSub-001.P1 Scope

Type: AFK

Extend `src/skein/api/cli/alpha.clj` with the `:subcommands` arg-spec concept per SPEC-003-D004 (devflow/feat/argspec-subcommands/specs/repl-api.delta.md). Pure accretion: flat arg-specs must parse and explain byte-for-byte as before.

## TASK-ArgspecSub-001.P2 Must implement exactly

- **TASK-ArgspecSub-001.MI1:** Arg-spec accretes optional `:subcommands`: map of subcommand name (string) to nested arg-spec with its own `:doc`, `:flags`, `:positionals` (SPEC-003-D004.C1).
- **TASK-ArgspecSub-001.MI2:** One shared structural validator (public or clearly-named private fn with a public entry) enforcing: one level only (nested `:subcommands` fails), no top-level `:flags`/`:positionals` alongside `:subcommands`, no nested flag/positional named `subcommand` (reserved). `parse` and `explain` both consult it; expose it so the registry can call it earlier (task 2). Specs without `:subcommands` are not newly validated.
- **TASK-ArgspecSub-001.MI3:** `parse` routing (SPEC-003-D004.C2): first argv token selects the nested spec; remaining argv parses against it; result is the nested parsed map merged with `:subcommand` (matched name string). Missing or unknown first token throws structured `ex-info` carrying `:op`, the offending token, and available subcommand names.
- **TASK-ArgspecSub-001.MI4:** Payload references and `:parse :json`/`:jsonl` work unchanged inside nested specs, including the unused-payload loud rule evaluated against the routed subcommand's consumption (SPEC-003-D004.C3).
- **TASK-ArgspecSub-001.MI5:** `explain` renders `:subcommands` as JSON-safe data: per subcommand name, doc, flags, positionals (SPEC-003-D004.C4). Decide and keep a stable key (e.g. `:subcommands`); flat specs' explain output is unchanged.
- **TASK-ArgspecSub-001.MI6:** Unit tests (extend the existing `skein.api.cli.alpha` test namespace; find via `rg "api.cli" test/`): routing happy path per arg kind, merged `:subcommand` key, reserved-name loud failure, structure loud failures (nested subcommands, mixed top-level args), missing/unknown token errors carrying available names, payload ref + json parse inside a nested spec, explain rendering, and a flat-spec regression case.

## TASK-ArgspecSub-001.P3 Done when

- **TASK-ArgspecSub-001.DW1:** `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test` green.
- **TASK-ArgspecSub-001.DW2:** Ns docstring updated to describe `:subcommands` in the arg-spec shape documentation; parser stays pure (no registry/runtime coupling, no module state).

## TASK-ArgspecSub-001.P4 Out of scope

- **TASK-ArgspecSub-001.OS1:** Registry/`register-op!` integration (task 2), batteries migration (task 3), any Go/`cli/` change, any `<op> help` invocation convention (separate feature).

## TASK-ArgspecSub-001.P5 References

- **TASK-ArgspecSub-001.REF1:** devflow/feat/argspec-subcommands/specs/repl-api.delta.md (contract), proposal.md, argspec-subcommands.plan.md A1/PH1, devflow/TENETS.md TEN-003/TEN-004.
