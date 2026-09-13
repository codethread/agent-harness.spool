# Agent notes

- When iterating with the user, always run the feature iteration workflow loop:
  `strand workflow start <run-id> --workflow feature-iteration --params
  '{"brief":"…","cwd":"…"}'`, then drive it with `strand workflow next`. Taking
  the brief, changing the code, gating on `make quality`, reviewing the diff on a
  read-only cross-vendor seat, and presenting back are encoded there — read them
  with `strand workflow show feature-iteration`, not from this file.
- Workflow definitions live in `.millstrand/config/workflows`, one file each, loaded
  and registered by `.millstrand/config/workflows.clj`.
- Root `deps.edn` pins Millstrand and Millhouse dependencies to immutable Git
  coordinates; keep those coordinates aligned across every alias and the
  checked-in `.millstrand/deps.edn` workspace config.
- Never run `make install` while developing or testing this repository.
- Kill spawned processes by exact PID only; never use pattern kills.
- Shared-spool publishing, activation, override, and test conventions live in
  `../skein-src/docs/spools/writing-shared-spools.md`.
- Working with users: claim a kanban card first; run `strand prime kanban`.
- Delegating: run `strand prime agent`; use tracked Harnesses agent runs, not
  native workflow subagents. List seats with `strand agent list`; shared
  routing policy and reviewer lenses live in the Codethread bootstrap. Workflow
  gates use `:agent`, and stalled runs are retried with `strand agent retry`.
- This repository is a deprecated implementation archive. Keep its shipped
  library source and historical tests intact; the `.millstrand` workspace must
  activate Harnesses and shared Codethread config instead of the old stack.
- Recover runs with `strand list --query agent-failures` and
  `strand agent logs <run-id> --tail 80`.

<!-- mill:millstrand-prime -->
## Millstrand / strand

This repo uses Millstrand strands to track work. Orientation ships in the `mill` CLI:

Start with `strand --help`. Run `mill prime millstrand` on demand when building
on this repo's `.millstrand/` config or spools.
<!-- /mill:millstrand-prime -->
