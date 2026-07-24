# Skein Subagent Executor Spool

> This is the **contract** doc: gate request attributes, the `gate/*` vocabulary, delivery
> semantics, and recovery. Its two companions are [`subagent.cookbook.md`](./subagent.cookbook.md),
> for worked composition recipes, and [`subagent.api.md`](./subagent.api.md), for generated fn
> signatures and docstrings. Reach for the cookbook when you want a runnable pattern, the API doc
> when you want an exact arity, and this doc for what the adapter promises.

## Overview

`ct.spools.executors.subagent` is the agent-run-backed adapter for workflow gates whose waiter is
`subagent`. It watches ready workflow gates, spawns agent-run runs, and completes the gate with the
run result when the run succeeds.

The workflow engine remains forge/tool agnostic: workflow authors declare an ordinary
`(workflow/gate ... :subagent ...)` with attributes. Agent-run remains a run engine with no workflow
concepts. The subagent executor is the small bridge that knows both vocabularies.

## Loading

Load workflow and agent-run before the subagent executor, approving workflow's
source coordinate as well as the agent-run root:

```clojure
(require '[skein.api.current.alpha :as current]
         '[skein.api.runtime.alpha :as runtime])

(def runtime (current/runtime))
(runtime/module! runtime :workflow
  {:ns 'skein.spools.workflow
   :spools ['skein.spools/workflow]
   :required? true})
(runtime/module! runtime :agent-run
  {:ns 'ct.spools.agent-run
   :spools ['ct.spools/agent-run]
   :required? true})
(runtime/module! runtime :subagent
  {:ns 'ct.spools.executors.subagent
   :spools ['ct.spools/agent-run skein.spools/workflow]
   :after [:workflow :agent-run]
   :required? true})
```

Agent-run's and the subagent executor's entry points come from each namespace's
public `spool` var (the `def spool` convention, ADR-004), so both declarations
name only a source target and world policy. The Skein checkout must contain or
descend from `343f886880092bc38ed3e0522eca2d95a7cf04bc`, the first compatible
commit.

`reconcile` fails loudly unless `:agent-run/engine` is already installed.

Activate the subagent executor **after** any startup config that registers harness aliases.
`reconcile` runs an initial gate scan, so an alias a durable ready gate names (e.g. `worker`) must
already be registered or that gate is stamped `gate/error` on every cold start.

Gate scans serialize on a runtime-owned monitor: independent weaver runtimes in one JVM scan
independently and never block each other.

## Gate request attributes

| Attribute | Required | Meaning |
|---|---|---|
| `workflow/gate` = `"subagent"` | yes | Marks a ready workflow gate for subagent-executor fulfillment. Other waiters are ignored. |
| `agent-run/harness` | yes | Harness or alias name passed to `agent-run/spawn-run!`. Missing or invalid values stamp `gate/error` on the gate. |
| `agent-run/prompt` | no | Run prompt. If absent, derives one from `workflow/instruction`, `description`, then title; if none exists, stamps `gate/error`. |
| `agent-run/cwd` | no | Passed through as the run working directory. |
| `agent-run/max-attempts` | no | Crash-recovery attempt bound. Accepts an integer or integer string; anything else stamps `gate/error`. |
| `gate/completion-policy` | no | Completion acceptance: `run-done` (default) closes on a successful non-blank run; `status-implemented` additionally requires the gate task's `status` to be `implemented`. Unknown values fail loudly through `gate/error`. |

Generic gates deliberately retain `run-done` semantics. Build task-aware AFK gates with
`ct.spools.executors.subagent/task-gate`; it accepts the same keyword options as `workflow/gate`
after the id and title, fixes the waiter to `:subagent`, and always emits the strict
`status-implemented` policy. Thus task constructors own the safe default rather than their callers.

## Gate attributes

| Attribute | On | Meaning |
|---|---|---|
| `gate/error` | gate step | Durable spawn- or completion-policy failure detail; the gate is skipped until a coordinator clears it. |
| `gate/completion-policy` | gate step | `run-done` or `status-implemented`; absent means `run-done` for compatibility. |
| `workflow/run-id` | run strand | Workflow `run-id` owning the gate. Workflow's own key, stamped onto the run as-is: the executor inherits it rather than declaring a synonym. |
| `gate/delivered` | run strand | `"true"`, `"gate-closed"`, or `"error: …"`; presence means delivery is terminal for this run. |
| `gate/delivery-blocked` | run strand | Written once when a finished run's gate is active but not ready; delivery retries when the gate becomes ready. |

The subagent executor links each delegated run to its gate with a `serves` edge from the run to the
gate. It must not use `parent-of` for this provenance because that structural relation would place
the run inside the workflow subgraph and surface it as workflow `ready` work. Agent-run lineage
uses `supersedes` edges; retry successors inherit the same `serves` target.

## Worked example

```clojure
(require '[skein.spools.workflow :as workflow])

(def build-widget
  (workflow/workflow
    "Build widget"
    (workflow/step :design "Design widget" :self)
    (workflow/gate :implement "Implement widget" :subagent
                   :depends-on [:design]
                   :attributes {"agent-run/harness" "pi"
                                "agent-run/prompt" "Implement the widget per specs/widget.md"
                                "agent-run/cwd" "/path/to/worktree"})
    (workflow/step :review "Review implementation" :self
                   :depends-on [:implement])))

(workflow/start! "widget-1" build-widget {})
(workflow/complete! "widget-1")
;; The subagent executor observes :implement as a ready subagent gate, spawns an agent run
;; run, then completes the gate with workflow/outcome-by = run id and
;; workflow/outcome-notes = agent-run/result when the run closes successfully.
```

## Failure and recovery

Only a genuinely successful run delivers a gate: delivery selects closed runs in agent-run phase
`done`, which agent-run records solely for a non-blank result. A run that exits 0 with a **blank**
result is recorded `failed` by agent-run (see the README blank-result paragraph), so it never
completes the gate. A silently dead worker must not satisfy a subagent gate. Instead the failed run
keeps its `serves` edge to the gate. The ready gate is discoverable via the stall predicate and
`stalled-subagent-gates` query below and, as a delegated run, via `agent-failures`.

Under `status-implemented`, a completed run whose served gate task does not carry
`status=implemented` remains undelivered. The executor leaves the gate active and stamps
`gate/error`, so workflow await and `stalled-subagent-gates` surface the red gate instead of
advancing downstream work. After correcting the task status, clear `gate/error`; the next scan
reconsiders the same completed run. The default `run-done` policy does not inspect task status.

A coordinator recovers such a gate with `agent retry <run-id>` on the failed or exhausted
gate-serving run. Retry marks the dead run superseded and spawns a successor that inherits the run's
`serves` edge, dependency edges, and run metadata. The subagent executor then observes the successor
as the gate's current serving run and delivers it when it succeeds. For spawn-side failures, clear
the gate's `gate/error` attribute after fixing the bad request. Clear it by making the key absent
with a typed JSON-null merge patch — `strand update <gate-id> --attributes '{"gate/error": null}'` —
and the next scan can spawn the gate's first serving run. Absence is the canonical cleared state; a
blank `gate/error` is still tolerated as cleared for back-compat, but an empty string is data, not a
delete, so do not reach for `--attr gate/error=`.

If a gate is closed or routed away while its agent-run run is in flight, the completed run is
stamped `gate/delivered "gate-closed"` and the result remains on the run strand for audit. If the
gate is still active but no longer ready when the run finishes, the run is stamped
`gate/delivery-blocked` and delivery retries once the gate is ready again.

Crash-window caveat: spawn idempotency re-adopts runs through the durable `serves` edge. A run that
failed before its transaction wrote that edge is orphaned; a fresh run may spawn beside it, and the
failed run stays visible for audit.

## Coordination attention

Declarative publication registers `:subagent` with `workflow/executor-kind`, registering itself as the
executor for every gate whose `waiter` is `subagent`. Because an executor is registered, `await!`
stays silent (`:waiting`) on a healthy subagent gate instead of surfacing it immediately as `:gate`.
`gate-stalled?` reports a ready subagent gate as stalled (`:reason :stalled`) when the gate has
`gate/error` or its current serving run is in agent-run phase `failed` or `exhausted`; otherwise it
reports nothing. No wall-clock hang policy is applied. A superseded run is not itself a stall
because `agent retry` moves service to the successor.

The spool also registers `stalled-subagent-gates` and `blocked-deliveries` named queries for coordinator
inspection. `stalled-subagent-gates` is the SQL-side mirror of the stall predicate: it returns active
subagent gates that either carry `gate/error` or have an incoming `serves` edge from a run in phase
`failed` or `exhausted`. The same `serves`+lineage rule drives `gate-stalled?`: superseded runs are
outside the dead-phase set, and their successors inherit the `serves` edge. After `agent retry`, the
gate stops surfacing as soon as the successor is in flight. `blocked-deliveries` returns finished
runs parked on `gate/delivery-blocked`.

## See also

- [`skein.spools.workflow`](../workflow.md) — workflow gates and runtime API.
- [`ct.spools.agent-run`](../agent-run/README.md) — agent-run run lifecycle and harness registry.
- ``test/skein/executors/subagent_test.clj`` — executable contract tests.
