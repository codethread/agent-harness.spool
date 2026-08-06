# Agent notes

- When iterating with the user, always run the feature iteration workflow loop:
  `strand workflow start <run-id> --workflow feature-iteration --params
  '{"brief":"…","cwd":"…"}'`, then drive it with `strand workflow next`. Taking
  the brief, changing the code, gating on `make quality`, reviewing the diff on a
  read-only cross-vendor seat, and presenting back are encoded there — read them
  with `strand workflow show feature-iteration`, not from this file.
- Workflow definitions live in `.skein/config/workflows`, one file each, loaded
  and registered by `.skein/config/workflows.clj`.
- Root `deps.edn` deliberately targets the sibling `../millstrand` checkout,
  including its off-classpath `spools/workflow/src` root.
- Never run `make install` while developing or testing this repository.
- Kill spawned processes by exact PID only; never use pattern kills.
- Shared-spool publishing, activation, override, and test conventions live in
  `../millstrand/docs/spools/writing-shared-spools.md`.
- Working with users: claim a kanban card first; run `strand kanban prime`.
- Delegating: run `strand prime agent`; use tracked agent runs, not
  harness-native subagents. List seats with `strand agent harnesses`; routing
  policy lives in `.skein/config/harnesses.clj`.
- Recover runs with `strand list --query agent-failures` and
  `strand agent logs <run-id> --tail 80`.

<!-- mill:millstrand-prime -->
## Millstrand / strand

This repo uses Millstrand strands to track work. Orientation ships in the `mill` CLI:

- `mill strand prime` — the day-to-day strand workflow; run it before multi-step work.
- `mill millstrand prime` — read on demand, only when building on this repo's `.skein/` config or spools.
<!-- /mill:millstrand-prime -->
