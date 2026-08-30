# agent-harness.spool

This monorepo contains four related Millstrand surfaces:

- `agent-run`: the durable agent-run engine and harness process lifecycle.
- `agent-run`'s `ct.spools.executors.subagent`: the workflow-gate executor.
- `delegation`: the cross-harness `strand agent` delegation surface.
- `bench`: deterministic harness benchmarking and metrics extraction.

Each published spool root has its own `deps.edn`. The checked-in `.millstrand/deps.edn` composes the repository's harness source paths and external pins; source roots are activated explicitly.

## Dependency information

Use a real 40-character commit SHA in the workspace's `deps.edn`. Git coordinates for all three roots:

```clojure
{:deps
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
{:deps
 {ct.spools/agent-run {:local/root "/path/to/agent-harness.spool/agent-run"}
  ct.spools/delegation {:local/root "/path/to/agent-harness.spool/delegation"}
  ct.spools/bench {:local/root "/path/to/agent-harness.spool/bench"}}}
```

The subagent executor also requires Millhouse's Workflow spool. Add either its root in a local Millhouse checkout:

```clojure
{:deps
 {millhouse.spools/workflow {:local/root "/path/to/millhouse.spool/spools/workflow"}}}
```

or a pinned nested root:

```clojure
{:deps
 {millhouse.spools/workflow
  {:git/url "https://github.com/codethread/millhouse.spool.git"
   :git/sha "f487eb42ea9523e8bd405e64a7c319013217d988"
   :deps/root "spools/workflow"}}}
```

Dependencies make source available; each workspace still activates only the modules it needs from trusted `init.clj`. A runtime loads one version of each namespace, so a pinned agent-harness commit runs against the consumer's chosen Workflow version.

## Compatibility: v7 → v8 (discovery-tier factoring)

The `delegation` (`ct.spools/agent-run`) coordinate carries a **breaking**
published-name change and ships as a new release (**v8**), not an accretion:

- **`agent` discovery surface reshaped.** `about`/`prime` are no longer `agent`
  subcommands: they are op-level prose the builtin `strand about agent` /
  `strand prime agent` meta-verbs project, and `strand help agent <verb>` carries
  per-verb detail from the arg-spec plus authored `:annotations` (use-when, notes,
  and glossary-referenced failure modes). `strand agent about` no longer returns
  the structured `{operation, concepts, verbs}` JSON manual, and the sole-token
  `strand agent about` / `agent prime` grammar is retired (it now fails loudly with
  a redirect to `strand help agent`). Same published op name, changed behavior →
  a v7 consumer scripting `strand agent about`'s shape breaks, so this is a
  new-release break by classification, not silent accretion.
- **The engine run preamble** now points delegated agents at `about agent`
  (was `agent about`), matching the new grammar.
- **New Millstrand API floor.** This release requires a Millstrand checkout shipping the
  discovery-tier deltas (DELTA-Dtf-001/002/003): the runtime glossary registry
  (`millstrand.api.runtime.glossary.alpha/register-glossary-outcome!`), op-metadata
  `:about`/`:prime`, arg-spec `:annotations`, and the builtin `about`/`prime`/`help`
  grammar with the retired-`<op> help` redirect. Encode this as the advisory
  `:millstrand/min` floor (a `vN` marker of the first Millstrand release carrying the
  deltas) in `spool.edn` **at tag time** (Task 10/11); it is intentionally left
  unpinned here because the Millstrand release marker is cut later.
- **Producer compat alarm.** `bin/compat-alarm <v7-tag>` against this working tree
  is expected to go **red** (the retired grammar and the changed `about` shape).
  That red is the correct signal that the change ships under a new release rather
  than being papered over — it must not be silenced before the v8 tag.

## Compatibility: selectable harness authoring

The agent-run authoring contract now makes `defharnesses` and `defaliases` inert declarations. Existing consumers that relied on source evaluation to publish those entries must add `use-harnesses!`/`use-aliases!`, or adopt the define-and-select `defharnesses!`/`defaliases!` forms. Selection is owner-complete: omitting a selection on refresh retracts that owner's prior entries, while the inert declaration remains reusable by another owner.

Selection accepts only the optional `{:override? boolean}` map. Unknown keys, non-boolean values, and an explicitly supplied `nil` fail loudly; omission is the no-options case. See [`agent-run/README.md`](./agent-run/README.md#21-selectable-harness-and-alias-declarations) for the migration example and full contract.

## Activation

After approving the coordinates needed by the workspace, activate them from
trusted `init.clj`. `ct.spools.agent-run`, `ct.spools.delegation`, and `ct.spools.bench`, plus `ct.spools.executors.subagent`, collect their core and domain contributions while their source is evaluated. Named lifecycle resource declarations own setup and removal. A consumer names only a source target and world policy. The Millstrand checkout must contain the contribution and lifecycle authoring APIs:

```clojure
(require '[millstrand.api.current.alpha :as current]
         '[millstrand.api.runtime.alpha :as runtime])

(def rt (current/runtime))
(runtime/module! rt :workflow
  {:ns 'millhouse.spools.workflow
   :required? true})

(runtime/module! rt :agent-run
  {:ns 'ct.spools.agent-run
   :required? true})

(runtime/module! rt :delegation
  {:ns 'ct.spools.delegation
   :after [:agent-run]
   :required? true})

(runtime/module! rt :subagent
  {:ns 'ct.spools.executors.subagent
   :after [:workflow :agent-run]
   :required? true})

(runtime/module! rt :bench
  {:ns 'ct.spools.bench
   :after [:agent-run]
   :required? true})
```

Remove activation blocks and approvals for surfaces the workspace does not use.

## Local development overrides

Keep shared `deps.edn` SHA-pinned. In gitignored `.millstrand/deps.local.edn`, override the same coordinate symbols with direct roots:

```clojure
{:deps
 {ct.spools/agent-run {:local/root "/Users/you/dev/agent-harness.spool/agent-run"}
  ct.spools/delegation {:local/root "/Users/you/dev/agent-harness.spool/delegation"}
  ct.spools/bench {:local/root "/Users/you/dev/agent-harness.spool/bench"}
  millhouse.spools/workflow {:local/root "/Users/you/dev/millhouse.spool/spools/workflow"}}}
```

Local entries replace shared entries by coordinate. `:deps/root` is git-only; a local root points directly at the selected spool directory. A changed dependency basis requires a replacement Weaver generation.

## Development

The root suite tests against Millstrand core from the sibling `../skein-src`
checkout and Workflow from the exact Millhouse commit pinned in `deps.edn`:

```sh
clojure -M:test
clojure -M:format
```
