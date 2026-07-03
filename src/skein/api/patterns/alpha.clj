(ns skein.api.patterns.alpha
  "Explicit-runtime API for registering, inspecting, and invoking weave patterns.

  Callers own runtime selection and pass the target weaver runtime as the first
  argument. The weaver API owns pattern validation, function resolution, input
  spec validation, and transactional batch creation."
  (:require [skein.api.weaver.alpha :as api]))

(defn register-pattern!
  "Register a weaver-memory weave pattern in `runtime`."
  ([runtime name fn-sym input-spec]
   (api/register-pattern! runtime name fn-sym input-spec))
  ([runtime name doc fn-sym input-spec]
   (api/register-pattern! runtime name doc fn-sym input-spec)))

(defn patterns
  "Return serializable weaver-memory pattern registry entries from `runtime`."
  [runtime]
  (api/patterns runtime))

(defn pattern
  "Return one registered pattern entry from `runtime`, or fail loudly if missing."
  [runtime name]
  (api/resolve-pattern runtime name))

(defn explain
  "Return caller guidance for a registered pattern's input contract in `runtime`."
  [runtime name]
  (api/pattern-explain runtime name))

(defn weave!
  "Invoke a registered pattern with input data and atomically create its strand batch."
  [runtime name input]
  (api/weave! runtime name input))
