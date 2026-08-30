# Agent Harness v26 (proposed)

This is the next Agent Harness release marker after v25. It carries the alpha Millstrand identity break: product dependencies and core namespaces use `io.millstrand/millstrand` and `millstrand.*`; no Skein compatibility alias is provided. The annotated `v26` tag must be cut only from landed canonical main; the coordinator records its peeled SHA in the MSR-06 release map.

The core is consumed by immutable commit SHA, not a core tag:

```clojure
{io.millstrand/millstrand
 {:git/url "https://github.com/codethread/millstrand.git"
  :git/sha "71c0ed3d80fcad090b74a704a8eb165a3fad996e"
  :deps/root "."}}
```

Workflow, identity, and Kanban are ordinary tools.deps roots from the landed Millhouse commit `f487eb42ea9523e8bd405e64a7c319013217d988`. Local sibling development may use private `deps.local.edn` overlays, but those overlays are removed from release proof. The verifier exercises delegation, await/review, and accounting entry points in a fresh disposable workspace.

## Release verifier contract

The release verifier treats the candidate root `deps.edn`, workspace `.millstrand/deps.edn`, and the two release JSON records as one strict boundary. It parses them once, requires exact immutable Millstrand `71c0ed3d80fcad090b74a704a8eb165a3fad996e` and Millhouse `f487eb42ea9523e8bd405e64a7c319013217d988` coordinates, and rejects disagreement in any declared root or alias pin before doing network or runtime work.

Pre-tag mode verifies a disposable copy of the current worktree. Published mode first proves the annotated tag's peeled SHA and then verifies the checked-out candidate through the same boundary. Neither mode has a historical-coordinate fallback or a manifest compatibility path.

The runtime smoke uses one disposable deps-native workspace and one Weaver lifetime. It never renames a marker or restarts a Weaver.

Rollback is to the prior published v25 Agent Harness release. This release does not migrate or activate any existing `.skein` world and does not recreate the forbidden core `v1` marker.
