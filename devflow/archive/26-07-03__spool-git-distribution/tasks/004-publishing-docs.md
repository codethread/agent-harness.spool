# Task 4: Publishing and consuming docs

**Document ID:** `SGD-TASK-004`

## SGD-TASK-004.P1 Scope

Type: AFK

Document publishing and consuming git-distributed spools, consistent with the shipped implementation and the feature spec deltas. Docs only — no code.

## SGD-TASK-004.P2 Must implement exactly

- **SGD-TASK-004.MI1:** `docs/writing-shared-spools.md`: new section on publishing a shared spool — git distribution (SHA-pinned approval, `:git/tag` as verified label, `:deps/root` for monorepos), authoring `spool.edn` (`:coordinate`, `:provides`, `:needs` + `:suggest`, `:docs`), and the explicit no-transitive-fetch consent loop (unmet need → agent proposes `spools.edn` addition → user approves → re-`sync!`). Match the doc's existing tone and composability-over-ergonomics framing.
- **SGD-TASK-004.MI2:** `spools/README.md`: brief pointer to the new publishing guidance where the approved-local-root flow is described.
- **SGD-TASK-004.MI4:** Document the local dev-override recipe: same coordinate as `{:git/url … :git/sha …}` in shared `spools.edn`, overridden by `{:local/root …}` in gitignored `spools.local.edn` (existing overlay-by-coordinate semantics), so an author develops against their checkout while consumers stay on the pinned sha. Note that the manifest is read from whichever effective root wins, and that `:deps/root` is git-only because a local path already points anywhere directly.
- **SGD-TASK-004.MI3:** Verify examples against the actual implemented behavior in `src/skein/api/weaver/alpha.clj` and the tests — do not document from the spec deltas alone.

## SGD-TASK-004.P3 Done when

- **SGD-TASK-004.DW1:** Both docs updated; every EDN example in the new sections is syntactically valid and matches implemented grammar (spot-check against `test/skein/spools_test.clj` cases).
- **SGD-TASK-004.DW2:** `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test` still green (no code touched).

## SGD-TASK-004.P4 Out of scope

- **SGD-TASK-004.OS1:** Root spec merging (happens at feature finish), CLAUDE.md/README.md beyond MI2, CLI docs.

## SGD-TASK-004.P5 References

- **SGD-TASK-004.REF1:** Both spec deltas; [plan](../spool-git-distribution.plan.md) PH4; `docs/writing-shared-spools.md`; `spools/README.md`.
