---
name: atom
description: Query and operate the Atom todo task database from agents, scripts, and the REPL.
---

# Atom task queries

Atom supports a small EDN query DSL for filtering tasks. Use it with `list` and `ready`, or load named queries in the REPL.

## CLI

Ad hoc query:

```sh
clojure -M:todo --db todo.sqlite --format edn list --where '[:= [:attr :owner] "agent"]'
```

Named parameterized query:

```clojure
;; queries.edn
{owned-open
 {:params [:owner]
  :where [:and
          [:= :status "todo"]
          [:= [:attr :owner] [:param :owner]]]}}
```

```sh
clojure -M:todo --db todo.sqlite --format edn ready \
  --query-file queries.edn \
  --query owned-open \
  --param owner=agent
```

## REPL

```clojure
(require '[todo.repl :refer :all])
(open! "todo.sqlite")
(load-queries! "queries.edn")
(query 'owned-open {:owner "agent"})
(ready 'owned-open {:owner "agent"})
```

Define a query at runtime:

```clojure
(defquery! 'high-priority [:= [:attr :priority] "high"])
(query 'high-priority)
```

## Query forms

Queries are EDN vectors. Supported fields:

- `:id`
- `:title`
- `:status`
- `:created_at`
- `:updated_at`
- `:final_at`
- `[:attr :key]` for JSON task attributes
- `[:attr :nested :key]` for nested JSON attributes

Supported operators:

```clojure
[:= field value]
[:!= field value]
[:< field value]
[:<= field value]
[:> field value]
[:>= field value]
[:in field [value ...]]
[:exists field]
[:missing field]
[:and query ...]
[:or query ...]
[:not query]
```

Use `[:param :name]` inside named queries to accept runtime values.

```clojure
{:params [:owner :priority]
 :where [:and
         [:= [:attr :owner] [:param :owner]]
         [:= [:attr :priority] [:param :priority]]]}
```

CLI `--param` values are strings. REPL parameter values may be Clojure scalars that SQLite JSON comparisons can handle.
