(ns todo.db
  (:import [java.security SecureRandom])
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.result-set :as rs]
            [todo.specs :as specs]))

(def default-db-file "todo.sqlite")

(def ^:private id-alphabet "abcdefghijklmnopqrstuvwxyz0123456789")
(def ^:private id-length 5)
(def ^:private max-id-attempts 32)
(def ^:private secure-random (SecureRandom.))

(defn generate-id []
  (apply str
         (repeatedly id-length
                     #(nth id-alphabet (.nextInt secure-random (count id-alphabet))))))

(defn datasource
  ([] (datasource default-db-file))
  ([db-file]
   (jdbc/get-datasource {:jdbcUrl (str "jdbc:sqlite:" db-file)})))

(defn execute! [ds sql-params]
  (jdbc/execute! ds sql-params {:builder-fn rs/as-unqualified-lower-maps}))

(defn execute-one! [ds sql-params]
  (jdbc/execute-one! ds sql-params {:builder-fn rs/as-unqualified-lower-maps}))

(defn- require-valid! [spec value message]
  (when-not (s/valid? spec value)
    (throw (ex-info message {:value value :explain (s/explain-str spec value)})))
  value)

(defn ->json [m]
  (require-valid! ::specs/attributes m "Attributes must be nil or a map that encodes to a JSON object")
  (json/write-str (or m {})))

(defn <-json [s]
  (json/read-str (or s "{}") :key-fn keyword))

(def schema-sql
  [["PRAGMA foreign_keys = ON"]
   ["CREATE TABLE IF NOT EXISTS tasks (
       id TEXT PRIMARY KEY,
       title TEXT NOT NULL,
       attributes TEXT NOT NULL DEFAULT '{}',
       CHECK (json_valid(attributes))
     )"]
   ["CREATE TABLE IF NOT EXISTS task_edges (
       from_task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
       to_task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
       edge_type TEXT NOT NULL,
       attributes TEXT NOT NULL DEFAULT '{}',
       PRIMARY KEY (from_task_id, to_task_id, edge_type),
       CHECK (edge_type IN ('depends-on', 'related-to', 'parent-of', 'supersedes')),
       CHECK (json_valid(attributes))
     )"]
   ["CREATE INDEX IF NOT EXISTS idx_task_edges_to ON task_edges(to_task_id, edge_type)"]
   ["CREATE INDEX IF NOT EXISTS idx_tasks_priority ON tasks(json_extract(attributes, '$.priority'))"]
   ["CREATE INDEX IF NOT EXISTS idx_tasks_due_date ON tasks(json_extract(attributes, '$.due-date'))"]])

(defn init! [ds]
  (doseq [stmt schema-sql]
    (execute! ds stmt))
  ds)

(defn reset-db! [ds]
  (execute! ds ["DROP TABLE IF EXISTS task_edges"])
  (execute! ds ["DROP TABLE IF EXISTS tasks"])
  (init! ds))

(declare get-task add-edge!)

(def ^:private batch-task-keys #{:title :attributes :ref :edges})
(def ^:private batch-edge-keys #{:type :to :attributes})

(defn- json-compatible? [value]
  (cond
    (nil? value) true
    (string? value) true
    (number? value) true
    (true? value) true
    (false? value) true
    (map? value) (and (every? #(or (keyword? %) (string? %)) (keys value))
                      (every? json-compatible? (vals value)))
    (vector? value) (every? json-compatible? value)
    (sequential? value) (every? json-compatible? value)
    :else false))

(defn- require-json-object-encodable! [attributes context]
  (when-not (or (nil? attributes)
                (and (map? attributes) (json-compatible? attributes)))
    (throw (ex-info "Attributes must be nil or an EDN map that encodes to a JSON object"
                    {:context context :attributes attributes})))
  attributes)

(defn- require-no-unknown-keys! [m allowed context]
  (let [unknown (seq (remove allowed (keys m)))]
    (when unknown
      (throw (ex-info "Unknown keys in batch input" {:context context :keys (vec unknown)})))))

(defn- validate-batch-edge! [edge]
  (when-not (map? edge)
    (throw (ex-info "Batch edge must be a map" {:edge edge})))
  (require-no-unknown-keys! edge batch-edge-keys :edge)
  (when-not (s/valid? ::specs/edge-type (:type edge))
    (throw (ex-info "Batch edge :type must be one of the allowed edge types"
                    {:edge edge :allowed specs/allowed-edge-types})))
  (when-not (or (symbol? (:to edge)) (string? (:to edge)))
    (throw (ex-info "Batch edge :to must be a symbol batch ref or string durable id" {:edge edge})))
  (require-json-object-encodable! (:attributes edge) :edge)
  edge)

(defn- validate-batch-task! [task]
  (when-not (map? task)
    (throw (ex-info "Batch task must be a map" {:task task})))
  (require-no-unknown-keys! task batch-task-keys :task)
  (when-not (and (string? (:title task)) (not (str/blank? (:title task))))
    (throw (ex-info "Batch task :title must be a non-blank string" {:task task})))
  (when (and (contains? task :ref) (not (symbol? (:ref task))))
    (throw (ex-info "Batch task :ref must be a symbol" {:task task})))
  (require-json-object-encodable! (:attributes task) :task)
  (when-not (or (nil? (:edges task)) (vector? (:edges task)))
    (throw (ex-info "Batch task :edges must be a vector" {:task task})))
  (doseq [edge (:edges task)]
    (validate-batch-edge! edge))
  task)

(defn- validate-batch! [tasks]
  (when-not (vector? tasks)
    (throw (ex-info "Batch input must be a vector of task maps" {:value tasks})))
  (when (empty? tasks)
    (throw (ex-info "Batch input must contain at least one task" {})))
  (doseq [task tasks]
    (validate-batch-task! task))
  (let [refs (keep :ref tasks)
        duplicate-ref (->> refs frequencies (filter (fn [[_ n]] (> n 1))) ffirst)]
    (when duplicate-ref
      (throw (ex-info "Duplicate batch ref" {:ref duplicate-ref}))))
  tasks)

(defn- insert-task! [ds id title attributes]
  (execute-one! ds
                ["INSERT INTO tasks (id, title, attributes)
                  VALUES (?, ?, json(?))
                  RETURNING id, title, attributes"
                 id title (->json attributes)]))

(defn- unique-task-id-error? [^Exception e]
  (str/includes? (.getMessage e) "UNIQUE constraint failed: tasks.id"))

(defn add-task! [ds {:keys [title attributes] :as task}]
  (require-valid! ::specs/task-input task "Invalid task")
  (loop [attempt 1]
    (when (> attempt max-id-attempts)
      (throw (ex-info "Unable to generate unique task id" {:attempts max-id-attempts})))
    (let [id (generate-id)
          result (try
                   [:created (insert-task! ds id title attributes)]
                   (catch org.sqlite.SQLiteException e
                     (if (unique-task-id-error? e)
                       [:retry nil]
                       (throw e))))]
      (case (first result)
        :created (second result)
        :retry (recur (inc attempt))))))

(defn add-task-with-edges! [ds task edges]
  (jdbc/with-transaction [tx ds]
    (let [created-task (add-task! tx task)]
      (doseq [{:keys [to type attributes]} edges]
        (when-not (get-task tx to)
          (throw (ex-info "Link target task not found" {:to to :type type})))
        (add-edge! tx {:from (:id created-task)
                       :to to
                       :type type
                       :attributes attributes}))
      created-task)))

(defn add-task-batch! [ds tasks]
  (validate-batch! tasks)
  (jdbc/with-transaction [tx ds]
    (let [created (mapv (fn [{:keys [title attributes]}]
                          (add-task! tx {:title title :attributes attributes}))
                        tasks)
          refs (into {}
                     (keep (fn [[task created-task]]
                             (when-let [ref (:ref task)]
                               [(str ref) (:id created-task)])))
                     (map vector tasks created))]
      (doseq [[task created-task] (map vector tasks created)
              {:keys [to type attributes]} (:edges task)]
        (let [resolved-to (cond
                            (symbol? to) (or (get refs (str to))
                                             (throw (ex-info "Batch edge target ref not found; symbolic targets only resolve to batch refs, use a string for durable ids"
                                                             {:to to :type type})))
                            (string? to) (do
                                           (when-not (get-task tx to)
                                             (throw (ex-info "Batch edge target task not found" {:to to :type type})))
                                           to))]
          (add-edge! tx {:from (:id created-task)
                         :to resolved-to
                         :type type
                         :attributes attributes})))
      {:created created
       :refs refs})))

(defn- path-exists? [ds from to]
  (boolean
   (execute-one! ds
                 ["WITH RECURSIVE reachable(id) AS (
                     SELECT to_task_id
                     FROM task_edges
                     WHERE from_task_id = ?
                   UNION
                     SELECT e.to_task_id
                     FROM reachable r
                     JOIN task_edges e ON e.from_task_id = r.id
                   )
                   SELECT 1 AS found
                   FROM reachable
                   WHERE id = ?
                   LIMIT 1"
                  from to])))

(defn- require-acyclic-edge! [ds from to type]
  (when (= from to)
    (throw (ex-info "Task edges must not point to the same task" {:from from :to to :type type})))
  (when (path-exists? ds to from)
    (throw (ex-info "Task edge would create a cycle" {:from from :to to :type type}))))

(defn add-edge! [ds {:keys [from to type attributes] :as edge}]
  (require-valid! ::specs/edge-input edge "Invalid edge")
  (require-acyclic-edge! ds from to type)
  (execute-one! ds
                ["INSERT INTO task_edges (from_task_id, to_task_id, edge_type, attributes)
                  VALUES (?, ?, ?, json(?))
                  ON CONFLICT(from_task_id, to_task_id, edge_type) DO UPDATE SET attributes = excluded.attributes
                  RETURNING from_task_id, to_task_id, edge_type, attributes"
                 from to type (->json attributes)]))

(defn get-task [ds task-id]
  (execute-one! ds
                ["SELECT id, title, attributes FROM tasks WHERE id = ?"
                 task-id]))

(defn- require-updated-task [task-id row]
  (or row
      (throw (ex-info "Task not found" {:task-id task-id}))))

(defn update-task-attributes! [ds task-id attributes]
  (require-updated-task
   task-id
   (execute-one! ds
                 ["UPDATE tasks
                   SET attributes = json_patch(attributes, json(?))
                   WHERE id = ?
                   RETURNING id, title, attributes"
                  (->json attributes) task-id])))

(defn update-task-status! [ds task-id status]
  (require-updated-task
   task-id
   (execute-one! ds
                 ["UPDATE tasks
                   SET attributes = json_set(attributes, '$.status', ?)
                   WHERE id = ?
                   RETURNING id, title, attributes"
                  status task-id])))

(defn all-tasks [ds]
  (execute! ds ["SELECT id, title, attributes FROM tasks ORDER BY id"]))

(defn tasks-by-attribute [ds attr-key attr-value]
  (execute! ds
            ["SELECT t.id, t.title, t.attributes
              FROM tasks t
              WHERE EXISTS (
                SELECT 1
                FROM json_each(t.attributes) attr
                WHERE attr.key = ? AND attr.value = ?
              )
              ORDER BY t.id"
             (name attr-key) attr-value]))

(defn task-dependencies [ds task-id]
  (execute! ds
            ["SELECT dep.id, dep.title, dep.attributes, e.attributes AS edge_attributes
              FROM task_edges e
              JOIN tasks dep ON dep.id = e.to_task_id
              WHERE e.from_task_id = ? AND e.edge_type = 'depends-on'
              ORDER BY dep.id"
             task-id]))

(defn blocking-tasks [ds task-id]
  (execute! ds
            ["SELECT blocked.id, blocked.title, blocked.attributes, e.attributes AS edge_attributes
              FROM task_edges e
              JOIN tasks blocked ON blocked.id = e.from_task_id
              WHERE e.to_task_id = ? AND e.edge_type = 'depends-on'
              ORDER BY blocked.id"
             task-id]))

(defn blocked-tasks [ds]
  (execute! ds
            ["SELECT t.id, t.title, json_group_array(dep.id) AS blockers
              FROM tasks t
              JOIN task_edges e ON e.from_task_id = t.id AND e.edge_type = 'depends-on'
              JOIN tasks dep ON dep.id = e.to_task_id
              GROUP BY t.id, t.title
              ORDER BY t.id"]))

(defn ready-tasks [ds]
  (execute! ds
            ["SELECT t.id, t.title, t.attributes
              FROM tasks t
              WHERE json_extract(t.attributes, '$.status') IS NOT 'done'
                AND NOT EXISTS (
                  SELECT 1
                  FROM task_edges e
                  JOIN tasks dep ON dep.id = e.to_task_id
                  WHERE e.from_task_id = t.id
                    AND e.edge_type = 'depends-on'
                    AND json_extract(dep.attributes, '$.status') IS NOT 'done'
                )
              ORDER BY t.id"]))

(defn transitive-dependencies [ds task-id]
  (execute! ds
            ["WITH RECURSIVE deps(id, title, attributes) AS (
                SELECT dep.id, dep.title, dep.attributes
                FROM task_edges e
                JOIN tasks dep ON dep.id = e.to_task_id
                WHERE e.from_task_id = ? AND e.edge_type = 'depends-on'
              UNION
                SELECT dep.id, dep.title, dep.attributes
                FROM deps
                JOIN task_edges e ON e.from_task_id = deps.id AND e.edge_type = 'depends-on'
                JOIN tasks dep ON dep.id = e.to_task_id
              )
              SELECT id, title, attributes
              FROM deps
              WHERE id <> ?
              ORDER BY id"
             task-id task-id]))

(defn tasks-by-priority [ds priority]
  (execute! ds
            ["SELECT id, title, attributes
              FROM tasks
              WHERE json_extract(attributes, '$.priority') = ?
              ORDER BY json_extract(attributes, '$.due-date'), id"
             priority]))

(defn tasks-due-before [ds due-date]
  (execute! ds
            ["SELECT id, title, attributes
              FROM tasks
              WHERE json_extract(attributes, '$.due-date') IS NOT NULL
                AND json_extract(attributes, '$.due-date') <= ?
              ORDER BY json_extract(attributes, '$.due-date'), id"
             due-date]))

(defn related-tasks [ds task-id]
  (execute! ds
            ["SELECT e.edge_type, e.from_task_id, src.title AS from_title,
                     e.to_task_id, dst.title AS to_title, e.attributes
              FROM task_edges e
              JOIN tasks src ON src.id = e.from_task_id
              JOIN tasks dst ON dst.id = e.to_task_id
              WHERE e.from_task_id = ? OR e.to_task_id = ?
              ORDER BY e.edge_type, e.from_task_id, e.to_task_id"
             task-id task-id]))
