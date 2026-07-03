# Skein Devflow Spool

`skein.spools.devflow` has moved to the external git-distributed spool repo:
[`codethread/devflow.spool`](https://github.com/codethread/devflow.spool).

The contract doc now lives there: [`devflow.md`](https://github.com/codethread/devflow.spool/blob/de735e74b4bae2f9e9f5e005033c969ad103749c/devflow.md).
This Skein checkout consumes the spool by the sha-pinned coordinate in
`.skein/spools.edn`; the test JVM pins the same sha in `deps.edn`. Keep those
two pins synchronized.
