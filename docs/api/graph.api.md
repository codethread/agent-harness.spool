
-----
# <a name="skein.api.graph.alpha">skein.api.graph.alpha</a>


Explicit-runtime API for the named-query registry, query selection, strand hydration, graph traversal, and burn.

  Callers own runtime selection and pass the target weaver runtime as the first
  argument. This namespace owns the blessed named-query registry surface and its
  validation, ad hoc and registered query id selection, strand hydration by id,
  relation-scoped traversal, edge adjacency, and burn with its pre-commit gate
  and event fanout. The query compiler lives in `skein.core.query`, the SQL
  engine in `skein.core.db`, and the shared lifecycle and dispatch plumbing in
  `skein.core.weaver.*`.




## <a name="skein.api.graph.alpha/ancestor-root-ids">`ancestor-root-ids`</a>
``` clojure
(ancestor-root-ids runtime seed-ids)
(ancestor-root-ids runtime seed-ids opts)
```
Function.

Return ancestor root ids reachable from `seed-ids`.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L116-L121">Source</a></sub></p>

## <a name="skein.api.graph.alpha/burn-by-id!">`burn-by-id!`</a>
``` clojure
(burn-by-id! runtime id)
(burn-by-id! runtime id req-ctx)
```
Function.

Delete one strand by id and return burn metadata.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L104-L109">Source</a></sub></p>

## <a name="skein.api.graph.alpha/burn-by-ids!">`burn-by-ids!`</a>
``` clojure
(burn-by-ids! runtime ids)
(burn-by-ids! runtime ids req-ctx)
```
Function.

Delete strands by id and enqueue burn events for removed rows.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L82-L102">Source</a></sub></p>

## <a name="skein.api.graph.alpha/incoming-edges">`incoming-edges`</a>
``` clojure
(incoming-edges runtime to-ids edge-type)
```
Function.

Return normalized `edge-type` edges whose target is one of `to-ids`.

  One indexed lookup for a strand's parents/annotators; no graph traversal.
  Adjacency is lenient: an id absent from storage yields no rows rather than a
  missing-id error (unlike subgraph/ancestor-root-ids seeds).
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L133-L140">Source</a></sub></p>

## <a name="skein.api.graph.alpha/load-queries!">`load-queries!`</a>
``` clojure
(load-queries! runtime query-defs)
```
Function.

Merge validated named query definitions into the runtime query registry.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L29-L34">Source</a></sub></p>

## <a name="skein.api.graph.alpha/outgoing-edges">`outgoing-edges`</a>
``` clojure
(outgoing-edges runtime from-ids edge-type)
```
Function.

Return normalized `edge-type` edges whose source is one of `from-ids`.

  One indexed lookup for a strand's children; no graph traversal. Lenient
  adjacency: an absent id yields no rows rather than a missing-id error.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L142-L148">Source</a></sub></p>

## <a name="skein.api.graph.alpha/queries">`queries`</a>
``` clojure
(queries runtime)
```
Function.

Return registered query definitions keyed by canonical string name.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L36-L39">Source</a></sub></p>

## <a name="skein.api.graph.alpha/query-explain">`query-explain`</a>
``` clojure
(query-explain runtime query-name)
```
Function.

Describe a registered query definition and how CLI callers invoke it.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L61-L72">Source</a></sub></p>

## <a name="skein.api.graph.alpha/query-ids">`query-ids`</a>
``` clojure
(query-ids runtime query-or-name params)
```
Function.

Return strand ids matching an ad hoc query definition or registered query name.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L74-L80">Source</a></sub></p>

## <a name="skein.api.graph.alpha/query-metadata">`query-metadata`</a>
``` clojure
(query-metadata runtime)
```
Function.

Return registered query caller metadata ordered by canonical name.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L56-L59">Source</a></sub></p>

## <a name="skein.api.graph.alpha/register-query!">`register-query!`</a>
``` clojure
(register-query! runtime query-name query-def)
```
Function.

Register a named query definition and return its canonical API shape.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L22-L27">Source</a></sub></p>

## <a name="skein.api.graph.alpha/resolve-query">`resolve-query`</a>
``` clojure
(resolve-query runtime query-name)
```
Function.

Return the registered query definition for a simple symbol or keyword name.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L41-L44">Source</a></sub></p>

## <a name="skein.api.graph.alpha/strands-by-ids">`strands-by-ids`</a>
``` clojure
(strands-by-ids runtime ids)
```
Function.

Return normalized strands for ids, preserving first-seen input order.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L111-L114">Source</a></sub></p>

## <a name="skein.api.graph.alpha/subgraph">`subgraph`</a>
``` clojure
(subgraph runtime root-ids)
(subgraph runtime root-ids opts)
```
Function.

Return a normalized strand subgraph rooted at `root-ids`.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/graph/alpha.clj#L123-L131">Source</a></sub></p>
