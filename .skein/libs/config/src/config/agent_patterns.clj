(ns config.agent-patterns
  "Agent-oriented weave patterns for repo-local Skein workflows."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [skein.patterns.alpha :as patterns]))

(s/def ::non-blank-string (s/and string? (complement str/blank?)))
(s/def ::feature ::non-blank-string)
(s/def ::title ::non-blank-string)
(s/def ::key ::non-blank-string)
(s/def ::kind #{"task" "review"})
(s/def ::body ::non-blank-string)
(s/def ::hitl boolean?)
(s/def ::depends_on (s/coll-of ::key :kind vector?))
(s/def ::task (s/keys :req-un [::key ::title]
                      :opt-un [::body ::kind ::hitl ::depends_on]))
(s/def ::tasks (s/coll-of ::task :kind vector? :min-count 1))
(s/def ::agent-plan-input (s/keys :req-un [::feature ::title ::tasks]))

(defn- ref-symbol
  "Return the batch ref symbol for a user-supplied task key."
  [key]
  (symbol key))

(defn- plan-strand
  "Return the feature/plan strand for an agent-plan input."
  [{:keys [feature title body tasks]}]
  {:ref 'plan
   :title title
   :attributes (cond-> {:feature feature
                        :kind "plan"}
                 body (assoc :body body))
   :edges (mapv (fn [{:keys [key]}]
                  {:type "parent-of" :to (ref-symbol key)})
                tasks)})

(defn- task-strand
  "Return one task/review strand for an agent-plan input task."
  [feature {:keys [key title body kind hitl depends_on]}]
  (cond-> {:ref (ref-symbol key)
           :title title
           :attributes (cond-> {:feature feature
                                :kind (or kind "task")}
                         body (assoc :body body)
                         hitl (assoc :hitl true))}
    (seq depends_on)
    (assoc :edges (mapv (fn [dep]
                          {:type "depends-on" :to (ref-symbol dep)})
                        depends_on))))

(defn agent-plan
  "Create a feature strand plus task/review children for agent work.

  Input requires `feature`, `title`, and a non-empty `tasks` vector. Optional
  `body` fields carry issue-style context on the plan or tasks. Task keys become
  local batch refs. Each task may set `kind` to `task` or `review`, `hitl` to
  true, and `depends_on` to a vector of task keys. The generated plan has
  `kind=plan`, children share `feature`, the plan has `parent-of` edges to each
  child, and task dependencies become `depends-on` edges."
  [{:keys [input]}]
  (into [(plan-strand input)]
        (map #(task-strand (:feature input) %))
        (:tasks input)))

(defn install!
  "Register repo-local agent weave patterns."
  []
  (patterns/register-pattern!
   'agent-plan
   "Create a feature strand plus task/review children for agent work. Input: {feature,title,body?,tasks:[{key,title,body?,kind?,hitl?,depends_on?}]}. Use body for delegated work context. Use `strand pattern explain agent-plan` for the spec-backed contract."
   'config.agent-patterns/agent-plan
   ::agent-plan-input))
