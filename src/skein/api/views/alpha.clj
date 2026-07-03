(ns skein.api.views.alpha
  "Explicit-runtime API for registering, inspecting, and invoking weaver views.

  Callers own runtime selection and pass the target weaver runtime as the first
  argument. The weaver API owns view validation, function resolution, registry
  state, and invocation."
  (:require [skein.api.weaver.alpha :as api]))

(defn register-view!
  "Register a weaver-memory view name in `runtime` to a function symbol."
  [runtime name fn-sym]
  (api/register-view! runtime name fn-sym))

(defn view!
  "Invoke a registered weaver-side view with params through `runtime`."
  [runtime name params]
  (api/view! runtime name params))

(defn views
  "Return serializable weaver-memory view registry entries from `runtime`."
  [runtime]
  (api/views runtime))
