# Agent notes

## Working here

- Run `strand prime kanban`, claim a feature card, and use its recorded worktree.
- Never edit `main` or push directly to `main`; feature-branch pushes are expected.
- Inspect `strand workflow show land` and `strand prime merge-queue`, then drive
  shared `land` for quality, one basic review, FIFO merge, card completion, and
  branch/worktree cleanup.

## Repository policy

- Workflow definitions live in `.millstrand/config/workflows`, one file each,
  loaded and registered by `.millstrand/config/workflows.clj`.
- Preserve the archived library/root/test/lint dependency coordinates. The
  active `.millstrand` workspace independently pins current Harnesses and shared
  Codethread bootstrap releases; validate it with disposable
  published-coordinate startup.
- Never run `make install` while developing or testing this repository.
- Kill spawned processes by exact PID only; never use pattern kills.
- Shared-spool publishing, activation, override, and test conventions live in
  `../skein-src/docs/spools/writing-shared-spools.md`.
- Delegate with tracked Harnesses agent runs. List seats with
  `strand agent list`; Workflow gates use `:agent`, and stalled runs are retried
  with `strand agent retry`.
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
