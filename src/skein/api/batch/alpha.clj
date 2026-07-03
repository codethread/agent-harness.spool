(ns skein.api.batch.alpha
  "Explicit-runtime API for applying batch graph mutations.

  Callers own runtime selection and pass the target weaver runtime as the first
  argument. The weaver API owns payload validation and transactional persistence."
  (:require [skein.api.weaver.alpha :as api]))

(defn apply!
  "Apply one transactional batch graph mutation payload to `runtime`."
  [runtime payload]
  (api/apply-batch runtime payload))
