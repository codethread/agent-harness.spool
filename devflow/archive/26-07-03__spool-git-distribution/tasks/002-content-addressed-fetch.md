# Task 2: Content-addressed fetch with tag verification

**Document ID:** `SGD-TASK-002`

## SGD-TASK-002.P1 Scope

Type: AFK

Make `sync!` materialize git spool coordinates into the content-addressed cache by shelling out to system git, with tag verification and loud per-spool sync outcomes, per [SGD-SPEC-DR-001.D1/D2](../specs/daemon-runtime.delta.md). Builds on task 1's normalized git entries.

## SGD-TASK-002.P2 Must implement exactly

- **SGD-TASK-002.MI1:** In `sync-approved-spool!` (src/skein/api/weaver/alpha.clj), `:kind :git` entries materialize before the existing add-libs path runs against `:root`. Cache hit (`<cache-base>/skein/spools/<sha>` exists, non-empty): no git invocation, no tag verification; result gains `:fetch :cached`.
- **SGD-TASK-002.MI2:** Cache miss: fetch with system git into a temp sibling directory, atomically rename into the cache path (a half-fetched tree must never be observable at the final path). Suggested sequence: `git init` tmp; `git fetch --depth 1 <url> <sha>`; `git checkout --detach FETCH_HEAD`; fall back to full fetch when the server rejects sha-addressed shallow fetch; remove `.git` from the materialized tree. Result gains `:fetch :fetched`.
- **SGD-TASK-002.MI3:** Any git failure (including missing git binary) → sync outcome status `:failed`, reason `:fetch-failed`, with exit code and trimmed stderr tail in the data. Never throw for a fetch problem.
- **SGD-TASK-002.MI4:** Tag verification only on cache miss and only when `:git/tag` present: `git ls-remote --tags <url> <tag>` (plus the peeled `<tag>^{}` form) must resolve to exactly the pinned sha, before the tree is renamed into place. Mismatch/absence → `:failed` / `:tag-mismatch` with expected sha, actual sha or nil, and tag; nothing installed into the cache.
- **SGD-TASK-002.MI5:** After materialization the effective `:root` (incl. `:deps/root` subpath) flows through the unchanged local-root path (deps.edn discovery, add-libs, classloader); missing/unreadable effective roots surface as the existing `:missing-root`/`:unreadable-root` outcomes. Successful status stays `:loaded`/`:already-available` with the extra `:fetch` key.
- **SGD-TASK-002.MI6:** Make the `root-paths` missing-deps.edn error kind-neutral (currently "Local root must contain deps.edn", src/skein/api/weaver/alpha.clj:169-177) so git-fetched roots fail with an honest message (TEN-003).

## SGD-TASK-002.P3 Done when

- **SGD-TASK-002.DW1:** Tests in `test/skein/spools_test.clj` build fixture repos in temp dirs with `git init`/commit/`git tag` (incl. one annotated tag), reference them by `file://` URL, and point the cache at a per-test temp dir by rebinding task 1's private cache-base fn (`alter-var-root`, mirroring test/skein/peers_test.clj:46-53) — not by real env mutation. The network is never touched.
- **SGD-TASK-002.DW2:** Covered cases: successful fetch+load of a spool with deps.edn+src; cache hit after deleting the fixture repo (proves no refetch); unknown sha → `:fetch-failed`; matching `:git/tag` loads; mismatching `:git/tag` → `:tag-mismatch` and cache path absent; `:deps/root` selects a monorepo subdir.
- **SGD-TASK-002.DW3:** `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test` fully green.

## SGD-TASK-002.P4 Out of scope

- **SGD-TASK-002.OS1:** Manifest handling, use! gating, docs.

## SGD-TASK-002.P5 References

- **SGD-TASK-002.REF1:** [specs/daemon-runtime.delta.md](../specs/daemon-runtime.delta.md) D1/D2; [plan](../spool-git-distribution.plan.md) PH2 + R1; `src/skein/api/weaver/alpha.clj` (`sync-approved-spool!`, `sync-failed`); `devflow/TENETS.md` TEN-003.
