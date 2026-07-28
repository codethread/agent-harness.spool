(ns module-adapters
  "Reconcile repo-owned adapters for enabled spool modules."
  (:require [skein.api.current.alpha :as current]
            [skein.api.runtime.help-transform.alpha :as help-transform]))

(defn reconcile-help-transform
  "Elect batteries' default help renderer for this runtime."
  [{:keys [runtime]}]
  (current/with-runtime runtime
    (help-transform/register-default-help-transform!
     runtime
     {:transform @(requiring-resolve
                   'skein.spools.batteries/default-help-transform)
      :owner 'skein.spools.batteries}))
  {:reconciled :help-transform})

(def spool
  "Declare the module entry point that reconciles the selected renderer."
  {:reconcile 'reconcile-help-transform})
