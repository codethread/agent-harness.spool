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
  checked-in `.millstrand/spools.edn` workspace config.
- Never run `make install` while developing or testing this repository.
- Kill spawned processes by exact PID only; never use pattern kills.
- Shared-spool publishing, activation, override, and test conventions live in
  `../skein-src/docs/spools/writing-shared-spools.md`.
- Working with users: claim a kanban card first; run `strand prime kanban`.
- Delegating: run `strand prime agent`; use tracked agent runs, not
  harness-native subagents. List seats with `strand agent harnesses`; shared
  routing policy lives in `ct.spools.codethread.agents`, with this workspace's
  delegation contracts in `.millstrand/config/delegation_contracts.clj`.
- Recover runs with `strand list --query agent-failures` and
  `strand agent logs <run-id> --tail 80`.

<!-- mill:millstrand-prime -->
## Millstrand / strand

This repo uses Millstrand strands to track work. Orientation ships in the `mill` CLI:

Start with `strand --help`. Run `mill prime millstrand` on demand when building
on this repo's `.millstrand/` config or spools.
<!-- /mill:millstrand-prime -->
