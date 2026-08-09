(ns delegation-contracts
  "Bind this workspace's delegation contracts over the shared agent seats."
  (:require [millstrand.api.current.alpha :as current]
            [millstrand.api.lifecycle.alpha :as lifecycle]
            [ct.spools.delegation :as agents]
            [ct.spools.agent-run :as shuttle]))

(defn open-delegation-contracts!
  "Bind the delegation spool's default review and worker contracts."
  [{:keys [runtime]}]
  (current/with-runtime
    runtime
    (shuttle/set-default-review-contract! agents/review-contract)
    (shuttle/set-default-task-contract! agents/worker-contract))
  {:opened :delegation-contracts})

(defn close-delegation-contracts!
  "Clear this workspace's default review and worker contracts."
  [{:keys [runtime]}]
  (current/with-runtime
    runtime
    (shuttle/set-default-review-contract! nil)
    (shuttle/set-default-task-contract! nil))
  {:closed :delegation-contracts})

(lifecycle/defresource delegation-contracts-runtime
  "Own the workspace's default delegation contracts for the module lifetime."
  {:open 'delegation-contracts/open-delegation-contracts!
   :close 'delegation-contracts/close-delegation-contracts!})
