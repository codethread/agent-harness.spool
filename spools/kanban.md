# Skein Kanban Spool

`skein.spools.kanban` has moved to the external git-distributed spool repo: `codethread/kanban.spool`.

The contract doc and cookbook now live there ([`kanban.md`](https://github.com/codethread/kanban.spool/blob/main/kanban.md), [`kanban.cookbook.md`](https://github.com/codethread/kanban.spool/blob/main/kanban.cookbook.md)). This Skein checkout consumes the spool by the coordinate in `.skein/spools.edn`; the test JVM consumes the same root in `deps.edn`. Keep those two coordinates synchronized.

The spool requires `skein.spools.devflow` (the `kanban card` devflow join), so its activation is ordered after devflow's in `.skein/init.clj`.
