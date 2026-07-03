# Backlog spool

The backlog spool is a small repo-local convention that keeps `BACKLOG.md` as the human-visible feature queue while Skein strands hold executable state and audit history.

## Model

Each Markdown checkbox row points at one backlog item strand:

```md
- [ ] `3rwu8` Some feature idea, maybe referencing an RFC or feature folder
```

The strand carries:

| Attribute | Meaning |
| --- | --- |
| `backlog/item` | String `"true"` for backlog item strands. |
| `backlog/status` | `pending`, `claimed`, `done`, or another explicit outcome such as `abandoned`. |
| `backlog/file` | Usually `BACKLOG.md`. |
| `kind` | `feature` for item roots. |

Feature plans, devflow runs, review strands, and task DAGs should hang under the backlog item strand with `parent-of` edges. The backlog item is the audit root.

## CLI op

Install registers one operation:

```sh
strand op backlog about
strand op backlog add "Feature idea" [--body "Longer context"] [--source devflow/rfcs/...]
strand op backlog next
strand op backlog claim <id> [--owner agent] [--branch feature-branch] [--worktree /path]
strand op backlog finish <id> [--outcome done|abandoned]
strand op backlog sync
```

`next` returns the first unchecked, active, `pending` backlog item in file order. `claim` marks it `claimed` but leaves the checkbox unchecked. `finish` closes the strand and checks the row. `sync` fails loudly if the Markdown file and graph disagree.

## Queries

Install also registers:

- `backlog-items` — all backlog item strands.
- `backlog-unstarted` — active backlog items with `backlog/status=pending`.
