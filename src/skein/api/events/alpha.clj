(ns skein.api.events.alpha
  "Explicit-runtime API for registering and inspecting weaver event handlers.

  Callers own runtime selection and pass the target weaver runtime as the first
  argument. The weaver API owns event handler validation, function resolution,
  registry state, and asynchronous failure capture."
  (:require [skein.api.weaver.alpha :as api]))

(defn register!
  "Register or replace an event handler in `runtime`."
  ([runtime key types fn-sym]
   (register! runtime key types fn-sym {}))
  ([runtime key types fn-sym metadata]
   (api/register-event-handler! runtime key types fn-sym metadata)))

(defn unregister!
  "Unregister an event handler by stable key from `runtime`."
  [runtime key]
  (api/unregister-event-handler! runtime key))

(defn handlers
  "Return data-first event handler registry entries from `runtime`."
  [runtime]
  (api/event-handlers runtime))

(defn recent-failures
  "Return recent event handler failures from `runtime` as data-first maps."
  [runtime]
  (api/recent-event-failures runtime))
