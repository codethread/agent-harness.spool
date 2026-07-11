
-----
# <a name="skein.api.events.alpha">skein.api.events.alpha</a>


Explicit-runtime API for registering, inspecting, and submitting weaver events.

  Callers own runtime selection and pass the target weaver runtime as the first
  argument. This namespace owns event handler validation, function resolution,
  registry state, asynchronous failure capture, and event submission; the queue
  submission and worker dispatch live in `skein.core.weaver.dispatch` and
  `skein.core.weaver.runtime`.




## <a name="skein.api.events.alpha/await-quiescent!">`await-quiescent!`</a>
``` clojure
(await-quiescent! runtime)
(await-quiescent! runtime {:keys [timeout-ms]})
```
Function.

Block until `runtime`'s event lane settles, then return `runtime`.

  Settled means the bounded event queue is empty *and* no handler dispatch is in
  flight; the worker raises its dispatch-in-progress flag before it claims an
  event, so this never reports settled while a just-claimed dispatch is still
  running. Throws an `ex-info` on timeout. The default budget comes from
  `skein.spools.test-support/await-budget-ms`; override it with `:timeout-ms`.

  This is a lane-only primitive: it says nothing about off-lane completion
  signals a handler may have kicked off (poll-until loops, agent-run awaits).
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/events/alpha.clj#L86-L115">Source</a></sub></p>

## <a name="skein.api.events.alpha/enqueue!">`enqueue!`</a>
``` clojure
(enqueue! runtime event)
```
Function.

Submit an event map to `runtime`'s event system for asynchronous dispatch.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/events/alpha.clj#L81-L84">Source</a></sub></p>

## <a name="skein.api.events.alpha/handlers">`handlers`</a>
``` clojure
(handlers runtime)
```
Function.

Return data-first event handler registry entries from `runtime`.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/events/alpha.clj#L70-L74">Source</a></sub></p>

## <a name="skein.api.events.alpha/recent-failures">`recent-failures`</a>
``` clojure
(recent-failures runtime)
```
Function.

Return recent asynchronous event handler failures from `runtime`.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/events/alpha.clj#L76-L79">Source</a></sub></p>

## <a name="skein.api.events.alpha/register!">`register!`</a>
``` clojure
(register! runtime key types fn-sym)
(register! runtime key types fn-sym metadata)
```
Function.

Register or replace an event handler in `runtime` for selected event types.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/events/alpha.clj#L50-L61">Source</a></sub></p>

## <a name="skein.api.events.alpha/unregister!">`unregister!`</a>
``` clojure
(unregister! runtime key)
```
Function.

Unregister an event handler by stable key from `runtime`.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/events/alpha.clj#L63-L68">Source</a></sub></p>
