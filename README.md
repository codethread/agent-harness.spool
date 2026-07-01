# Skein 🧶 Give your agents a lisp

> skein: A continuous length of thread or wool wound into a loose, long twist so it doesn't tangle

Skein is a small, local graph for coding agents and the humans working with them. It stores everything on your machine in SQLite and keeps its command-line surface small and script-friendly — its everyday commands emit JSON. Richer customization lives in trusted config and a live Clojure REPL, not in the CLI.

A few terms you'll see throughout:

- **strand** — one record in your graph: a title, a lifecycle `state`, and an open-ended map of JSON `attributes`.
- **weaver** — the long-lived local process that owns the database and runtime state.
- **mill** — the local supervisor you start once; it routes each command to the right weaver.
- **`strand` CLI** — a thin, JSON-only control surface for scripts and agents.
- **REPL** — a live, trusted Clojure connection to the weaver for customization and exploration.
- **world** — one isolated Skein setup, chosen by config directory (a repo's `.skein`, or an explicit `--config-dir`).

Use it to:

- Track local strand graphs without a hosted service.
- Let coding agents create, update, query, and inspect structured strand state.
- Attach flexible JSON attributes to strands and edges for userland workflows.
- Keep custom queries, weave patterns, graph helpers, views, and runtime libraries in trusted Clojure config instead of the low-privilege CLI.

## Quick start

Install from this checkout, then start `mill` once:

```sh
make install
mill start
```

In the Git repo you want to work in, create a world and start its weaver:

```sh
mkdir -p ~/learn-skein
cd ~/learn-skein
git init
strand init          # create this repo's .skein config
strand weaver start  # boot the weaver for this world
```

Add a few strands, including one that depends on another:

```sh
strand add "initial"
ID=$(strand add "hello" | jq -r '.id')      # capture the new strand's id
strand add "world" --edge depends-on:${ID}  # "world" depends on "hello"
```

Inspect them — these commands emit JSON:

```sh
strand list   # every strand
strand ready  # only strands with nothing blocking them
```

Open a live REPL or stop the weaver when you're done:

```sh
strand weaver repl  # live Clojure REPL (optional)
strand weaver stop
```

By default (no `--config-dir`), `strand` finds the canonical Git repository root and uses that repo as its world, so linked worktrees of the same repository share one world. Outside a supported Git layout, no-flag commands fail loudly rather than guess — they won't silently create a world from your current directory or fall back to a global default. See [Getting started](./docs/getting-started.md) for the full walkthrough.

### Isolated weavers

For agent or testing work, prefer an explicit disposable world instead of a repo's default one:

```sh
world=$(mktemp -d)
```

Pass `--config-dir "$world"` on **every** command that should target it — the flag is not remembered between commands. With `mill` running:

```sh
strand --config-dir "$world" init
strand --config-dir "$world" weaver start
```

Then use it from another terminal:

```sh
strand --config-dir "$world" add "Sketch strand model" --state closed --attr example_outcome=sketched
```

An explicit `--config-dir <dir>` world keeps trusted config in that directory. Runtime metadata, sockets, and SQLite data live under mill-owned XDG state paths, keyed to the selected config.

## Data model

Skein stores:

- **strands** — a generated text id, title, lifecycle `state`, timestamps, and JSON attributes;
- **edges** — an open relation name, a direction, and JSON attributes;
- three **declared acyclic relations** for structure: `depends-on`, `parent-of`, and `supersedes`.

`state` is the only core lifecycle field. Active strands participate in readiness; closed and replaced strands are retained; deleting is explicit, via `burn`.

Superseding is a first-class move: `strand supersede <old-id> <replacement-id>` records `replacement --supersedes--> old`, marks the old strand `replaced`, and rewires its direct dependents onto the replacement.

Everything else — outcomes, categories, temporary markers, priorities — lives in attributes your world chooses, not in built-in fields.

## Runtime customization

The CLI stays thin on purpose. Richer behavior — named queries, weave patterns, weaver-memory views, and trusted runtime libraries — is loaded into your world, then consumed by helpers or by small CLI commands such as `list --query <name>` and `weave --pattern <name>`.

Two kinds of code can extend the weaver:

- **Built-in `skein.*.alpha` namespaces** — privileged helpers shipped with Skein.
- **Your own trusted libraries** — Clojure loaded through config, approved local roots, or live REPL work.

Fresh `strand init` creates a repo's config files: `.skein/init.clj`, `.skein/libs.edn`, `.skein/.gitignore`, and a local `.skein/config.json` alpha marker. Commit the shared files (`init.clj`, `libs.edn`) for behavior the whole repo gets; keep personal libraries in gitignored `init.local.clj` / `libs.local.edn`.

`strand init` does not persist a source path. Mill resolves the Skein source for weaver/REPL launch from `SKEIN_SOURCE`, the install-time source, or a canonical Skein checkout as the working directory.

Use `strand weaver repl` to attach directly to the running weaver's nREPL for trusted interactive work, and `(skein.runtime.alpha/reload!)` to hot-reload `init.clj` followed by `init.local.clj`.

## Documentation

- [Skein user reference](./docs/skein.md)
- [Getting started](./docs/getting-started.md)
- [Clojure crash course](./docs/clojure-crash-course.md)
- [Devflow specs](./devflow/specs/)
