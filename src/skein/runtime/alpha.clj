(ns skein.runtime.alpha
  "Privileged helper API for trusted weaver runtime loader/config workflows.

  This namespace routes approved-root inspection, sync, config reload, and
  module-use operations to the selected Skein weaver runtime. Inside a daemon
  process calls use the in-process runtime; from an explicit connected client they
  route through the active weaver client connection."
  (:refer-clojure :exclude [sync use])
  (:require [skein.client :as client]
            [skein.repl :as repl]
            [skein.weaver.runtime :as runtime]))

(defn- call-daemon [op & args]
  (if-let [rt @runtime/current-runtime]
    (apply (requiring-resolve (symbol "skein.weaver.api" (name op))) rt args)
    (apply client/call-world (repl/connected-config-dir) (repl/connected-opts) op args)))

(defn approved
  "Return the normalized approved spool roots for the selected weaver config dir.

  Reads the effective `spools.edn` plus `spools.local.edn` overlay through the
  active runtime and returns `{:spools ...}` with canonical local roots and source
  metadata. Malformed allowlists fail loudly with ExceptionInfo."
  []
  (call-daemon :approved-spools))

(defn sync!
  "Load approved local roots into the selected weaver runtime.

  Returns `{:spools ...}` with one result per approved spool and records the
  results in weaver-lifetime sync state. Structural allowlist errors throw;
  per-spool load failures are returned as failed result maps."
  []
  (call-daemon :sync-approved-spools))

(defn syncs
  "Return the selected weaver runtime's most recent approved-root sync state."
  []
  (call-daemon :approved-spool-syncs))

(defn reload!
  "Reload startup files from the selected config dir in the active weaver.

  Clears runtime extension registries before loading `init.clj` then
  `init.local.clj` when present and returns the load result map."
  []
  (call-daemon :reload-config!))

(defn use!
  "Activate a weaver-side module and record its use state.

  `key` must be a keyword. `opts` selects exactly one module source with `:ns`
  or `:file`, and may include `:spools`, `:after`, `:call`, and `:required?` gates.
  Returns a loaded, skipped, or failed module-use result map."
  [key opts]
  (call-daemon :use! key opts))

(defn uses
  "Return the selected weaver runtime's module-use registry as data-first maps."
  []
  (call-daemon :uses))

(defn use
  "Return one module-use registry entry from the selected weaver runtime by key."
  [key]
  (call-daemon :use key))
