# Agent notes

- Validate with `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test` and
  `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:format` from the repo root.
- Run `make quality` for the complete local gate: format, clj-kondo, Splint,
  repository conventions, reflection warnings, and the cold test suite.
- Root `deps.edn` deliberately targets the sibling `../skein-src` checkout,
  including its off-classpath `spools/workflow/src` root.
- Never run `make install` while developing or testing this repository.
- Kill spawned processes by exact PID only; never use pattern kills.
- Shared-spool publishing, activation, override, and test conventions live in
  `../skein-src/docs/spools/writing-shared-spools.md`.
- Working with users: claim a kanban card first; run `strand kanban prime`.
- Delegating: run `strand prime agent`; use tracked agent runs, not
  harness-native subagents. List seats with `strand agent harnesses`; routing
  policy lives in `.skein/harnesses.clj`.
- Recover runs with `strand list --query agent-failures` and
  `strand agent logs <run-id> --tail 80`.

<!-- mill:skein-prime -->
## Skein / strand

This repo uses Skein strands to track work. Orientation ships in the `mill` CLI:

- `mill strand prime` — the day-to-day strand workflow; run it before multi-step work.
- `mill skein prime` — read on demand, only when building on this repo's `.skein/` config or spools.
<!-- /mill:skein-prime -->
