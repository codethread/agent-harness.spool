(ns skein.api.current.alpha
  "Isolated convenience facade for reading the current active Skein runtime.

  This namespace is the blessed public facade for trusted in-process config,
  spool, and REPL code that must capture the active weaver runtime explicitly and
  then pass it to `skein.api.*.alpha` functions. It never falls back to client or
  connected REPL state."
  (:require [skein.core.weaver.runtime :as runtime]))

(defn runtime
  "Return the active in-process weaver runtime, or fail loudly when absent."
  []
  (or @runtime/current-runtime
      (throw (ex-info "No active Skein weaver runtime" {}))))
