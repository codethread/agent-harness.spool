(ns ct.spools.harness-core
  "Provider-neutral structure, registry, and lifecycle for harness runs."
  (:require [clojure.string :as str]
            [skein.api.runtime.alpha :as runtime]
            [skein.api.spool.alpha :refer [attr-get fail!]]
            [skein.api.vocab.alpha :as vocab]
            [skein.api.weaver.alpha :as weaver])
  (:import [java.util UUID]))

(def ^:private registry-version 1)
(def ^:private overlay-prefix "harness.")
(def ^:private core-keys
  ["harness/run" "harness/alias" "harness/harness" "harness/mode"
   "harness/phase" "harness/prompt" "harness/cwd" "harness/session-id"
   "harness/resumes" "harness/result" "harness/exit-code" "harness/error"
   "harness/generated" "harness/overrides"])

(defn- new-registry []
  {:harnesses (atom {})
   :aliases (atom {})})

(defn- registry [rt]
  (runtime/spool-state rt ::registry {:version registry-version} new-registry))

(defn- name-string [v context]
  (let [s (cond
            (keyword? v) (name v)
            (symbol? v) (name v)
            (string? v) v
            :else nil)]
    (if (and s (not (str/blank? s)))
      s
      (fail! (str context " must be a non-blank name") {:value v}))))

(defn- overlay-key? [k]
  (str/starts-with? (if (keyword? k)
                      (if-let [n (namespace k)] (str n "/" (name k)) (name k))
                      (str k))
                    overlay-prefix))

(defn- normalize-overlay [m]
  (into {}
        (map (fn [[k v]]
               (let [k (if (keyword? k) k (keyword (str k)))]
                 (when-not (overlay-key? k)
                   (fail! "Harness overrides may contain only harness.<provider>/* attributes"
                          {:attribute k}))
                 [k v])))
        (or m {})))

(defn register-harness!
  "Add or replace one concrete harness definition in `rt`."
  [rt harness-name definition]
  (let [harness-name (name-string harness-name "Harness name")]
    (when-not (and (map? definition)
                   (set? (:modes definition))
                   (seq (:modes definition))
                   (qualified-symbol? (:prepare definition))
                   (qualified-symbol? (:finish definition)))
      (fail! "Harness definition is invalid" {:name harness-name :definition definition}))
    (let [definition (update definition :attributes normalize-overlay)]
      (swap! (:harnesses (registry rt)) assoc harness-name definition)
      {:harness harness-name :definition definition})))

(defn register-alias!
  "Add or replace one alias whose parent may be a concrete harness or alias."
  [rt alias-name parent attributes]
  (let [alias-name (name-string alias-name "Alias name")
        parent (name-string parent "Alias parent")
        entry {:parent parent :attributes (normalize-overlay attributes)}]
    (swap! (:aliases (registry rt)) assoc alias-name entry)
    {:alias alias-name :parent parent :attributes (:attributes entry)}))

(defn unregister-alias!
  "Remove one alias definition. Existing runs retain their frozen generated data."
  [rt alias-name]
  (let [alias-name (name-string alias-name "Alias name")
        [before _] (swap-vals! (:aliases (registry rt)) dissoc alias-name)]
    (when-not (contains? before alias-name)
      (fail! "Harness alias is not registered" {:alias alias-name}))
    {:unregistered alias-name}))

(defn resolve-harness
  "Resolve a concrete harness or alias into implementation data and merged defaults."
  [rt requested]
  (let [requested (name-string requested "Harness")
        {:keys [harnesses aliases]} (registry rt)]
    (loop [cursor requested seen #{} layers []]
      (when (contains? seen cursor)
        (fail! "Harness alias cycle" {:requested requested :at cursor}))
      (if-let [alias (get @aliases cursor)]
        (recur (:parent alias) (conj seen cursor) (conj layers (:attributes alias)))
        (if-let [definition (get @harnesses cursor)]
          {:alias requested
           :harness cursor
           :definition definition
           :generated (apply merge (:attributes definition) (reverse layers))}
          (fail! "Harness or alias is not registered"
                 {:requested requested :missing cursor}))))))

(defn concrete-harness
  "Return a registered concrete harness definition by name."
  [rt harness-name]
  (or (get @(:harnesses (registry rt)) (name-string harness-name "Concrete harness"))
      (fail! "Concrete harness is not registered" {:harness harness-name})))

(defn harnesses
  "List registered concrete harnesses and aliases."
  [rt]
  (let [{:keys [harnesses aliases]} (registry rt)]
    (vec
     (concat
      (for [[name definition] (sort-by key @harnesses)]
        {:name name :kind "harness" :modes (mapv clojure.core/name (:modes definition))})
      (for [[name {:keys [parent attributes]}] (sort-by key @aliases)]
        {:name name :kind "alias" :alias-of parent :attributes attributes})))))

(defn- mode-keyword [mode]
  (let [mode (if (keyword? mode) mode (keyword (str mode)))]
    (if (#{:headless :interactive} mode)
      mode
      (fail! "Harness mode must be headless or interactive" {:mode mode}))))

(defn- run-title [alias mode prompt]
  (if-not (str/blank? prompt)
    (subs prompt 0 (min 80 (count prompt)))
    (str alias " " (name mode) " run")))

(defn create!
  "Create one pending harness run. `attributes` contains provider overlay overrides."
  [rt {:keys [harness mode prompt cwd attributes title resumes session-id]}]
  (let [mode (mode-keyword (or mode :headless))
        {:keys [alias harness definition generated]} (resolve-harness rt harness)
        overrides (normalize-overlay attributes)
        effective (merge generated overrides)
        cwd (or cwd (System/getProperty "user.dir"))]
    (when-not (contains? (:modes definition) mode)
      (fail! "Harness does not support requested mode"
             {:harness harness :mode mode :modes (:modes definition)}))
    (when (and (= :headless mode) (str/blank? prompt))
      (fail! "Headless harness run requires a prompt" {:harness alias}))
    (weaver/add!
     rt
     (cond-> {:title (or title (run-title alias mode prompt))
              :attributes
              (merge
               {:harness/run "true"
                :harness/alias alias
                :harness/harness harness
                :harness/mode (name mode)
                :harness/phase "pending"
                :harness/cwd cwd
                :harness/session-id (or session-id (str (UUID/randomUUID)))
                :harness/generated generated
                :harness/overrides overrides}
               effective
               (when-not (str/blank? prompt) {:harness/prompt prompt})
               (when resumes {:harness/resumes resumes}))}
       resumes (assoc :edges [{:type "resumes" :to resumes}])))))

(defn- require-run [rt id]
  (let [run (or (weaver/show rt id) (fail! "Harness run not found" {:id id}))]
    (when-not (= "true" (attr-get run :harness/run))
      (fail! "Strand is not a harness run" {:id id}))
    run))

(defn- require-phase [run phase]
  (when-not (= phase (attr-get run :harness/phase))
    (fail! "Harness run has invalid phase for operation"
           {:id (:id run) :expected phase :actual (attr-get run :harness/phase)}))
  run)

(defn mark-running!
  "Transition a pending run to running."
  [rt id]
  (require-phase (require-run rt id) "pending")
  (weaver/update! rt id {:attributes {:harness/phase "running"}}))

(defn finish!
  "Record a terminal provider-neutral outcome. Failed runs remain active."
  [rt id {:keys [status exit-code result session-id error]}]
  (let [run (require-run rt id)
        phase (attr-get run :harness/phase)
        status (if (keyword? status) status (keyword (str status)))]
    (when-not (and (#{:pending :running} (keyword phase)) (#{:done :failed} status))
      (fail! "Harness finish transition is invalid" {:id id :phase phase :status status}))
    (when (and (= :done status) (not= 0 exit-code))
      (fail! "Successful harness outcome requires exit code zero" {:id id :exit-code exit-code}))
    (when (and (= :done status)
               (= "headless" (attr-get run :harness/mode))
               (str/blank? result))
      (fail! "Successful headless harness outcome requires a result" {:id id}))
    (weaver/update!
     rt id
     {:state (if (= :done status) "closed" "active")
      :attributes
      {:harness/phase (name status)
       :harness/exit-code exit-code
       :harness/result result
       :harness/session-id (or session-id (attr-get run :harness/session-id))
       :harness/error (when (= :failed status)
                        (or error "Harness process failed"))}})))

(defn self-complete!
  "Record best-effort interactive result text without changing lifecycle."
  [rt id result]
  (let [run (require-run rt id)]
    (when-not (= "interactive" (attr-get run :harness/mode))
      (fail! "self-complete applies only to interactive runs" {:id id}))
    (weaver/update! rt id {:attributes {:harness/result result}})))

(defn retry!
  "Reconstruct and reset one failed run, applying replacement options."
  [rt id {:keys [harness cwd attributes]}]
  (let [run (require-phase (require-run rt id) "failed")
        old-attrs (:attributes run)
        old-generated (normalize-overlay (attr-get run :harness/generated))
        old-overrides (normalize-overlay (attr-get run :harness/overrides))
        requested (or harness (attr-get run :harness/alias))
        explicit-alias? (some? harness)
        resolved (try
                   (resolve-harness rt requested)
                   (catch Exception e
                     (if explicit-alias?
                       (throw e)
                       nil)))
        generated (or (:generated resolved) old-generated)
        concrete (or (:harness resolved) (attr-get run :harness/harness))
        _ (concrete-harness rt concrete)
        call-overrides (normalize-overlay attributes)
        overrides (reduce-kv (fn [m k v] (if (nil? v) (dissoc m k) (assoc m k v)))
                             old-overrides call-overrides)
        effective (merge generated overrides)
        old-overlay-keys (set (filter overlay-key? (keys old-attrs)))
        all-overlay-keys (into old-overlay-keys (keys effective))
        overlay-delta (into {} (map (fn [k] [k (get effective k)]) all-overlay-keys))
        generated-delta (into {}
                              (map (fn [k] [k (get generated k)]))
                              (into (set (keys old-generated)) (keys generated)))
        overrides-delta (into {}
                              (map (fn [k] [k (get overrides k)]))
                              (into (set (keys old-overrides)) (keys overrides)))
        resumed? (some? (attr-get run :harness/resumes))]
    (weaver/update!
     rt id
     {:attributes
      (merge overlay-delta
             {:harness/alias requested
              :harness/harness concrete
              :harness/cwd (or cwd (attr-get run :harness/cwd))
              :harness/phase "pending"
              :harness/generated generated-delta
              :harness/overrides overrides-delta
              :harness/session-id (if resumed?
                                    (attr-get run :harness/session-id)
                                    (str (UUID/randomUUID)))
              :harness/error nil
              :harness/result nil
              :harness/exit-code nil})})))

(defn resume!
  "Create a new run continuing one successful provider session."
  [rt id {:keys [prompt cwd attributes mode title]}]
  (let [run (require-phase (require-run rt id) "done")
        session-id (attr-get run :harness/session-id)
        retained (normalize-overlay (attr-get run :harness/overrides))
        replacements (normalize-overlay attributes)
        overrides (reduce-kv (fn [m k v] (if (nil? v) (dissoc m k) (assoc m k v)))
                             retained replacements)]
    (when (str/blank? session-id)
      (fail! "Resume predecessor has no session id" {:id id}))
    (create! rt {:harness (attr-get run :harness/alias)
                 :mode (or mode (attr-get run :harness/mode))
                 :prompt prompt
                 :cwd (or cwd (attr-get run :harness/cwd))
                 :attributes overrides
                 :title title
                 :resumes id
                 :session-id session-id})))

(defn reconcile
  "Initialize runtime-local registry and declare core vocabulary."
  [{:keys [runtime] :as ctx}]
  (case (get-in ctx [:module/contribution :status])
    :removed {:reconciled :removed}
    (do
      (registry runtime)
      (vocab/declare! runtime
                      {:kind :attr-namespace
                       :name "harness"
                       :owner :ct.spools/harness-core
                       :keys core-keys
                       :doc "Provider-neutral harness run lifecycle and reconstruction attributes."})
      {:reconciled :applied})))

(def spool {:reconcile 'reconcile})
