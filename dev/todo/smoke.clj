(ns todo.smoke
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [todo.db :as db]
            [todo.repl :as repl]))

(def smoke-db "smoke.sqlite")
(def cli-smoke-db "smoke-cli.sqlite")

(def seed-tasks
  [{:ref :design :title "Sketch task graph model" :attributes {:priority "high" :due-date "2026-07-01" :status "done"}}
   {:ref :schema :title "Create SQLite schema" :attributes {:priority "high" :due-date "2026-07-02" :status "done"}}
   {:ref :tui :title "Build terminal UI" :attributes {:priority "medium" :due-date "2026-07-05" :status "doing"}}
   {:ref :docs :title "Write usage notes" :attributes {:priority "low" :due-date "2026-07-08" :status "todo"}}
   {:ref :release :title "Package MVP" :attributes {:priority "high" :due-date "2026-07-10" :status "todo" :estimate-hours 2}}])

(def seed-edges
  [{:from :schema :to :design :type "depends-on" :attributes {:reason "schema follows model"}}
   {:from :tui :to :schema :type "depends-on" :attributes {:reason "TUI persists tasks"}}
   {:from :docs :to :tui :type "depends-on" :attributes {:reason "document real commands"}}
   {:from :release :to :docs :type "depends-on" :attributes {:reason "ship docs"}}
   {:from :release :to :tui :type "depends-on" :attributes {:reason "ship UI"}}
   {:from :docs :to :design :type "related-to" :attributes {:section "architecture"}}])

(defn section [title rows]
  (println "\n--" title "--")
  (doseq [row rows] (println row)))

(defn ids [rows]
  (mapv :id rows))

(defn titles [rows]
  (mapv :title rows))

(defn delete-sqlite-family! [db-file]
  (doseq [suffix ["" "-journal" "-wal" "-shm"]]
    (.delete (java.io.File. (str db-file suffix)))))

(defn run-cli-with-input! [db-file input & args]
  (let [command (into ["clojure" "-M:todo" "--db" db-file] args)
        process (-> (ProcessBuilder. command)
                    (.redirectErrorStream true)
                    (.start))]
    (with-open [writer (java.io.OutputStreamWriter. (.getOutputStream process))]
      (.write writer (or input "")))
    (let [output (slurp (.getInputStream process))
          exit-code (.waitFor process)]
      (assert (= 0 exit-code)
              (str "CLI command succeeds: " (pr-str command) "\n" output))
      output)))

(defn run-cli! [db-file & args]
  (apply run-cli-with-input! db-file nil args))

(defn cli-add! [db-file title & args]
  (str/trim (apply run-cli! db-file "add" title args)))

(defn assert= [expected actual message]
  (assert (= expected actual)
          (str message "\nexpected: " (pr-str expected) "\nactual: " (pr-str actual))))

(defn -main [& [db-file]]
  (let [ds (db/datasource (or db-file smoke-db))
        cli-db (if db-file (str db-file ".cli") cli-smoke-db)]
    (delete-sqlite-family! cli-db)
    (run-cli! cli-db "init")
    (let [design (cli-add! cli-db "Sketch task graph model" "--attr" "priority=high" "--attr" "due-date=2026-07-01" "--attr" "status=done")
          schema (cli-add! cli-db "Create SQLite schema" "--attr" "priority=high" "--attr" "due-date=2026-07-02" "--attr" "status=todo" "--link" (str "depends-on:" design))
          docs (cli-add! cli-db "Write usage notes" "--attr" "priority=low" "--attr" "due-date=2026-07-08" "--attr" "status=todo" "--attr" "owner=agent" "--link" (str "depends-on:" schema))]
      (assert= ["Create SQLite schema"] (titles (read-string (run-cli! cli-db "--format" "edn" "ready"))) "CLI process ready sees tasks with done dependencies")
      (assert= ["Write usage notes"] (titles (json/read-str (run-cli! cli-db "--format" "json" "by-attr" "owner" "agent") :key-fn keyword)) "CLI process by-attr queries JSON1 task attributes")
      (assert= ["Sketch task graph model"] (titles (read-string (run-cli! cli-db "--format" "edn" "deps" schema))) "CLI process deps queries graph relationships")
      (assert= #{"Sketch task graph model" "Create SQLite schema"} (set (titles (read-string (run-cli! cli-db "--format" "edn" "transitive-deps" docs)))) "CLI process transitive-deps traverses graph relationships")
      (run-cli! cli-db "done" schema)
      (assert= ["Write usage notes"] (titles (read-string (run-cli! cli-db "--format" "edn" "ready"))) "CLI process done updates status used by ready")
      (let [batch-result (read-string (run-cli-with-input!
                                       cli-db
                                       "[{:ref batch-design :title \"Batch design\" :attributes {:status \"done\"}} {:ref batch-docs :title \"Batch docs\" :edges [{:type \"depends-on\" :to batch-design}]}]"
                                       "--format" "edn" "batch"))
            batch-refs (:refs batch-result)]
        (assert= ["Batch design"]
                 (titles (read-string (run-cli! cli-db "--format" "edn" "deps" (get batch-refs "batch-docs"))))
                 "CLI process batch resolves symbolic refs")))
    (section "agent CLI process ready" (read-string (run-cli! cli-db "--format" "edn" "ready")))
    (section "agent CLI process by-attr owner=agent" (json/read-str (run-cli! cli-db "--format" "json" "by-attr" "owner" "agent") :key-fn keyword))
    (db/reset-db! ds)
    (let [task-ids (into {}
                         (map (fn [{:keys [ref title attributes]}]
                                [ref (:id (db/add-task! ds {:title title :attributes attributes}))]))
                         seed-tasks)]
      (doseq [{:keys [from to type attributes]} seed-edges]
        (db/add-edge! ds {:from (task-ids from) :to (task-ids to) :type type :attributes attributes}))
      (assert= "Sketch task graph model" (:title (db/get-task ds (task-ids :design))) "get-task retrieves one seeded task")
      (db/update-task-attributes! ds (task-ids :docs) {:owner "agent"})
      (assert= {:priority "low" :due-date "2026-07-08" :status "todo" :owner "agent"}
               (db/<-json (:attributes (db/get-task ds (task-ids :docs))))
               "update-task-attributes! patches JSON attributes")
      (assert= ["Build terminal UI"] (titles (db/ready-tasks ds)) "only tui is ready before tui is done")
      (db/update-task-status! ds (task-ids :tui) "done")
      (assert= ["Write usage notes"] (titles (db/ready-tasks ds)) "docs becomes ready after tui is done")
      (assert= ["Package MVP"] (titles (db/blocking-tasks ds (task-ids :docs))) "release directly depends on docs")
      (assert= #{"Sketch task graph model" "Write usage notes" "Create SQLite schema" "Build terminal UI"}
               (set (titles (db/transitive-dependencies ds (task-ids :release))))
               "release transitive dependencies")
      (assert= ["Package MVP"] (titles (db/tasks-by-attribute ds :estimate-hours 2)) "arbitrary JSON attribute lookup")
      (section "all tasks" (db/all-tasks ds))
      (section "high priority tasks via JSON1" (db/tasks-by-priority ds "high"))
      (section "tasks due by 2026-07-05 via JSON1" (db/tasks-due-before ds "2026-07-05"))
      (section "blocked tasks from edge table" (db/blocked-tasks ds))
      (section "release dependencies" (db/task-dependencies ds (task-ids :release)))
      (section "release transitive dependencies" (db/transitive-dependencies ds (task-ids :release)))
      (section "tasks blocked by docs" (db/blocking-tasks ds (task-ids :docs)))
      (section "estimate-hours=2 via arbitrary JSON attribute" (db/tasks-by-attribute ds :estimate-hours 2))
      (section "docs graph edges" (db/related-tasks ds (task-ids :docs))))
    (let [repl-db (str (or db-file smoke-db) ".repl")
          expected-repl-helpers '#{open! init! task! depends! edge! done! tasks task deps transitive-deps blocking ready by-attr graph}]
      (assert= expected-repl-helpers (set (keys (select-keys (ns-publics 'todo.repl) expected-repl-helpers))) "todo.repl exposes the MVP helper vocabulary")
      (try
        (repl/ready)
        (throw (ex-info "Expected todo.repl helpers to fail before open!" {}))
        (catch clojure.lang.ExceptionInfo e
          (assert (re-find #"No todo database is open" (.getMessage e)))))
      (.delete (java.io.File. repl-db))
      (repl/open! repl-db)
      (repl/init!)
      (let [a (:id (repl/task! "First task" {:status "done"}))
            b (:id (repl/task! "Second task" {:status "todo"}))]
        (repl/depends! b a)
        (assert= ["Second task"] (titles (repl/ready)) "todo.repl ready returns tasks with done dependencies")
        (repl/done! b)
        (assert= "done" (:status (:attributes (repl/task b))) "todo.repl done! updates status")))
    (println "\nSmoke database:" (or db-file smoke-db))))
