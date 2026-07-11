
-----
# <a name="skein.api.views.alpha">skein.api.views.alpha</a>


Explicit-runtime API for registering, inspecting, and invoking weaver views.

  Callers own runtime selection and pass the target weaver runtime as the first
  argument. This namespace owns view validation, function resolution, registry
  state, and invocation.




## <a name="skein.api.views.alpha/register-view!">`register-view!`</a>
``` clojure
(register-view! runtime view-name fn-sym)
```
Function.

Register a weaver-memory view name in `runtime` to a function symbol.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/views/alpha.clj#L16-L22">Source</a></sub></p>

## <a name="skein.api.views.alpha/view!">`view!`</a>
``` clojure
(view! runtime view-name params)
```
Function.

Invoke a registered weaver-side view with params through `runtime`.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/views/alpha.clj#L36-L42">Source</a></sub></p>

## <a name="skein.api.views.alpha/views">`views`</a>
``` clojure
(views runtime)
```
Function.

Return serializable weaver-memory view registry entries from `runtime`.
<p><sub><a href="https://github.com/codethread/skein/blob/main/src/skein/api/views/alpha.clj#L24-L27">Source</a></sub></p>
