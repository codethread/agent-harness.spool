# Agents Spool: a Cross-Harness Subagent Surface over Shuttle

**Document ID:** `RFC-015`
**Status:** Open
**Date:** 2026-07-03
**Related:** [Agent Shuttle](../../spools/shuttle/README.md), [Treadle Gate Bridge](../../spools/shuttle/treadle.md), [Shuttle-backed Agent Coordination](./2026-07-02-shuttle-backed-coordination.md) (RFC-010), [Weaver Runtime](../specs/daemon-runtime.md), [REPL API](../specs/repl-api.md), repo workspace policy in `.skein/config.clj`, `.agents/skills/strand/SKILL.md`

## RFC-015.P1 Problem

We want every coding agent working in a Skein workspace — regardless of harness — to be able to create subagents through shuttle instead of (or alongside) its harness-native subagent tools, in a way that respects the strand DAG and is observable by the coordinator and the user. The mechanics mostly exist; the packaging does not.

Evidence from the `runtime-ownership` feature (2026-07-03), which was coordinated exactly this way (one coordinator, one core pi-main run, then four concurrent pi-main runs, coordinator fan-in):

- **RFC-015.P1a:** Coordinator assembly cost. Before the first delegation the coordinator needed ~10 discovery steps: the strand skill, `pattern list`/`explain`, `op agent about`, `harnesses`, and a grep of `.skein/config.clj` to learn that `agent-delegate` exists at all. The orchestration loop (weave plan → delegate ready tasks → await → fan in) is written down nowhere.
- **RFC-015.P1b:** Load-bearing policy has no home. The decisions that made the fan-out safe — one shared worktree because sibling tasks were compile-coupled to the first task's output, disjoint per-task file ownership, workers never commit, strand bodies as the full contract, coordinator-only fan-in — were invented ad hoc in session. They worked, but the next coordinator reinvents them.
- **RFC-015.P1c:** The agent-facing vocabulary is owned by the engine. The `strand op agent` surface is registered by shuttle's `install!`, while the higher-level orchestration verbs (`agent-plan` pattern, `agent-delegate` op, delegation policy text) live as private policy in this repo's `.skein/config.clj`. Other workspaces get the engine but not the workflow.
- **RFC-015.P1d:** Worker-side guidance exists but is role-blind. Every run's injected preamble already carries its `run-id`, the pinned `strand` invocation, and spawn/await/note one-liners — the observability linchpin is built. What a worker lacks is judgement guidance: when to spawn, what roles (explore/review/test) look like, how sub-spawns should hang off the DAG, and what it must not do (commit, mutate siblings' scope).
- **RFC-015.P1e:** Harness-native subagents are invisible. Native scout/task tools are cheap and good, but their results bypass the graph: nothing is awaitable, nothing persists, the coordinator cannot see them. There is no stated boundary for when that invisibility is acceptable.

## RFC-015.P2 Goals

- **RFC-015.G1:** Any agent in a Skein workspace can discover and use shuttle-backed subagents in-band (via `strand op ...` manuals), without harness-specific skills.
- **RFC-015.G2:** Delegation shapes the strand DAG by convention: task strands carry contracts, runs attach to tasks, sub-spawns attach to the spawning run with `spawned-by` provenance, and the whole tree is inspectable by the coordinator and the user.
- **RFC-015.G3:** The orchestration vocabulary matches what agent LLMs are already heavily RL-trained on (agent/task/spawn/await), so the surface reads like a native tool rather than a bespoke DSL.
- **RFC-015.G4:** Coordinator guidance and worker guidance are both terse, discoverable, and owned by the same shipped artifact rather than one repo's private config.
- **RFC-015.G5:** Shuttle remains a small, composable run engine (per RFC-010's intent).

## RFC-015.P3 Non-goals

- **RFC-015.NG1:** No scheduler, resource caps, or depth enforcement. Agents are trusted (TEN-002); runaway protection stays at guidance level plus existing `max-attempts` until abuse is observed.
- **RFC-015.NG2:** No ban on harness-native subagent tools. The boundary is guidance: results that should outlive the run, be awaitable by others, or gate other strands go through shuttle; private throwaway exploration may stay native.
- **RFC-015.NG3:** No worktree management in the spool. Worktree allocation is coordinator/userland tooling (`wktree` etc.); the spool stays cwd-agnostic via the existing `--cwd` pass-through.
- **RFC-015.NG4:** Not every agent delegates. Depth-1 workers doing their assigned scope directly remains the default; delegation is a tool, not a mandate.

## RFC-015.P4 Options

| ID | Summary | Pros | Cons |
| --- | --- | --- | --- |
| RFC-015.O1 | Docs only: a skill plus AGENTS.md describing today's shuttle + config.clj setup | Cheapest; no code moves | Skills are harness-specific; orchestration verbs stay private to this repo; duplicates `about` text that will drift |
| RFC-015.O2 | Extend shuttle in place: fold plan/delegate/tree/policy into the shuttle spool | One namespace; existing op name kept | Bloats the engine with policy; contradicts shuttle's "not scheduler infrastructure" stance; harder to swap policy per workspace |
| RFC-015.O3 | New `agents` spool over shuttle: shuttle becomes a pure engine (no registered ops), `agents` owns the whole `strand op agent` surface, orchestration verbs, policy text, and role guidance | Familiar RL-trained vocabulary; engine stays composable; policy is shipped and reusable; workspace config shrinks to genuine tuning | A re-layering, not just an addition: the op surface moves out of shuttle; two spools to load instead of one |
| RFC-015.O4 | Guidance split as two API surfaces: separate worker ops and orchestrator ops | Role clarity enforced by shape | Artificial capability wall; a worker legitimately self-promotes to orchestrator for its own sub-world, and TEN-004 argues against duplicate surfaces |

## RFC-015.P5 Recommendation

- **RFC-015.REC1:** Adopt RFC-015.O3. Ship a `skein.spools.agents` spool that composes the shuttle engine and owns the complete agent-facing surface. Shuttle's `install!` stops registering ops; `agents/install!` registers `strand op agent ...` with the existing verbs (about/spawn/ps/await/logs/kill/note/notes/council/review — council/review are agent workflows, not run mechanics, so they move too) plus the promoted orchestration verbs: the `agent-plan` pattern, `agent delegate <task-id>` (from `.skein/config.clj`'s `agent-delegate`), a readiness-driven `agent delegate --ready <plan-id>` for fan-out, and an `agent tree [root]` view rendering the delegation graph from `parent-of` + `spawned-by` (data that already exists).
- **RFC-015.REC2:** Keep the op name singular `agent`. It already exists (no migration), and the heavily-trained tool names are singular (`Agent`, `Task`, `dispatch_agent`). The spool/namespace is plural (`agents`) because it packages the whole capability.
- **RFC-015.REC3:** One API, two guidance sets (deciding RFC-015.O4 against a split surface). Roles are lenses, not capabilities: any worker may promote itself to orchestrator for its own sub-world using the same ops. Tailoring happens in guidance placement: **workers** get the worker contract automatically — shuttle keeps injecting a minimal identity preamble (run-id, pinned command) and exposes a preamble-extension hook that `agents` fills with worker policy (read your strand first; record progress attrs; spawn read-only explore/review children freely with `--spawned-by`; never a second mutator in your file scope; never commit unless your contract says so; keep delegation shallow). **Orchestrators** opt in by reading `agent about` (which gains a coordinator section: plan → delegate ready → await → fan in, shared-worktree vs isolated-worktree tradeoff, disjoint scope rule, body-as-contract conventions) — plus a ~6-line harness-agnostic AGENTS.md pointer as the trigger.
- **RFC-015.REC4:** Repo-local `.skein/config.clj` shrinks to genuine workspace tuning: harness aliases (`pi-main`, `explore`, `grunt`, `build`), default review contract, chime attention rules. The strand skill shrinks to defer to the in-band manuals.

## RFC-015.P6 Consequences

- **RFC-015.C1:** Spool docs split accordingly: `spools/shuttle/README.md` documents the engine contract (harness registry, run lifecycle, preamble seam); a new `spools/agents.md` (or `spools/agents/README.md` if it ships as a local-root spool beside shuttle) documents the op surface, DAG conventions, and both guidance sets.
- **RFC-015.C2:** Workspaces that load shuttle today must load `agents` to keep `strand op agent`; the `.skein/init.clj` use-chain gains one entry. TEN-000 applies — no compatibility shim for the moved op registration.
- **RFC-015.C3:** The delegated-agent contract text moves from `.agents/skills/strand/SKILL.md` and `.skein/config.clj` into the spool, becoming the single source injected into worker preambles and printed by `agent about`.
- **RFC-015.C4:** `daemon-runtime.md` and `repl-api.md` references to shuttle's op registration move/point to the `agents` spool; treadle's gate bridge re-targets the promoted delegate verb.
- **RFC-015.C5:** The boundary rule for native subagent tools (RFC-015.NG2) becomes stated policy in AGENTS.md and `agent about`, giving coordinators a defensible default rather than per-session judgement.
- **RFC-015.C6:** Observability improves without new storage: `agent tree` renders existing provenance, and because sub-spawns ride the same run vocabulary, chime attention rules and the `agent-failures` query cover nested delegation for free.

## RFC-015.P7 Outcome

- **RFC-015.OUT1:** Pending decision.
