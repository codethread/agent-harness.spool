(ns skein.api.hooks.alpha
  "Explicit-runtime API for registering and inspecting weaver lifecycle hooks.

  Callers own runtime selection and pass the target weaver runtime as the first
  argument. The weaver API owns hook validation, function resolution, registry
  state, and synchronous invocation by later lifecycle gates."
  (:require [skein.api.weaver.alpha :as api]))

(defn register!
  "Register or replace a lifecycle hook in `runtime`."
  ([runtime key types fn-sym]
   (register! runtime key types fn-sym {}))
  ([runtime key types fn-sym opts]
   (api/register-hook! runtime key types fn-sym opts)))

(defn unregister!
  "Unregister a lifecycle hook by stable key from `runtime`."
  [runtime key]
  (api/unregister-hook! runtime key))

(defn hooks
  "Return data-first lifecycle hook registry entries from `runtime`."
  [runtime]
  (api/hooks runtime))
