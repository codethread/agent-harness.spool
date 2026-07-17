# Agent notes

- Validate with `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:test` and
  `PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:format` from the repo root.
- Root `deps.edn` deliberately targets the sibling `../skein-src` checkout,
  including its off-classpath `spools/workflow/src` root.
- Never run `make install` while developing or testing this repository.
- Kill spawned processes by exact PID only; never use pattern kills.
- Shared-spool publishing, activation, override, and test conventions live in
  `../skein-src/docs/spools/writing-shared-spools.md`.
