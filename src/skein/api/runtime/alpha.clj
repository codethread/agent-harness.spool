(ns skein.api.runtime.alpha
  "Explicit-runtime API for trusted weaver runtime loader/config workflows.

  Callers own runtime selection and pass the target weaver runtime as the first
  argument. Use `skein.api.current.alpha/runtime` only at trusted in-process entry
  points that need to capture the active runtime."
  (:refer-clojure :exclude [sync use])
  (:require [skein.api.weaver.alpha :as api]))

(defn approved
  "Return the normalized approved spool roots for `runtime`'s config dir."
  [runtime]
  (api/approved-spools runtime))

(defn sync!
  "Load approved local roots into `runtime`."
  [runtime]
  (api/sync-approved-spools runtime))

(defn syncs
  "Return `runtime`'s most recent approved-root sync state."
  [runtime]
  (api/approved-spool-syncs runtime))

(defn reload!
  "Reload startup files from `runtime`'s config dir after clearing registries."
  [runtime]
  (api/reload-config! runtime))

(defn use!
  "Activate a weaver-side module in `runtime` and record its use state."
  [runtime key opts]
  (api/use! runtime key opts))

(defn uses
  "Return `runtime`'s module-use registry as data-first maps."
  [runtime]
  (api/uses runtime))

(defn use
  "Return one module-use registry entry from `runtime` by key."
  [runtime key]
  (api/use runtime key))
