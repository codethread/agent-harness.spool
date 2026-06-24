(ns todo.repl
  (:require [todo.client :as client]))

(defonce ^:private active-db-file (atom nil))

(defn- db-file []
  (or @active-db-file
      (throw (ex-info "No todo daemon is open. Start a daemon for the database, then call (open! \"path/to/todo.sqlite\") before using todo.repl helpers."
                      {:helper 'open!}))))

(defn open! [db-file]
  (reset! active-db-file nil)
  (client/status db-file)
  (reset! active-db-file db-file))

(defn init! []
  (client/init (db-file)))

(defn task!
  ([title]
   (task! title {}))
  ([title attributes]
   (client/add (db-file) {:title title :attributes attributes}))
  ([title status attributes]
   (client/add (db-file) {:title title :status status :attributes attributes})))

(defn update!
  ([id patch]
   (client/update (db-file) id patch)))

(defn task [id]
  (client/show (db-file) id))

(defn tasks []
  (client/list (db-file)))

(defn ready []
  (client/ready (db-file)))
