(ns todo.repl
  (:require [todo.db :as db]))

(defonce ^:private active-datasource (atom nil))

(defn- ds []
  (or @active-datasource
      (throw (ex-info "No todo database is open. Call (open! \"path/to/todo.sqlite\") before using todo.repl helpers."
                      {:helper 'open!}))))

(def ^:private json-columns #{:attributes :edge_attributes :blockers})

(defn- unpack-json [row]
  (reduce-kv (fn [m k v]
               (assoc m k (if (and (json-columns k) (string? v))
                            (db/<-json v)
                            v)))
             {}
             row))

(defn- unpack [x]
  (cond
    (map? x) (unpack-json x)
    (sequential? x) (mapv unpack-json x)
    :else x))

(defn open! [db-file]
  (reset! active-datasource (db/datasource db-file)))

(defn init! []
  (db/init! (ds)))

(defn task!
  ([title]
   (task! title {}))
  ([title attributes]
   (unpack (db/add-task! (ds) {:title title :attributes attributes}))))

(defn edge!
  ([from to type]
   (edge! from to type {}))
  ([from to type attributes]
   (unpack (db/add-edge! (ds) {:from from :to to :type type :attributes attributes}))))

(defn depends!
  ([from to]
   (depends! from to {}))
  ([from to attributes]
   (edge! from to "depends-on" attributes)))

(defn done! [id]
  (unpack (db/update-task-status! (ds) id "done")))

(defn tasks []
  (unpack (db/all-tasks (ds))))

(defn task [id]
  (unpack (db/get-task (ds) id)))

(defn deps [id]
  (unpack (db/task-dependencies (ds) id)))

(defn transitive-deps [id]
  (unpack (db/transitive-dependencies (ds) id)))

(defn blocking [id]
  (unpack (db/blocking-tasks (ds) id)))

(defn ready []
  (unpack (db/ready-tasks (ds))))

(defn by-attr [attr-key attr-value]
  (unpack (db/tasks-by-attribute (ds) attr-key attr-value)))

(defn graph [id]
  (unpack (db/related-tasks (ds) id)))
