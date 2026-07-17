# agent-harness.spool

This monorepo contains four related Skein surfaces:

- `agent-run`: the durable agent-run engine and harness process lifecycle.
- `agent-run`'s `ct.spools.executors.subagent`: the workflow-gate executor.
- `delegation`: the cross-harness `strand agent` delegation surface.
- `bench`: deterministic harness benchmarking and metrics extraction.

Each top-level spool root has its own `deps.edn`. Source roots are approved and
activated explicitly; this repository does not ship composition metadata.

## Dependency information

Use a real 40-character commit SHA in shared `spools.edn`. Git coordinates for
all three roots:

```clojure
{:spools
 {ct.spools/agent-run
  {:git/url "https://github.com/codethread/agent-harness.spool.git"
   :git/sha "<40-hex-sha-for-the-approved-commit>"
   :deps/root "agent-run"}
  ct.spools/delegation
  {:git/url "https://github.com/codethread/agent-harness.spool.git"
   :git/sha "<40-hex-sha-for-the-approved-commit>"
   :deps/root "delegation"}
  ct.spools/bench
  {:git/url "https://github.com/codethread/agent-harness.spool.git"
   :git/sha "<40-hex-sha-for-the-approved-commit>"
   :deps/root "bench"}}}
```

Equivalent local coordinates:

```clojure
{:spools
 {ct.spools/agent-run {:local/root "/path/to/agent-harness.spool/agent-run"}
  ct.spools/delegation {:local/root "/path/to/agent-harness.spool/delegation"}
  ct.spools/bench {:local/root "/path/to/agent-harness.spool/bench"}}}
```

The subagent executor also requires Skein's workflow spool. Approve either its
root in a local Skein checkout:

```clojure
{:spools
 {skein.spools/workflow {:local/root "/path/to/skein/spools/workflow"}}}
```

or a pinned nested root:

```clojure
{:spools
 {skein.spools/workflow
  {:git/url "https://github.com/codethread/skein.git"
   :git/sha "<40-hex-sha-for-the-pinned-commit>"
   :deps/root "spools/workflow"}}}
```

No prerequisite is fetched transitively. A runtime loads one version of each
namespace, so a pinned agent-harness commit runs against the consumer's single
chosen workflow version. Compatibility across version skew follows the
accretion convention: the engine adds; it does not break. This is a convention,
not a version contract the dependent spool can enforce.

## Activation

After approving the coordinates needed by the workspace, activate them from
trusted `init.clj`:

```clojure
(require '[skein.api.current.alpha :as current]
         '[skein.api.runtime.alpha :as runtime])

(def rt (current/runtime))
(runtime/sync! rt)

(runtime/use! rt :workflow
  {:ns 'skein.spools.workflow
   :spools '[skein.spools/workflow]
   :required? true})

(runtime/use! rt :agent-run
  {:ns 'ct.spools.agent-run
   :spools '[ct.spools/agent-run]
   :call 'ct.spools.agent-run/install!
   :required? true})

(runtime/use! rt :delegation
  {:ns 'ct.spools.delegation
   :spools '[ct.spools/delegation ct.spools/agent-run]
   :call 'ct.spools.delegation/install!
   :after [:agent-run]
   :required? true})

(runtime/use! rt :subagent
  {:ns 'ct.spools.executors.subagent
   :spools '[ct.spools/agent-run skein.spools/workflow]
   :call 'ct.spools.executors.subagent/install!
   :after [:workflow :agent-run]
   :required? true})

(runtime/use! rt :bench
  {:ns 'ct.spools.bench
   :spools '[ct.spools/bench ct.spools/agent-run]
   :call 'ct.spools.bench/install!
   :after [:agent-run]
   :required? true})
```

Remove activation blocks and approvals for surfaces the workspace does not use.

## Local development overrides

Keep shared `spools.edn` SHA-pinned. In gitignored `spools.local.edn`, overlay
the same coordinate symbols with direct roots:

```clojure
{:spools
 {ct.spools/agent-run {:local/root "/Users/you/dev/agent-harness.spool/agent-run"}
  ct.spools/delegation {:local/root "/Users/you/dev/agent-harness.spool/delegation"}
  ct.spools/bench {:local/root "/Users/you/dev/agent-harness.spool/bench"}
  skein.spools/workflow {:local/root "/Users/you/dev/skein/spools/workflow"}}}
```

Local entries replace shared entries by coordinate. `:deps/root` is git-only;
a local root points directly at the selected spool directory.

## Development

The root suite tests all five namespaces against a sibling `../skein-src`
checkout:

```sh
clojure -M:test
clojure -M:format
```
