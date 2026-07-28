(ns ct.spools.claude-harness
  "Claude Code definition and provider-specific prepare/finish callbacks."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [ct.spools.harness-core :as core]
            [skein.api.spool.alpha :refer [attr-get fail!]]
            [skein.api.vocab.alpha :as vocab]))

(def ^:private efforts #{"low" "medium" "high" "xhigh" "max"})

(defn harness
  "Return the plain-data Claude Code harness definition."
  ([rt]
   (harness rt {}))
  ([_rt attributes]
   {:modes #{:headless :interactive}
    :prepare 'ct.spools.claude-harness/prepare
    :finish 'ct.spools.claude-harness/finish
    :attributes attributes}))

(defn- attribute [run k]
  (attr-get run k))

(defn- validate-extra-argv [argv]
  (when-not (and (vector? argv) (every? #(and (string? %) (not (str/blank? %))) argv))
    (fail! "harness.claude/extra-argv must be a vector of non-blank strings"
           {:extra-argv argv}))
  argv)

(defn prepare
  "Turn the resolved harness and full run strand into Claude argv."
  [_rt _resolved-harness run]
  (let [mode (attribute run :harness/mode)
        resumes (attribute run :harness/resumes)
        session-id (attribute run :harness/session-id)
        model (attribute run :harness.claude/model)
        effort (attribute run :harness.claude/effort)
        prompt (attribute run :harness/prompt)
        extra (or (attribute run :harness.claude/extra-argv) [])]
    (when-not (#{"headless" "interactive"} mode)
      (fail! "Claude run mode is unsupported" {:mode mode}))
    (when (str/blank? session-id)
      (fail! "Claude run requires a session id" {:run (:id run)}))
    (when (and effort (not (efforts effort)))
      (fail! "Claude effort is unsupported" {:effort effort :allowed (sort efforts)}))
    (validate-extra-argv extra)
    (vec
     (concat
      ["claude"]
      (when (= "headless" mode) ["--print" "--output-format" "json"])
      (if resumes ["--resume" session-id] ["--session-id" session-id])
      (when model ["--model" model])
      (when effort ["--effort" effort])
      extra
      (when (and (= "interactive" mode) (not (str/blank? prompt))) [prompt])))))

(defn- clipped [s]
  (when-not (str/blank? s)
    (subs s 0 (min 4000 (count s)))))

(defn finish
  "Normalize Claude's process result into the core outcome."
  [_rt _resolved-harness run {:keys [exit-code stdout stderr]}]
  (let [mode (attribute run :harness/mode)
        known-session (attribute run :harness/session-id)]
    (if (= "interactive" mode)
      (if (zero? exit-code)
        {:status :done
         :exit-code exit-code
         :result (attribute run :harness/result)
         :session-id known-session}
        {:status :failed
         :exit-code exit-code
         :session-id known-session
         :error (or (clipped stderr) (str "Claude exited " exit-code))})
      (if-not (zero? exit-code)
        {:status :failed
         :exit-code exit-code
         :session-id known-session
         :error (or (clipped stderr) (clipped stdout) (str "Claude exited " exit-code))}
        (try
          (let [parsed (json/read-str stdout :key-fn keyword)
                result (:result parsed)
                session-id (or (:session_id parsed) known-session)]
            (if (str/blank? result)
              {:status :failed
               :exit-code exit-code
               :session-id session-id
               :error (str "Claude returned no result: " (or (clipped stdout) "<blank>"))}
              {:status :done
               :exit-code exit-code
               :result result
               :session-id session-id}))
          (catch Exception e
            {:status :failed
             :exit-code exit-code
             :session-id known-session
             :error (str "Claude JSON parse failed: " (ex-message e)
                         (when-let [output (clipped stdout)] (str "\n" output)))}))))))

(defn reconcile
  "Declare Claude overlay vocabulary and register the concrete Claude harness."
  [{:keys [runtime] :as ctx}]
  (case (get-in ctx [:module/contribution :status])
    :removed {:reconciled :removed}
    (do
      (vocab/declare! runtime
                      {:kind :attr-namespace
                       :name "harness.claude"
                       :owner :ct.spools/claude-harness
                       :keys ["harness.claude/model"
                              "harness.claude/effort"
                              "harness.claude/extra-argv"]
                       :doc "Claude Code command overlay attributes."})
      (core/register-harness!
       runtime :claude
       (harness runtime
                {:harness.claude/extra-argv ["--dangerously-skip-permissions"]}))
      {:reconciled :applied :harness "claude"})))

(def spool {:reconcile 'reconcile})
