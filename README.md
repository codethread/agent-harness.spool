# Todo Graph MVP

A small Clojure todo graph tool using `next.jdbc` + SQLite.

It exists to give coding agents and humans a lightweight local task graph:

- tasks are stored in SQLite;
- open-ended task attributes are JSON stored as `TEXT` and queried with SQLite JSON1;
- `task_edges` stores acyclic graph relationships using `depends-on`, `related-to`, `parent-of`, or `supersedes`;
- agents can use a scriptable CLI or a compact REPL API;
- humans can still use the basic TUI.

For contributor, debugging, and implementation guidance, see [AGENTS.md](./AGENTS.md). For durable behavior contracts, see the [Devflow spec index](./devflow/README.md#root-specs).

## Requirements

- Clojure CLI
- Java / OpenJDK
- SQLite, provided by the `org.xerial/sqlite-jdbc` dependency at runtime

On this system, Homebrew OpenJDK may need to be put on PATH:

```sh
PATH="/opt/homebrew/opt/openjdk/bin:$PATH" clojure -M:smoke
```

## Quickstart

Run the unit tests and smoke demo:

```sh
clojure -M:test
clojure -M:smoke
```

Use the agent CLI:

```sh
DB=/tmp/todo-agent.sqlite
clojure -M:todo --db "$DB" init
design=$(clojure -M:todo --db "$DB" add "Sketch model" --attr status=done --attr priority=high)
docs=$(clojure -M:todo --db "$DB" add "Write docs" --attr status=todo --attr owner=agent --link depends-on:$design)
clojure -M:todo --db "$DB" --format edn ready

printf '[{:ref design :title "Design" :attributes {:status "done"}} {:ref docs :title "Docs" :edges [{:type "depends-on" :to design}]}]' \
  | clojure -M:todo --db "$DB" --format edn batch
```

Use the REPL helpers:

```clojure
(require '[todo.repl :refer :all])
(open! "agent.sqlite")
(init!)
(def design (:id (task! "Sketch model" {:status "done" :priority "high"})))
(def docs (:id (task! "Write docs" {:status "todo" :owner "agent"})))
(depends! docs design)
(ready)
```

Run the TUI:

```sh
clojure -M:run
# or choose a database file
clojure -M:run my-todos.sqlite
```

## Data model

The durable data contract is specified in [Task Model](./devflow/specs/task-model.md). At a high level:

- tasks have a generated unique text id, a title, and open-ended JSON object attributes;
- task edges connect two tasks with a canonical edge type and open-ended JSON object attributes;
- edge writes reject unsupported types and directed cycles;
- `depends-on` edges define readiness semantics;
- task completion is represented by the conventional `status` attribute value `done`.

## Development

See:

- [AGENTS.md](./AGENTS.md) for contributor/debug/build guidance;
- [Task Model](./devflow/specs/task-model.md) for data semantics;
- [CLI Surface](./devflow/specs/cli.md) for full command vocabulary;
- [REPL API](./devflow/specs/repl-api.md) for full helper vocabulary.
