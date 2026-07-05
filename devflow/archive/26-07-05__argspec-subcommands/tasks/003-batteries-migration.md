# Task 3: Migrate batteries query/pattern to declared subcommands

**Document ID:** `TASK-ArgspecSub-003`

## TASK-ArgspecSub-003.P1 Scope

Type: AFK

Migrate the batteries `query` and `pattern` ops from the fake required `subcommand` string positional to declared `:subcommands`, proving the primitive end to end (plan PH2).

## TASK-ArgspecSub-003.P2 Must implement exactly

- **TASK-ArgspecSub-003.MI1:** In `spools/src/skein/spools/batteries.clj`, rewrite `query-arg-spec` and `pattern-arg-spec` to `:subcommands` `{"list" ..., "explain" ...}` — `explain` carries its required `name` positional, `list` takes none. Valid usage (`strand query list`, `strand query explain <name>`) is unchanged.
- **TASK-ArgspecSub-003.MI2:** Handlers keep dispatching on `:subcommand` from `:op/args` (merged shape preserves this). Delete the handler-owned unknown/missing-subcommand branches now unreachable — parser owns those failures (SPEC-003-D004.C2); do not leave dead fallback code (TEN-003).
- **TASK-ArgspecSub-003.MI3:** Update `test/skein/spools/batteries_test.clj` unknown-subcommand cases to assert the parser-phase structured error (available names present) instead of handler errors; add a `strand help query`-level assertion via the op registry that subcommands render.
- **TASK-ArgspecSub-003.MI4:** Update `spools/batteries.md` where it documents query/pattern op shapes or their errors.

## TASK-ArgspecSub-003.P3 Done when

- **TASK-ArgspecSub-003.DW1:** `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test` green.
- **TASK-ArgspecSub-003.DW2:** `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:smoke` green (smoke exercises the strand dispatcher against batteries ops).
- **TASK-ArgspecSub-003.DW3:** `git status --short` shows no generated SQLite/runtime artifacts.

## TASK-ArgspecSub-003.P4 Out of scope

- **TASK-ArgspecSub-003.OS1:** kanban/agents spool migration (follow-up feature), root spec merges, Go changes.

## TASK-ArgspecSub-003.P5 References

- **TASK-ArgspecSub-003.REF1:** specs/repl-api.delta.md SPEC-003-D004.C2, plan A3/PH2/CM1, spools/batteries.md.
