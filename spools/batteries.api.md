# Table of contents
-  [`skein.spools.batteries`](#skein.spools.batteries)  - Shipped core strand command surface as parser-backed weaver ops.
    -  [`activate!`](#skein.spools.batteries/activate!) - Register the batteries core strand ops into a weaver runtime.
    -  [`add-op`](#skein.spools.batteries/add-op) - Create a strand with merged attributes, optional state, and outgoing edges.
    -  [`burn-op`](#skein.spools.batteries/burn-op) - Physically delete one strand by id and return the burn summary.
    -  [`list-op`](#skein.spools.batteries/list-op) - List lean-projected strands, optionally filtered by lifecycle state and/or a named query.
    -  [`pattern-op`](#skein.spools.batteries/pattern-op) - Introspect registered weave patterns: list all metadata or explain one.
    -  [`query-op`](#skein.spools.batteries/query-op) - Introspect registered named queries: list all metadata or explain one.
    -  [`ready-op`](#skein.spools.batteries/ready-op) - List lean-projected ready strands, optionally from the result set of a named query.
    -  [`show-op`](#skein.spools.batteries/show-op) - Return one normalized strand by id.
    -  [`subgraph-op`](#skein.spools.batteries/subgraph-op) - Return a relation-scoped subgraph rooted at one strand.
    -  [`supersede-op`](#skein.spools.batteries/supersede-op) - Replace one strand with another and return the supersession result.
    -  [`update-op`](#skein.spools.batteries/update-op) - Patch one strand's title, state, attributes, and outgoing edges.
    -  [`weave-op`](#skein.spools.batteries/weave-op) - Apply a registered create-only weave pattern to one JSON input value.

-----
# <a name="skein.spools.batteries">skein.spools.batteries</a>


Shipped core strand command surface as parser-backed weaver ops.

  Batteries registers the everyday strand operations — add/update/show/supersede/
  burn/list/ready/subgraph plus the create-only `weave` op and the read-only
  `query`/`pattern` registry-introspection ops — as `register-op!` ops whose
  `:arg-spec` is parsed by `skein.api.cli.alpha`. Each op delegates to the same
  `skein.api.weaver.alpha` calls the JSON socket dispatch uses today and returns
  the same JSON shapes, so the ops are reachable through `strand <name>` at the
  CLI root. The namespace owns no module-level state:
  op handlers read the runtime from their invocation context (`:op/runtime`).

  Attribute/edge flag semantics reproduce old SPEC-002.C6–C11: `--attr key=value`
  is a repeatable, highest-precedence string map whose values may be payload
  references; `--attributes` references a JSON object of typed bulk attributes at
  lowest precedence; `--edge edge-type:to-id` adds outgoing edges. `--state`
  accepts `active|closed` for mutations and `active|closed|replaced` for `list`
  filtering.




## <a name="skein.spools.batteries/activate!">`activate!`</a>
``` clojure
(activate!)
(activate! rt)
```
Function.

Register the batteries core strand ops into a weaver runtime.

  The no-arg arity registers into the active runtime for `use!`-style
  installation; the explicit-runtime arity is for tests and trusted callers.
<p><sub><a href="https://github.com/codethread/skein/blob/main/spools/src/skein/spools/batteries.clj#L403-L418">Source</a></sub></p>

## <a name="skein.spools.batteries/add-op">`add-op`</a>
``` clojure
(add-op ctx)
```
Function.

Create a strand with merged attributes, optional state, and outgoing edges.
<p><sub><a href="https://github.com/codethread/skein/blob/main/spools/src/skein/spools/batteries.clj#L168-L180">Source</a></sub></p>

## <a name="skein.spools.batteries/burn-op">`burn-op`</a>
``` clojure
(burn-op ctx)
```
Function.

Physically delete one strand by id and return the burn summary.
<p><sub><a href="https://github.com/codethread/skein/blob/main/spools/src/skein/spools/batteries.clj#L208-L211">Source</a></sub></p>

## <a name="skein.spools.batteries/list-op">`list-op`</a>
``` clojure
(list-op ctx)
```
Function.

List lean-projected strands, optionally filtered by lifecycle state and/or a named query.
<p><sub><a href="https://github.com/codethread/skein/blob/main/spools/src/skein/spools/batteries.clj#L213-L228">Source</a></sub></p>

## <a name="skein.spools.batteries/pattern-op">`pattern-op`</a>
``` clojure
(pattern-op ctx)
```
Function.

Introspect registered weave patterns: list all metadata or explain one.
<p><sub><a href="https://github.com/codethread/skein/blob/main/spools/src/skein/spools/batteries.clj#L276-L285">Source</a></sub></p>

## <a name="skein.spools.batteries/query-op">`query-op`</a>
``` clojure
(query-op ctx)
```
Function.

Introspect registered named queries: list all metadata or explain one.
<p><sub><a href="https://github.com/codethread/skein/blob/main/spools/src/skein/spools/batteries.clj#L265-L274">Source</a></sub></p>

## <a name="skein.spools.batteries/ready-op">`ready-op`</a>
``` clojure
(ready-op ctx)
```
Function.

List lean-projected ready strands, optionally from the result set of a named query.
<p><sub><a href="https://github.com/codethread/skein/blob/main/spools/src/skein/spools/batteries.clj#L230-L242">Source</a></sub></p>

## <a name="skein.spools.batteries/show-op">`show-op`</a>
``` clojure
(show-op ctx)
```
Function.

Return one normalized strand by id.
<p><sub><a href="https://github.com/codethread/skein/blob/main/spools/src/skein/spools/batteries.clj#L197-L200">Source</a></sub></p>

## <a name="skein.spools.batteries/subgraph-op">`subgraph-op`</a>
``` clojure
(subgraph-op ctx)
```
Function.

Return a relation-scoped subgraph rooted at one strand.
<p><sub><a href="https://github.com/codethread/skein/blob/main/spools/src/skein/spools/batteries.clj#L244-L253">Source</a></sub></p>

## <a name="skein.spools.batteries/supersede-op">`supersede-op`</a>
``` clojure
(supersede-op ctx)
```
Function.

Replace one strand with another and return the supersession result.
<p><sub><a href="https://github.com/codethread/skein/blob/main/spools/src/skein/spools/batteries.clj#L202-L206">Source</a></sub></p>

## <a name="skein.spools.batteries/update-op">`update-op`</a>
``` clojure
(update-op ctx)
```
Function.

Patch one strand's title, state, attributes, and outgoing edges.
<p><sub><a href="https://github.com/codethread/skein/blob/main/spools/src/skein/spools/batteries.clj#L182-L195">Source</a></sub></p>

## <a name="skein.spools.batteries/weave-op">`weave-op`</a>
``` clojure
(weave-op ctx)
```
Function.

Apply a registered create-only weave pattern to one JSON input value.
<p><sub><a href="https://github.com/codethread/skein/blob/main/spools/src/skein/spools/batteries.clj#L255-L263">Source</a></sub></p>
