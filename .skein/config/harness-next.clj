(ns harness-next
  "Repo-local aliases for exercising the next harness vertical slice."
  (:require [ct.spools.harness-core :as harness]))

(defn reconcile
  "Register the workspace's aliases for the next harness implementation."
  [{:keys [runtime] :as ctx}]
  (case (get-in ctx [:module/contribution :status])
    :removed {:reconciled :removed}
    (do
      (harness/register-alias!
       runtime :opus-high :claude
       {:harness.claude/model "opus"
        :harness.claude/effort "high"})
      (harness/register-alias!
       runtime :sonnet-low :claude
       {:harness.claude/model "sonnet"
        :harness.claude/effort "low"})
      {:reconciled :applied
       :aliases ["opus-high" "sonnet-low"]})))

(def spool
  "Entry-point declaration for the harness-next workspace-file module."
  {:reconcile 'reconcile})
