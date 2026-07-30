(ns ct.spools.agent-cli
  "CLI and execution layer for provider-neutral harness runs."
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [ct.spools.harness-core :as harness]
            [skein.api.current.alpha :as current]
            [skein.api.events.alpha :as events]
            [skein.api.runtime.alpha :as runtime]
            [skein.api.skein.alpha :as skein]
            [skein.api.spool.alpha :refer [attr-get fail! require-valid!]]
            [skein.api.weaver.alpha :as weaver])
  (:import [java.lang ProcessBuilder]
           [java.nio.file Files]
           [java.nio.file.attribute PosixFilePermissions]
           [java.util.concurrent Executors ThreadFactory TimeUnit]))

(def ^:private state-version 1)
(def ^:private event-types #{:strand/added :strand/updated :batch/applied})

(declare ^:private scan!
         state
         pending-headless
         claim!
         launch-headless!
         op-run
         await!
         op-retry
         op-resume
         summary
         mark-interactive-running!
         finish-interactive!
         harness-arg-spec)

(s/def ::event
  (s/and map?
         #(keyword? (:event/type %))
         #(contains? % :event/id)))
(s/def ::op-context
  (s/and map?
         #(s/valid? ::harness/runtime (:op/runtime %))
         #(map? (:op/args %))))
(s/def ::alias string?)
(s/def ::harness string?)
(s/def ::mode #{"headless" "interactive"})
(s/def ::phase #{"pending" "running" "done" "failed"})
(s/def ::session-id string?)
(s/def ::launcher string?)
(s/def ::exit-code int?)
(s/def ::result string?)
(s/def ::error string?)
(s/def ::resumes string?)
(s/def ::run-summary
  (s/keys :req-un [::harness/id ::harness/title ::harness/state
                   ::alias ::harness ::mode ::phase ::session-id]
          :opt-un [::launcher ::exit-code ::result ::error ::resumes]))
(s/def ::runs (s/coll-of ::run-summary :kind vector?))
(s/def ::timed-out (s/coll-of ::harness/id :kind vector?))
(s/def ::claimed-run-ids (s/coll-of ::harness/id :kind vector?))
(s/def ::await-result
  (s/keys :req-un [::runs ::timed-out]))
(s/def ::op-result
  (s/or :run ::run-summary
        :await ::await-result
        :registry ::harness/registry-list))

(defn on-event
  "Schedule newly ready headless runs after a graph event.

  Claims eligible runs, submits each to the daemon executor, and returns their
  IDs without waiting for the launched processes to finish."
  [event]
  (require-valid! ::event event "Harness event handler received an invalid event")
  (let [rt (current/runtime)
        claimed (filterv #(claim! rt (:id %)) (pending-headless rt))
        executor (:executor (state rt))]
    (doseq [run claimed]
      (.execute executor ^Runnable #(launch-headless! rt (:id run))))
    (mapv :id claimed)))

(s/fdef on-event
  :args (s/cat :event ::event)
  :ret ::claimed-run-ids)

(defn reconcile
  [{:keys [runtime] :as ctx}]
  (require-valid! ::harness/reconcile-context ctx
                  "agent-cli reconcile received an invalid context")
  (require-valid!
   ::harness/reconcile-result
   (case (get-in ctx [:module/contribution :status])
     :removed (do
                (events/unregister-handler! runtime :harness/engine)
                {:reconciled :removed})
     (do
       (state runtime)
       (events/register-handler! runtime :harness/engine event-types
                                 'ct.spools.agent-cli/on-event
                                 {:spool "agent-cli"})
       {:reconciled :applied :claimed (scan! runtime)}))
   "agent-cli reconcile produced an invalid result"))

(s/fdef reconcile
  :args (s/cat :ctx ::harness/reconcile-context)
  :ret ::harness/reconcile-result)

(defn- daemon-thread-factory []
  (reify ThreadFactory
    (newThread [_ runnable]
      (doto (Thread. runnable "harness-worker")
        (.setDaemon true)))))

(defn- new-state []
  (let [executor (Executors/newCachedThreadPool (daemon-thread-factory))]
    {:in-flight (atom #{})
     :executor executor
     :close-fn (fn []
                 (.shutdownNow executor)
                 (.awaitTermination executor 1000 TimeUnit/MILLISECONDS))}))

(defn- state [rt]
  (runtime/spool-state rt ::state {:version state-version} new-state))

(defn- callback [symbol]
  (or (requiring-resolve symbol)
      (fail! "Harness callback cannot be resolved" {:callback symbol})))

(defn- run? [run]
  (= "true" (attr-get run :harness/run)))

(defn- pending-headless [rt]
  (filterv #(and (run? %)
                 (= "pending" (attr-get % :harness/phase))
                 (= "headless" (attr-get % :harness/mode)))
           (weaver/ready rt)))

(defn- claim! [rt id]
  (let [claimed? (atom false)]
    (swap! (:in-flight (state rt))
           (fn [ids]
             (if (contains? ids id)
               ids
               (do (reset! claimed? true) (conj ids id)))))
    @claimed?))

(defn- release! [rt id]
  (swap! (:in-flight (state rt)) disj id))

(defn- full-run [rt id]
  (or (weaver/show rt id) (fail! "Harness run not found" {:id id})))

(defn- resolved-definition [rt run]
  (harness/concrete-harness rt (attr-get run :harness/harness)))

(defn- valid-argv [argv]
  (when-not (and (vector? argv) (seq argv)
                 (every? #(and (string? %) (not (str/blank? %))) argv))
    (fail! "Harness prepare must return a non-empty argv vector" {:argv argv}))
  argv)

(defn- process-result [run argv]
  (let [pb (doto (ProcessBuilder. ^java.util.List argv)
             (.directory (io/file (attr-get run :harness/cwd))))
        process (.start pb)
        stdout-f (future (slurp (.getInputStream process)))
        stderr-f (future (slurp (.getErrorStream process)))]
    (with-open [stdin (.getOutputStream process)]
      (.write stdin (.getBytes (str (attr-get run :harness/prompt) "\n") "UTF-8")))
    {:exit-code (.waitFor process)
     :stdout @stdout-f
     :stderr @stderr-f}))

(defn- launch-headless!
  "Launch one already-claimed pending headless run."
  [rt id]
  (try
    (harness/mark-running! rt id)
    (let [run (full-run rt id)
          definition (resolved-definition rt run)
          argv (valid-argv ((callback (:prepare definition)) rt definition run))
          observed (process-result run argv)
          outcome ((callback (:finish definition)) rt definition run observed)]
      (harness/finish! rt id outcome))
    (catch Exception e
      (try
        (harness/finish! rt id {:status :failed
                                :error (str (ex-message e)
                                            (when-let [data (ex-data e)]
                                              (str " " (pr-str data))))})
        (catch Exception finish-error
          (binding [*out* *err*]
            (println "[harness] failed to record launch failure"
                     {:run id
                      :launch-error (ex-message e)
                      :finish-error (ex-message finish-error)})))))
    (finally
      (release! rt id)
      (scan! rt))))

(defn- scan!
  "Claim and asynchronously launch every ready pending headless run."
  [rt]
  (let [claimed (filterv #(claim! rt (:id %)) (pending-headless rt))
        executor (:executor (state rt))]
    (doseq [run claimed]
      (.execute executor ^Runnable #(launch-headless! rt (:id run))))
    (mapv :id claimed)))

(defn- sh-quote [s]
  (str "'" (str/replace (str s) "'" "'\\''") "'"))

(defn- state-root [rt]
  (-> (io/file (get-in rt [:metadata :state-dir]))
      .getParentFile .getParentFile .getParentFile .getCanonicalPath))

(defn- launcher-dir [rt]
  (doto (io/file (get-in rt [:metadata :state-dir]) "harness-launchers")
    (.mkdirs)))

(defn- write-launcher! [rt run argv]
  (let [file (io/file (launcher-dir rt) (str (:id run) ".sh"))
        workspace (get-in rt [:metadata :config-dir])]
    (spit file
          (str "#!/bin/sh\n"
               "export SKEIN_RUN_ID=" (sh-quote (:id run)) "\n"
               "export SKEIN_WORKSPACE=" (sh-quote workspace) "\n"
               "export XDG_STATE_HOME=" (sh-quote (state-root rt)) "\n"
               "cd " (sh-quote (attr-get run :harness/cwd)) " || exit 1\n"
               "exec " (str/join " " (map sh-quote argv)) "\n"))
    (Files/setPosixFilePermissions (.toPath file)
                                   (PosixFilePermissions/fromString "rwx------"))
    (.getCanonicalPath file)))

(defn- overlay-map [value]
  (cond
    (nil? value) {}
    (map? value) value
    :else (fail! "--attributes must be a JSON object" {:attributes value})))

(defn- summary [run]
  (cond-> {:id (:id run)
           :title (:title run)
           :state (:state run)
           :alias (attr-get run :harness/alias)
           :harness (attr-get run :harness/harness)
           :mode (attr-get run :harness/mode)
           :phase (attr-get run :harness/phase)
           :session-id (attr-get run :harness/session-id)}
    (some? (attr-get run :harness/exit-code))
    (assoc :exit-code (attr-get run :harness/exit-code))
    (attr-get run :harness/result) (assoc :result (attr-get run :harness/result))
    (attr-get run :harness/error) (assoc :error (attr-get run :harness/error))
    (attr-get run :harness/resumes) (assoc :resumes (attr-get run :harness/resumes))))

(defn- interactive-plan [rt run]
  (try
    (let [definition (resolved-definition rt run)
          argv (valid-argv ((callback (:prepare definition)) rt definition run))]
      (assoc (summary run) :launcher (write-launcher! rt run argv)))
    (catch Exception e
      (harness/finish! rt (:id run) {:status :failed
                                     :error (str (ex-message e)
                                                 (when-let [data (ex-data e)]
                                                   (str " " (pr-str data))))})
      (throw e))))

(defn- op-run [rt {:keys [harness interactive prompt cwd attributes title]} op-cwd]
  (let [run (harness/create!
             rt
             (cond-> {:harness harness
                      :mode (if interactive :interactive :headless)
                      :cwd (or cwd op-cwd)
                      :attributes (overlay-map attributes)}
               (some? prompt) (assoc :prompt prompt)
               (some? title) (assoc :title title)))]
    (if interactive
      (interactive-plan rt run)
      (do
        (scan! rt)
        (summary run)))))

(defn- terminal? [run]
  (#{"done" "failed"} (attr-get run :harness/phase)))

(defn- await!
  "Wait for run IDs to reach done or failed, returning structured summaries."
  [rt ids timeout-secs]
  (let [deadline (+ (System/nanoTime) (* 1000000000 (long timeout-secs)))]
    (loop []
      (let [runs (mapv #(full-run rt %) ids)
            unfinished (remove terminal? runs)]
        (if (or (empty? unfinished) (>= (System/nanoTime) deadline))
          {:runs (mapv summary runs)
           :timed-out (mapv :id unfinished)}
          (do (Thread/sleep 100) (recur)))))))

(defn- op-retry [rt args]
  (summary
   (harness/retry!
    rt (:run-id args)
    (cond-> {}
      (contains? args :harness) (assoc :harness (:harness args))
      (contains? args :cwd) (assoc :cwd (:cwd args))
      (contains? args :attributes) (assoc :attributes (overlay-map (:attributes args)))))))

(defn- op-resume [rt args]
  (let [run (harness/resume!
             rt (:run-id args)
             (cond-> {:mode (if (:interactive args) :interactive :headless)}
               (contains? args :prompt) (assoc :prompt (:prompt args))
               (contains? args :cwd) (assoc :cwd (:cwd args))
               (contains? args :attributes) (assoc :attributes (overlay-map (:attributes args)))
               (contains? args :title) (assoc :title (:title args))))]
    (if (:interactive args)
      (interactive-plan rt run)
      (do (scan! rt) (summary run)))))

(defn- mark-interactive-running! [rt id]
  (let [run (full-run rt id)]
    (when-not (= "interactive" (attr-get run :harness/mode))
      (fail! "_started applies only to interactive harness runs" {:id id}))
    (harness/mark-running! rt id)))

(defn- finish-interactive! [rt id exit-code]
  (let [run (full-run rt id)]
    (when-not (= "interactive" (attr-get run :harness/mode))
      (fail! "_finished applies only to interactive harness runs" {:id id}))
    (try
      (let [definition (resolved-definition rt run)
            outcome ((callback (:finish definition))
                     rt definition run
                     {:exit-code exit-code :stdout nil :stderr nil})]
        (harness/finish! rt id outcome))
      (catch Exception e
        (harness/finish! rt id {:status :failed
                                :exit-code exit-code
                                :error (str (ex-message e)
                                            (when-let [data (ex-data e)]
                                              (str " " (pr-str data))))})))))

(def ^:private harness-arg-spec
  {:op "harness"
   :doc "Create, await, retry, and resume provider-neutral harness runs."
   :subcommands
   {"run" {:doc "Create an asynchronous harness run."
           :hook-class :mutating :deadline-class :standard
           :flags {:interactive {:type :boolean :doc "Prepare a host-TTY interactive launcher."}
                   :cwd {:type :string :doc "Execution directory."}
                   :prompt {:type :string :doc "Prompt; required headlessly."}
                   :title {:type :string :doc "Run title."}
                   :attributes {:type :string :parse :json :doc "Provider overlay JSON object."}}
           :positionals [{:name :harness :type :string :required? true :doc "Concrete harness or alias."}]}
    "await" {:doc "Wait for runs to reach done or failed."
             :hook-class :read :deadline-class :unbounded
             :flags {:timeout-secs {:type :int :doc "Timeout in seconds; defaults to 300."}}
             :positionals [{:name :run-ids :type :string :required? true :variadic? true :doc "Run IDs."}]}
    "retry" {:doc "Retry one failed run in place."
             :hook-class :mutating :deadline-class :standard
             :flags {:harness {:type :string :doc "Replacement alias."}
                     :cwd {:type :string :doc "Replacement cwd."}
                     :attributes {:type :string :parse :json :doc "Provider overlay merge patch."}}
             :positionals [{:name :run-id :type :string :required? true :doc "Failed run ID."}]}
    "resume" {:doc "Create a new run continuing a completed provider session."
              :hook-class :mutating :deadline-class :standard
              :flags {:interactive {:type :boolean :doc "Prepare a host-TTY interactive launcher."}
                      :cwd {:type :string :doc "Replacement cwd."}
                      :prompt {:type :string :doc "Continuation prompt; required headlessly."}
                      :title {:type :string :doc "Run title."}
                      :attributes {:type :string :parse :json :doc "Provider overlay merge patch."}}
              :positionals [{:name :run-id :type :string :required? true :doc "Completed predecessor run ID."}]}
    "self-complete" {:doc "Record best-effort interactive result text."
                     :hook-class :mutating :deadline-class :standard
                     :positionals [{:name :run-id :type :string :required? true :doc "Interactive run ID."}
                                   {:name :result :type :string :required? true :doc "Final notes."}]}
    "_started" {:doc "Private wrapper transition: pending to running."
                :hook-class :mutating :deadline-class :standard
                :positionals [{:name :run-id :type :string :required? true :doc "Interactive run ID."}]}
    "_finished" {:doc "Private wrapper transition: record process exit."
                 :hook-class :mutating :deadline-class :standard
                 :flags {:exit-code {:type :int :required? true :doc "Observed process exit code."}}
                 :positionals [{:name :run-id :type :string :required? true :doc "Interactive run ID."}]}
    "list" {:doc "List registered concrete harnesses and aliases."
            :hook-class :read :deadline-class :standard}}})

(skein/defop harness
  "Dispatch parsed `strand harness` subcommands.

  Run, retry, and resume may schedule asynchronous headless work. `await`
  blocks the CLI thread until each requested run is terminal or its timeout
  expires; every other subcommand returns after its immediate transition."
  {:arg-spec harness-arg-spec}
  [{:op/keys [runtime args cwd] :as ctx}]
  (require-valid! ::op-context ctx "harness op received an invalid operation context")
  (require-valid!
   ::op-result
   (case (first (:subcommand args))
     "run" (op-run runtime args cwd)
     "await" (await! runtime (:run-ids args) (or (:timeout-secs args) 300))
     "retry" (op-retry runtime args)
     "resume" (op-resume runtime args)
     "self-complete" (summary (harness/self-complete! runtime (:run-id args) (:result args)))
     "_started" (summary (mark-interactive-running! runtime (:run-id args)))
     "_finished" (summary (finish-interactive! runtime (:run-id args) (:exit-code args)))
     "list" (harness/harnesses runtime))
   "harness op produced an invalid result"))

(s/fdef harness-op
  :args (s/cat :ctx ::op-context)
  :ret ::op-result)

(def spool
  "Declare the agent CLI lifecycle entry point."
  {:reconcile 'reconcile})
