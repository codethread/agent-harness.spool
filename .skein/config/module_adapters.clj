(ns module-adapters
  "Reconcile repo-owned adapters for enabled spool modules."
  (:require [skein.api.current.alpha :as current]
            [skein.api.lifecycle.alpha :as lifecycle]
            [skein.api.runtime.help-transform.alpha :as help-transform]))

(defn open-help-transform!
  "Elect batteries' default help renderer for this runtime."
  [{:keys [runtime]}]
  (current/with-runtime runtime
    (help-transform/register-default-help-transform!
     runtime
     {:transform @(requiring-resolve
                   'skein.spools.batteries/default-help-transform)
      :owner 'skein.spools.batteries}))
  {:opened :help-transform})

(defn close-help-transform!
  "Release the workspace's selected help renderer."
  [{:keys [runtime]}]
  (help-transform/unregister-default-help-transform!
   runtime 'skein.spools.batteries)
  {:closed :help-transform})

(lifecycle/defresource help-transform-runtime
  "Own the selected help renderer for the module lifetime."
  {:open 'module-adapters/open-help-transform!
   :close 'module-adapters/close-help-transform!})
