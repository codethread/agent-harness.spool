# Skein 🧶

Skein is a small weaver-backed strand graph for coding agents and humans. It stores strands locally in SQLite, exposes a thin JSON-only `strand` CLI for scripts, and keeps richer customization in trusted weaver config and Clojure REPL workflows.

Use it to:

- Track local strand graphs without a hosted service.
- Let coding agents create, update, query, and inspect structured strand state.
- Attach flexible JSON attributes to strands and edges for userland workflows.
- Keep custom queries, weave patterns, graph helpers, views, and runtime libraries in trusted Clojure config instead of the low-privilege CLI.

## Quick start

Install the `strand` and `mill` commands from this checkout, start `mill`, then initialize a repo-local `.skein` world in the Git repository you want to work in:

```sh
go install ./cli/cmd/strand ./cli/cmd/mill
mill start
strand init --source /path/to/skein-src
strand weaver start
```

Without `--config-dir`, `strand` selects the current Git repository root. Outside Git, no-flag commands fail loudly instead of creating an accidental cwd world or using a global default.

Then use it from another terminal:

```sh
strand add "Sketch strand model" --state closed --attr example_outcome=sketched
strand add "Write docs" --attr owner=agent
strand list
strand ready
```

The CLI emits JSON for all strand/weaver commands. The `repl` is where Skein really shines (see [docs](./docs/getting-started.md) for more):

```sh
strand weaver repl
```

When finished:

```sh
strand weaver stop
```

### Isolated weavers

For agent/testing work, prefer an explicit disposable world:

```sh
world=$(mktemp -d)
printf '{"configFormat":"alpha","source":"%s"}\n' "$PWD" | jq . > "$world/config.json"
```

Start mill and the weaver:

```sh
mill start
strand --config-dir "$world" weaver start
```

Then use it from another terminal:

```sh
strand --config-dir "$world" add "Sketch strand model" --state closed --attr example_outcome=sketched
```

Explicit `--config-dir <dir>` worlds keep trusted config in that directory. Runtime metadata, sockets, and SQLite data live under mill-owned XDG state paths keyed by the selected config identity.

## Data model

Skein stores:

- strands with generated text ids, titles, lifecycle `state`, timestamps, and JSON attributes;
- strand edges with an open relation name, direction, and JSON attributes;
- declared acyclic operational relations: `depends-on`, `parent-of`, and `supersedes`.

`state` is the only core lifecycle field. Active strands participate in readiness; closed and replaced strands are retained; destructive cleanup uses explicit `burn`. `strand supersede <old-id> <replacement-id>` records `replacement --supersedes--> old`, marks the old strand `replaced`, and rewires direct dependents. Outcomes, categories, temporary markers, or other workflow concepts are user attributes chosen by your world, not built-in fields.

## Runtime customization

Named queries, weave patterns, weaver-memory views, and runtime libraries are loaded into the selected Skein world, then consumed by helpers or by small CLI commands such as `list --query <name>` and `weave --pattern <name>`.

Fresh `strand init` creates `.skein/init.clj`, `.skein/libs.edn`, and `.skein/.gitignore` for shared repo config. It also writes local `.skein/config.json` when it can resolve the Skein source from `--source`, `SKEIN_SOURCE`, or the current Skein checkout. Keep shared workflow config in committed `init.clj`/`libs.edn`; keep personal workflow libraries in gitignored `init.local.clj`/`libs.local.edn`.

Use `strand weaver repl` for trusted interactive work and `(skein.libs.alpha/reload!)` to hot-reload `init.clj` followed by `init.local.clj`.

## Documentation

- [Skein user reference](./docs/skein.md)
- [Getting started](./docs/getting-started.md)
- [Clojure crash course](./docs/clojure-crash-course.md)
- [Devflow specs](./devflow/specs/)
