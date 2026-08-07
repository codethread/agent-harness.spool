(ns module-adapters
  "Reconcile repo-owned adapters for enabled spool modules."
  (:require [millstrand.api.current.alpha :as current]
            [millstrand.api.lifecycle.alpha :as lifecycle]
            [millstrand.api.runtime.help-transform.alpha :as help-transform]))

(defn open-help-transform!
  "Elect batteries' default help renderer for this runtime."
  [{:keys [runtime]}]
  (current/with-runtime runtime
    (help-transform/register-default-help-transform!
     runtime
     {:transform @(requiring-resolve
                   'millstrand.spools.batteries/default-help-transform)
      :owner 'millstrand.spools.batteries}))
  {:opened :help-transform})

(defn close-help-transform!
  "Release the workspace's selected help renderer."
  [{:keys [runtime]}]
  (help-transform/unregister-default-help-transform!
   runtime 'millstrand.spools.batteries)
  {:closed :help-transform})

(lifecycle/defresource help-transform-runtime
  "Own the selected help renderer for the module lifetime."
  {:open 'module-adapters/open-help-transform!
   :close 'module-adapters/close-help-transform!})
