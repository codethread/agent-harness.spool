(ns user
  (:require [todo.repl :refer :all]))

(def demo-db "/tmp/todo-demo.sqlite")

(defn demo!
  "Open and initialize the default demo database for REPL exploration."
  []
  (open! demo-db)
  (init!)
  {:database demo-db
   :status :ready})

(defn reset-demo!
  "Delete, reopen, and initialize the default demo database."
  []
  (let [f (java.io.File. demo-db)]
    (when (.exists f)
      (when-not (.delete f)
        (throw (ex-info "Could not delete demo database" {:path demo-db}))))
    (demo!)))

(defn seed-demo!
  "Reset the demo database and add a small dependency graph."
  []
  (reset-demo!)
  (let [design (task! "Sketch model" {:status "done" :priority "high" :demo-id "design"})
        docs (task! "Write docs" {:status "todo" :owner "agent" :demo-id "docs"})
        impl (task! "Build feature" {:status "todo" :owner "agent" :demo-id "impl"})]
    (depends! (:id docs) (:id design))
    (depends! (:id impl) (:id docs))
    (tasks)))

(comment
  (demo!)
  (seed-demo!)
  (ready)
  (def docs-id (:id (first (by-attr :demo-id "docs"))))
  (done! docs-id)
  (ready))
