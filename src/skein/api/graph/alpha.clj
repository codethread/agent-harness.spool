(ns skein.api.graph.alpha
  "Explicit-runtime API for query selection, strand hydration, and graph traversal.

  Callers own runtime selection and pass the target weaver runtime as the first
  argument. The weaver API owns validation, persistence, query evaluation, and
  relation traversal semantics."
  (:require [skein.api.weaver.alpha :as api]))

(defn query-ids!
  "Return strand ids matching an ad hoc query definition or registered query name."
  [runtime query params]
  (api/query-ids runtime query params))

(defn burn-by-ids!
  "Burn strands by id through `runtime`."
  [runtime ids]
  (api/burn-by-ids runtime ids))

(defn burn-by-id!
  "Burn one strand by id through `runtime`."
  [runtime id]
  (api/burn-by-id runtime id))

(defn strands-by-ids
  "Hydrate strands by id through `runtime`."
  [runtime ids]
  (api/strands-by-ids runtime ids))

(defn ancestor-root-ids
  "Return ancestor root ids for seed ids through `runtime`."
  ([runtime seed-ids]
   (api/ancestor-root-ids runtime seed-ids))
  ([runtime seed-ids opts]
   (api/ancestor-root-ids runtime seed-ids opts)))

(defn subgraph
  "Return a relation-scoped subgraph for root ids through `runtime`."
  ([runtime root-ids]
   (api/subgraph runtime root-ids))
  ([runtime root-ids opts]
   (api/subgraph runtime root-ids opts)))
