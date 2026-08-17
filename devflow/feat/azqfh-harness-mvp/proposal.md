# Greenfield harness vertical-slice MVP

**Document ID:** `PROP-Hmv-001`
**Status:** Accepted
**Last Updated:** 2026-07-28
**Kanban epic:** `abu1q`
**Design spike:** `azqfh`
**Implementation card:** `8ti7y`
**Research:** design strand `dulpu`; Codex task `qwm7q`; Claude task `wrzxa`; delegated plan `ck95v`
**Related existing code:** `agent-run/`, `delegation/` (context only; unchanged by this spike)

**Configuration identification:** Document IDs are ordered as document type, short name,
sequential id, then optional version. Every nested point uses the full document ID so references
remain globally grepable.

## PROP-Hmv-001.P1 Problem

The existing agent-run spool proves durable, readiness-driven agent execution, but it also owns
provider command lines and provider output parsers. That makes its shared abstraction know what
Codex, Claude Code, and Pi emit.

The replacement should be explored as a greenfield vertical slice rather than as a sequence of
horizontal framework layers. Building registries, process supervision, session hosting, providers,
and CLI separately would postpone the first real call until late in the work and allow attractive
but incorrect seams to survive.

This proposal therefore builds the smallest complete Claude Code path through exactly three new
spools. The implementation is deliberately throwaway: its job is to reveal the best API, not to
pre-build the eventual production engine.

## PROP-Hmv-001.P2 Governing constraints

- **PROP-Hmv-001.C1:** Hold `TEN-000@1`: prefer a clean greenfield contract over compatibility with
  the existing pre-v1 engine.
- **PROP-Hmv-001.C2:** Hold `TEN-001`: return structured data useful to coding agents.
- **PROP-Hmv-001.C3:** Hold `TEN-003`: missing harnesses, invalid transitions, unsupported modes,
  malformed provider results, and unknown modeled values fail loudly.
- **PROP-Hmv-001.C4:** Hold `TEN-004`: expose only the surface exercised by the first end-to-end
  slice.
- **PROP-Hmv-001.C5:** Hold `TEN-006`: `strand` remains a JSON control surface; runtime harness and
  alias configuration lives in trusted Clojure and the REPL.
- **PROP-Hmv-001.C6:** No existing `agent-run`, `delegation`, or bench contract changes during this
  slice.

## PROP-Hmv-001.P3 Goals

- **PROP-Hmv-001.G1:** Create durable harness-run strands with one small, provider-neutral core.
- **PROP-Hmv-001.G2:** Define Claude Code once and derive runtime aliases such as `opus-high` and
  `sonnet-low` from it.
- **PROP-Hmv-001.G3:** Start headless Claude runs asynchronously, return their IDs immediately, and
  await several runs in one call.
- **PROP-Hmv-001.G4:** Run Claude interactively in the caller's real host TTY through a tiny shell
  wrapper.
- **PROP-Hmv-001.G5:** Preassign a provider session ID before every new launch, headless or
  interactive, and resume only by an explicit ID.
- **PROP-Hmv-001.G6:** Allow aliases to supply defaults while runtime attributes replace them for one
  call.
- **PROP-Hmv-001.G7:** Make provider-owned strand data flat and queryable under
  `harness.<harness>/*`.
- **PROP-Hmv-001.G8:** Support retrying failed runs with current alias generation, frozen fallback,
  and inline replacement overrides.
- **PROP-Hmv-001.G9:** Keep provider-session continuation distinct from retry: resume creates a new
  strand connected to the prior run.

## PROP-Hmv-001.P4 Non-goals

- **PROP-Hmv-001.NG1:** No automated tests, fixtures, mocks, or test scaffolding. Exercise the slice
  manually.
- **PROP-Hmv-001.NG2:** No Codex, Pi, or generic JSONL implementation.
- **PROP-Hmv-001.NG3:** No tmux, zellij, backend registry, or multiplexer abstraction.
- **PROP-Hmv-001.NG4:** No provider hooks, transcript parser, transcript guarantee, or automatic
  interactive result extraction.
- **PROP-Hmv-001.NG5:** No heartbeat, PID identity, ticket authentication, process adoption, or
  restart reconciliation.
- **PROP-Hmv-001.NG6:** No workflow/subagent adapter.
- **PROP-Hmv-001.NG7:** No usage, cost, token, or timing accounting.
- **PROP-Hmv-001.NG8:** No production scheduler, multi-Weaver ownership, or retry-specific
  compare-and-set protocol.
- **PROP-Hmv-001.NG9:** No compatibility adapter or migration of existing `agent-run/*` strands.
- **PROP-Hmv-001.NG10:** No generic strategy framework beyond the two callbacks exercised here.

## PROP-Hmv-001.P5 Vertical slice and spool ownership

The slice contains exactly:

```text
workspace / REPL
  ├── register-harness! :claude
  └── register-alias! :opus-high {Claude defaults}
              ↓
agent-cli
  ├── headless:    create → async worker → parse → finish
  └── interactive: create launcher → host wrapper → finish
              ↓
harness-core
  └── durable request, registry, and legal lifecycle transitions
```

| Spool | Owns | Does not own |
| --- | --- | --- |
| `harness-core` | harness and alias registry; run structure; lifecycle transitions; minimum vocabulary | processes, argv, Claude semantics, CLI |
| `claude-harness` | Claude command construction; modeled Claude attributes; JSON result/session parsing; explicit resume | strand mutation, CLI parsing, terminal hosting |
| `agent-cli` | CLI operations; minimal async worker; process execution; interactive launcher/wrapper; await | Claude event semantics, transcripts, workflow gates, general scheduling |

Each spool is a normal shared-spool root with its own `deps.edn` and `spool.edn`. Its `reconcile`
function takes an explicit runtime. `harness-core` initializes its runtime-local registry;
`claude-harness` registers only its concrete harness definition; `agent-cli` registers the
`harness` CLI operation, installs a
graph-mutation event handler for pending headless runs, and performs one install-time scan so work
created before activation is not parked.

## PROP-Hmv-001.P6 Core harness definition and registry

### PROP-Hmv-001.P6.1 Definition shape

`harness-core` accepts one closed plain-data implementation map:

```clojure
{:modes #{:headless :interactive}
 :prepare 'ct.spools.claude-harness/prepare
 :finish  'ct.spools.claude-harness/finish
 :attributes
 {:harness.claude/extra-argv
  ["--dangerously-skip-permissions"]}}
```

Hook symbols resolve at execution time. Mode presence is the only capability declaration in the
MVP. There is no capability-ranking data structure yet. Constructing a harness produces data; it
does not register behavior or mutate runtime state.

Every public shared-spool function takes the Weaver runtime as its first argument. The two symbols
name functions with explicit contracts:

```clojure
(prepare runtime resolved-harness full-run-strand)
;; => non-empty vector of non-blank argv strings

(finish runtime resolved-harness full-run-strand process-result)
;; => normalized core outcome map
```

`resolved-harness` is the concrete plain-data definition after registry resolution. The full run
strand contains the canonical `harness/*` fields plus every merged overlay attribute such as
`harness.claude/*`. The callbacks do not receive a reduced request projection and do not query the
Weaver to reconstruct it.

`prepare` returns argv data, never a shell command string. `agent-cli` owns `ProcessBuilder`, sets
the process cwd from `harness/cwd`, supplies `harness/prompt` on stdin for headless execution, and
captures headless stdout/stderr. For interactive execution, Claude's `prepare` includes an optional
initial prompt in argv because stdin belongs to the live terminal.

`finish` receives the same resolved harness and full strand plus:

```clojure
{:exit-code 0
 :stdout "captured headless output or nil"
 :stderr "captured headless error output or nil"}
```

It returns the provider-neutral fields core may persist:

```clojure
{:status :done|:failed
 :exit-code 0
 :result "optional result"
 :session-id "provider-session-id"
 :error "optional failure"}
```

Core validates both callback return shapes. Exceptions and invalid return data fail the run loudly.
Before-process failures are recorded directly as failed outcomes without invoking provider
`finish`. Neither callback mutates strands, starts processes, or writes lifecycle attributes
directly.

### PROP-Hmv-001.P6.2 Registry API

```clojure
(register-harness! runtime :claude (claude/harness runtime))

(register-alias! runtime :opus-high :claude
  {:harness.claude/model "opus"
   :harness.claude/effort "high"})

(register-alias! runtime :sonnet-low :claude
  {:harness.claude/model "sonnet"
   :harness.claude/effort "low"})

(register-alias! runtime :opus-high-ro :opus-high
  {:harness.claude/extra-argv
   ["--allowedTools" "Read,Grep"]})
```

- `register-harness!` adds or replaces one concrete implementation by name.
- `register-alias!` adds or replaces one alias independently.
- An alias names either one concrete harness or another alias and owns a plain attribute map.
- Resolution walks the complete chain to one concrete harness, then merges attribute maps from
  least specific to most specific. The leaf alias wins over its parents.
- Registration is replace-by-name to support cheap REPL iteration.
- Resolution fails on missing concrete harnesses and alias cycles.
- The registry is runtime-local and need not be persisted.

The run records both names:

```clojure
{:harness/alias "opus-high"
 :harness/harness "claude"}
```

`harness/alias` is the requested preset. `harness/harness` is the concrete implementation needed
to execute frozen data if the alias later disappears.

For:

```text
opus-high-ro → opus-high → claude
```

the run records:

```clojure
{:harness/alias "opus-high-ro"
 :harness/harness "claude"}
```

The intermediate alias chain is reconstruction data, not canonical query surface.

There is no special composition mechanism. For `opus-high-ro`, resolution is equivalent to:

```clojure
(merge (:attributes claude-definition)
       (:attributes opus-high-definition)
       (:attributes opus-high-ro-definition)
       retained-run-overrides
       current-call-overrides)
```

Alias traversal only discovers the ordered maps and concrete implementation. Ordinary map merge is
the composition rule.

## PROP-Hmv-001.P7 Core run API and lifecycle

### PROP-Hmv-001.P7.1 Public mutation API

```clojure
(create! runtime {:harness "opus-high"
          :mode :headless
          :prompt "Review the change"
          :cwd "/repo"
          :harness.claude/effort "low"
          :harness.claude/extra-argv ["--allowedTools" "Read"]})
;; => active pending run strand

(mark-running! runtime run-id)
;; pending → running

(finish! runtime run-id
  {:status :done
   :exit-code 0
   :result "..."
   :session-id "..."})
;; running → done|failed
```

These are the only public core run mutations. `create!` mints the new run's UUIDv4 session ID.
Provider continuation is exposed only through the run-based resume operation in this slice.
`weaver/show` already reads runs.

### PROP-Hmv-001.P7.2 Lifecycle

```text
pending → running → done       closes
        ↘         → failed     remains active
          failed

failed → pending               retry
```

- Only the displayed transitions are legal. `pending → failed` records preparation or process-start
  failures that occur before a process exists.
- `done` requires exit zero.
- Headless `done` additionally requires a non-blank result.
- Interactive `done` does not require a result.
- Failed strands remain active so they continue blocking dependants and remain retryable.
- Successful closed strands are never reopened.

### PROP-Hmv-001.P7.3 At-most-once launch

The agent CLI's async scanner claims only pending **headless** IDs in one runtime-local in-flight
set before launch. Interactive runs leave pending only through the private `_started` operation,
so the host wrapper is their sole launch owner. The claim is released after `finish!`. This is
required for ordinary repeated graph events, not specifically for retry, and is sufficient for the
MVP's single-Weaver assumption.

Concurrent retry calls may both report acceptance, but the ordinary launch claim starts at most one
process. No storage-level conditional transition is added.

## PROP-Hmv-001.P8 Attribute contract

### PROP-Hmv-001.P8.1 Core canonical attributes

| Attribute | Required | Meaning |
| --- | --- | --- |
| `harness/run` | always | run marker, `"true"` |
| `harness/alias` | always | requested concrete name or alias |
| `harness/harness` | always | resolved concrete implementation |
| `harness/mode` | always | `"headless"` or `"interactive"` |
| `harness/phase` | always | `"pending"`, `"running"`, `"done"`, or `"failed"` |
| `harness/prompt` | headless; optional interactive | initial prompt |
| `harness/cwd` | always | execution directory |
| `harness/session-id` | always | preassigned or resumed provider session ID; a new-session retry replaces it |
| `harness/resumes` | resumed run only | predecessor run ID |
| `harness/result` | done headless; optional interactive | final response or best-effort notes |
| `harness/exit-code` | after exit | observed process exit code |
| `harness/error` | failed | human-readable failure |

No PID, heartbeat, timestamps, attempt counter, usage, cost, transcript path, ticket, backend,
handle, fan-out, or provider-specific field belongs in `harness/*`.

### PROP-Hmv-001.P8.2 Overlay namespace convention

Core owns `harness/*`. A harness overlay owns:

```text
harness.<harness>/*
```

Claude therefore owns:

```clojure
{:harness.claude/model "opus"
 :harness.claude/effort "high"
 :harness.claude/extra-argv ["--allowedTools" "Read"]
 :harness.claude/bespoke-claude-field "experimental-value"}
```

Overlay values are JSON-compatible scalars, maps, and arrays; registry helpers perform no keyword
coercion. The canonical values are flat strand attributes so queries and later spools can consume them.
Core persists them but never interprets them. Claude callbacks receive every
`harness.claude/*` attribute unchanged; they may interpret known keys, ignore bespoke keys, or
fail explicitly for unsupported values.

Provider namespaces make accidental foreign carryover harmless: a future Codex adapter ignores
`harness.claude/*`.

### PROP-Hmv-001.P8.3 Opaque reconstruction blobs

Core also retains two private read blobs:

```clojure
{:harness/generated {...}
 :harness/overrides {...}}
```

For the MVP they are plain maps: `generated` is the exact merged concrete-harness and alias defaults,
and `overrides` is the exact retained inline override map. No query or other spool builds behavior
on their contents.

Retry reconstructs the canonical overlay as:

```clojure
(merge current-generated-or-frozen-generated
       retained-overrides
       current-call-overrides)
```

Core rewrites both blobs from that operation. It writes a delta across the union of old and new
overlay keys, using nil values to delete keys no longer present. This prevents stale provider
attributes surviving an alias replacement or null removal.

## PROP-Hmv-001.P9 Resolution and override precedence

Resolution order is:

```text
retry-call overrides
  > retained inline overrides
    > leaf alias defaults
      > parent alias defaults
      > concrete harness defaults
```

Example:

```clojure
harness defaults
{:harness.claude/extra-argv ["--dangerously-skip-permissions"]}

alias defaults
{:harness.claude/model "opus"
 :harness.claude/effort "high"}

run overrides
{:harness.claude/effort "low"
 :harness.claude/extra-argv ["--allowedTools" "Read"]}

effective canonical attributes
{:harness.claude/model "opus"
 :harness.claude/effort "low"
 :harness.claude/extra-argv ["--allowedTools" "Read"]}
```

An `opus-high` alias may therefore run at low effort. Aliases are presets, not policy boundaries.

Given:

```clojure
(register-alias! :opus-high-ro :opus-high
  {:harness.claude/extra-argv
   ["--allowedTools" "Read,Grep"]})
```

`opus-high-ro` inherits the model and effort generated by `opus-high`, then adds its more specific
Claude argv. Runtime attributes still win over both aliases:

```sh
printf '%s' '{
  "harness.claude/effort": "medium"
}' |
strand --stdin harness run opus-high-ro \
  --cwd /repo \
  --prompt "Review without editing" \
  --attributes :stdin
```

The effective canonical attributes are model `opus`, effort `medium`, and
`extra-argv ["--allowedTools", "Read,Grep"]`.

`harness.claude/extra-argv` is a vector of strings appended verbatim after generated arguments. It
is an owner-risk escape hatch with no semantic validation, portability promise, or conflict
detection.

JSON null removes a retained override and reveals the next lower layer:

```json
{"harness.claude/effort": null}
```

## PROP-Hmv-001.P10 Claude harness API

### PROP-Hmv-001.P10.1 Constructor

```clojure
(harness runtime)

(harness runtime
         {:harness.claude/model "opus"
          :harness.claude/effort "high"
          :harness.claude/extra-argv
          ["--dangerously-skip-permissions"]})
```

The MVP models only `harness.claude/model`, `harness.claude/effort`, and
`harness.claude/extra-argv`. Effort accepts `"low"`, `"medium"`, `"high"`, `"xhigh"`, or `"max"`.
Other Claude-namespaced attributes still reach its callbacks.

### PROP-Hmv-001.P10.2 Prepare and finish callbacks

```clojure
(prepare runtime
  {:modes #{:headless :interactive}
   :prepare 'ct.spools.claude-harness/prepare
   :finish 'ct.spools.claude-harness/finish}
  {:id "run-id"
   :title "Review the change"
   :state "active"
   :attributes
   {:harness/run "true"
    :harness/alias "opus-high"
    :harness/harness "claude"
    :harness/mode "headless"
    :harness/phase "running"
    :harness/prompt "Review the change"
    :harness/cwd "/repo"
    :harness/session-id "preassigned-id"
    :harness.claude/model "opus"
    :harness.claude/effort "high"
    :harness.claude/extra-argv [...]}})
;; => ["claude" "--print" "--output-format" "json"
;;     "--session-id" "preassigned-id"
;;     "--model" "opus" "--effort" "high" ...]

(finish runtime
  resolved-harness
  full-run-strand
  {:exit-code 0
   :stdout "{\"type\":\"result\",...}"
   :stderr ""})
;; =>
{:status :done
 :exit-code 0
 :result "Review complete"
 :session-id "preassigned-id"}
```

Claude's `prepare` reads only the supplied data. It maps modeled `harness.claude/*` fields to Claude
flags, appends `harness.claude/extra-argv` verbatim, selects resume argv exactly when
`harness/resumes` is present, and appends the optional prompt only for interactive mode.

Claude's `finish` parses the documented headless JSON result. On failure it includes useful,
bounded stderr—or stdout when JSON parsing fails—in `harness/error`. In interactive mode stdout is
nil, so it classifies success from the exit code and preserves an optional result already written
by `self-complete`.

### PROP-Hmv-001.P10.3 Claude command behavior

| Case | Command behavior | Completion |
| --- | --- | --- |
| new headless | `claude --print --output-format json --session-id <uuid>`, prompt on stdin | parse documented result, session ID, and error fields |
| resume headless | `claude --print --output-format json --resume <session-id>`, prompt on stdin | same JSON contract; verify returned ID |
| new interactive | `claude --session-id <uuid> [prompt]` | known ID and process exit; automatic result not required |
| resume interactive | `claude --resume <session-id> [prompt]` | explicit ID and process exit |

Headless and interactive both have a known session identity before launch and both use explicit
resume. Interactive prompt is optional.

## PROP-Hmv-001.P11 CLI surface

### PROP-Hmv-001.P11.1 Run and await

`run` is always asynchronous:

```sh
# Preferred: use a registered alias.
strand harness run opus-high \
  --cwd /repo \
  --prompt "Review this change"

# Start three without background shells, then wait once.
one="$(strand harness run opus-high --cwd /repo --prompt 'Review A')"
two="$(strand harness run opus-high --cwd /repo --prompt 'Review B')"
three="$(strand harness run sonnet-low --cwd /repo --prompt 'Review C')"

strand harness await \
  "$(printf '%s' "$one" | jq -r .id)" \
  "$(printf '%s' "$two" | jq -r .id)" \
  "$(printf '%s' "$three" | jq -r .id)"
```

Headless JSON includes the run ID and pending phase. Interactive JSON additionally includes a
private launcher path:

```sh
strand harness run opus-high \
  --interactive \
  --cwd /repo
```

`--prompt` is required headlessly and optional interactively. `--cwd` defaults to the command
envelope's cwd. Runs with prompts use a truncated prompt as their default title; prompt-less
interactive runs use `"<alias> interactive run"`.

`await` accepts run IDs and blocks until every run reaches `done` or `failed`; failed runs are
terminal for waiting even though their strands remain active. It defaults to 300 seconds and
accepts `--timeout-secs`. Its JSON result contains a `runs` array with `id`, `phase`, `exit-code`,
`result`, `error`, and `session-id`, plus a `timed-out` array of unfinished IDs.

### PROP-Hmv-001.P11.2 Inline overlay attributes

The preferred surface is a named alias. One-off rich overrides use the standard stdin payload
channel:

```sh
printf '%s' '{
  "harness.claude/model": "opus",
  "harness.claude/effort": "high"
}' |
strand --stdin harness run claude \
  --cwd /repo \
  --prompt "Review this change" \
  --attributes :stdin
```

Arbitrary Claude overlay data rides the same way:

```sh
printf '%s' '{
  "harness.claude/effort": "low",
  "harness.claude/extra-argv": ["--allowedTools", "Read"],
  "harness.claude/bespoke-claude-field": "experimental-value"
}' |
strand --stdin harness run opus-high \
  --cwd /repo \
  --prompt "Review this change" \
  --attributes :stdin
```

`--attributes` accepts only attributes in the concrete harness's overlay namespace. It cannot
write `harness/*` lifecycle fields.

### PROP-Hmv-001.P11.3 Host-TTY wrapper

One small user-side command owns the real terminal:

```sh
bin/strand-harness opus-high \
  --cwd /repo \
  --prompt "Pair with me on this bug"
```

It:

1. calls `strand harness run ... --interactive`;
2. extracts the launcher path;
3. calls the private `_started` transition;
4. runs the launcher with inherited stdin, stdout, and stderr;
5. calls `_finished RUN_ID --exit-code N`.

The wrapper contains no Claude argv or parsing knowledge. The private launcher is a mode-0700
`#!/bin/sh` file under the Weaver state directory. `run --interactive` invokes `prepare`
synchronously and writes the resulting argv into that launcher; headless runs invoke `prepare` in
the async worker. The launcher exports `SKEIN_RUN_ID`, the workspace path, and `XDG_STATE_HOME`,
changes to `harness/cwd`, then `exec`s the argv with inherited stdin, stdout, and stderr. Launcher
cleanup is outside the MVP.

### PROP-Hmv-001.P11.4 Best-effort interactive result

The interactive prompt may tell the agent:

```sh
strand --workspace "$SKEIN_WORKSPACE" \
  harness self-complete "$SKEIN_RUN_ID" "Final notes"
```

`self-complete` records or replaces `harness/result`. It does not close the run; process exit owns
lifecycle completion. A successful interactive process does not require self-completion.

## PROP-Hmv-001.P12 Retry

Retry applies only to active failed strands:

```sh
strand harness retry RUN_ID [replacement arguments]
```

It validates the prospective request before changing the run, then clears prior error, result, and
exit code, transitions `failed → pending`, and lets the ordinary async worker launch it. A retry of
a new-session run mints a fresh UUID because Claude will not create the same session twice. A retry
of a resumed run continues to use its inherited resume session.

### PROP-Hmv-001.P12.1 Retry unchanged

```sh
strand harness retry RUN_ID
```

Core tries to resolve `harness/alias` again:

- if the complete alias chain resolves, its current plain maps merge from root to leaf;
- if the unchanged chain no longer resolves, frozen `harness/generated` remains valid;
- retained `harness/overrides` is reapplied in either case.

This intentionally means replacing an alias map in the REPL can change the final canonical
attributes and behavior of a no-argument retry.

The concrete `harness/harness` implementation must still be registered. Frozen provider attributes
cannot execute without its callbacks.

Explicitly selecting an alias whose chain is currently broken fails loudly. Frozen fallback applies
only when retrying the run's unchanged stored alias; it does not make a missing or broken alias
available for new selection.

### PROP-Hmv-001.P12.2 Replace a core value

```sh
strand harness retry RUN_ID --cwd /repo-b
```

`cwd` replaces the previous value; other request data is reconstructed normally.

### PROP-Hmv-001.P12.3 Replace and remove inline overrides

```sh
printf '%s' '{"harness.claude/effort":"low"}' |
strand --stdin harness retry RUN_ID --attributes :stdin
```

The supplied value replaces any earlier inline effort override.

```sh
printf '%s' '{"harness.claude/effort":null}' |
strand --stdin harness retry RUN_ID --attributes :stdin
```

Null removes the retained inline override and reveals the current alias or harness default.

### PROP-Hmv-001.P12.4 Change alias

Given:

```clojure
(register-alias! runtime :sonnet-low :claude
  {:harness.claude/model "sonnet"
   :harness.claude/effort "low"})
```

then:

```sh
strand harness retry RUN_ID --harness sonnet-low
```

discards every field supplied by the prior alias chain, merges the current `sonnet-low` chain, and
reapplies retained inline overrides. Explicitly naming a missing alias fails even though an
unchanged retry could use its frozen data.

Same-call inline attributes win:

```sh
printf '%s' '{
  "harness.claude/effort": "high",
  "harness.claude/extra-argv": ["--allowedTools", "Read"]
}' |
strand --stdin harness retry RUN_ID \
  --harness sonnet-low \
  --cwd /repo-c \
  --attributes :stdin
```

The result uses model `sonnet`, effort `high`, the supplied cwd, and the supplied extra argv.

## PROP-Hmv-001.P13 Resume

Retry repeats one failed logical invocation and therefore mutates the same strand. Resume is a new
invocation in an existing provider conversation and therefore creates a new strand.

```text
run A: done
   ↑
   └── resumes ── run B: pending → running → done
```

CLI:

```sh
strand harness resume RUN_A \
  --prompt "Now implement the second option"
```

Run B inherits the predecessor's concrete harness, alias, session ID, cwd, generated values, and
overrides, then applies same-call replacements. It records:

```clojure
{:harness/resumes "run-A-id"
 :harness/session-id "existing-claude-session"}
```

and a `resumes` annotation edge to run A.

Resume requires a `done` predecessor with a recorded session ID. Failed runs use retry, and an
already-running interactive conversation remains the same invocation.

An interactive conversation continuing while its original TTY process remains alive is still the
same run. Reopening that provider session after process completion creates the new resumed run.

## PROP-Hmv-001.P14 Manual acceptance

No automated suite is added. Before declaring the spike useful:

1. Register `claude`, then add `opus-high` through a separate alias call.
2. Start three headless prompts and verify every call returns immediately.
3. Await all three IDs together.
4. Inspect closed strands for result, preassigned session ID, phase, and exit code.
5. Resume one successful headless run into a new strand and verify session continuity.
6. Create an interactive run without a prompt through `bin/strand-harness`.
7. Optionally call `self-complete`; verify success does not depend on it.
8. Resume the completed interactive run into a new strand and verify prior context.
9. Run `opus-high` with low effort and `extra-argv` inline overrides.
10. Force a headless failure, redefine `opus-high` in the REPL, retry without CLI overrides, and
    verify the regenerated canonical attributes.
11. Remove the alias, retry the same failure unchanged, and verify frozen data still works.
12. Explicitly request that now-missing alias through `retry --harness`; verify loud refusal.
13. Remove an inline override with JSON null and verify the alias default becomes canonical.

## PROP-Hmv-001.P15 Known throwaway compromises

- Private interactive transitions authenticate only by run ID.
- The async launch claim is runtime-local.
- Restart loses in-flight process ownership.
- Interactive result capture is optional and cooperative.
- Raw Claude transcripts and hooks are not consumed.
- A retry may resolve different current alias maps without retaining registry history.
- No attempt history is stored; retry replaces terminal fields on the same failed strand.
- Core mints UUIDv4 session IDs for new runs. That happens to satisfy Claude's requirement but is
  not yet claimed as a provider-neutral session format.

These are visible constraints, not promises hidden behind future-sounding APIs.

## PROP-Hmv-001.P16 Acceptance gate

The repository owner accepted the proposal on 2026-07-28, including the `strand harness` public
operation needed to coexist with the existing delegation spool. Implementation card `8ti7y` may
now be promoted and claimed.
