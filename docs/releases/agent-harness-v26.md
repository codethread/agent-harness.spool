# Agent Harness v26 (proposed)

This is the next Agent Harness release marker after v25. It carries the alpha Millstrand identity break: product dependencies and core namespaces use `io.millstrand/millstrand` and `millstrand.*`; no Skein compatibility alias is provided. The annotated `v26` tag must be cut only from landed canonical main; the coordinator records its peeled SHA in the MSR-06 release map.

The core is consumed by immutable commit SHA, not a core tag:

```clojure
{io.millstrand/millstrand
 {:git/url "https://github.com/codethread/millstrand.git"
  :git/sha "fb6c9057d594bfa4b5ea8531b9774b5e9a23a4b4"}}
```

The released Kanban dependency is pinned to `v24` and peeled SHA `87f61bc2750e7026f3650235907db25f19b1536e`. Local sibling development may use `{:local/root "../millstrand"}` only in a private override; it is not release proof. The release verifier rejects local roots and exercises delegation, await/review, and accounting entry points in a fresh disposable workspace.

The marker-rename smoke intentionally excludes sibling weaver UUID continuity. A fresh sibling weaver generation is allowed; storage identity and representative functional state must remain unchanged.

Rollback is to the prior published v25 Agent Harness release. This release does not migrate or activate any existing `.skein` world and does not recreate the forbidden core `v1` marker.
