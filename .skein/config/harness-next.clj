(ns harness-next
  "Repo-local aliases for exercising the next harness vertical slice."
  (:require [ct.spools.harness-core :as harness]
            [skein.api.lifecycle.alpha :as lifecycle]))

(defn open-harness-next!
  "Register the workspace's aliases for the next harness implementation."
  [{:keys [runtime]}]
  (harness/register-alias! runtime :opus-high :claude
                           {:harness.claude/model "opus"
                            :harness.claude/effort "high"})
  (harness/register-alias! runtime :sonnet-low :claude
                           {:harness.claude/model "sonnet"
                            :harness.claude/effort "low"})
  {:opened :harness-next :aliases ["opus-high" "sonnet-low"]})

(defn close-harness-next!
  "Close the harness-next workspace resource."
  [_context]
  {:closed :harness-next})

(lifecycle/defresource harness-next-runtime
  "Own the workspace's next-harness aliases for the module lifetime."
  {:open 'harness-next/open-harness-next!
   :close 'harness-next/close-harness-next!})
