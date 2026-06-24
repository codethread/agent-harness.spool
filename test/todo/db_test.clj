(ns todo.db-test
  (:require [clojure.test :refer [deftest is testing]]
            [todo.db :as db]))

(defn delete-sqlite-family! [db-file]
  (doseq [suffix ["" "-journal" "-wal" "-shm"]]
    (.delete (java.io.File. (str db-file suffix)))))

(defn temp-db-file []
  (let [file (java.io.File/createTempFile "todo-db-test" ".sqlite")]
    (.delete file)
    (.getAbsolutePath file)))

(defn with-db [f]
  (let [db-file (temp-db-file)
        ds (db/datasource db-file)]
    (try
      (db/init! ds)
      (f ds)
      (finally
        (delete-sqlite-family! db-file)))))

(deftest task-creation-and-attribute-validation
  (with-db
    (fn [ds]
      (testing "tasks are created with generated ids and open-ended JSON attributes"
        (let [task (-> (db/add-task! ds {:title "Sketch model"
                                         :attributes {:priority "high"}})
                       (update :attributes db/<-json))]
          (is (re-matches #"[a-z0-9]+" (:id task)))
          (is (= {:title "Sketch model"
                  :attributes {:priority "high"}}
                 (dissoc task :id)))))
      (testing "generated task ids are unique across task creation"
        (let [first-task (db/add-task! ds {:title "First" :attributes {}})
              second-task (db/add-task! ds {:title "Second" :attributes {}})]
          (is (not= (:id first-task) (:id second-task)))))
      (testing "invalid task inputs fail before SQLite writes"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Invalid task"
                              (db/add-task! ds {:title "" :attributes {}})))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Invalid task"
                              (db/add-task! ds {:title "Bad attrs"
                                                :attributes [:nope]})))))))

(deftest dependency-readiness-semantics
  (with-db
    (fn [ds]
      (let [design (:id (db/add-task! ds {:title "Design" :attributes {:status "done"}}))
            schema (:id (db/add-task! ds {:title "Schema" :attributes {:status "todo"}}))
            docs (:id (db/add-task! ds {:title "Docs" :attributes {:status "todo"}}))]
        (db/add-edge! ds {:from schema
                          :to design
                          :type "depends-on"
                          :attributes {:reason "follows design"}})
        (db/add-edge! ds {:from docs :to schema :type "depends-on" :attributes {}})

        (testing "ready tasks are incomplete tasks with all direct dependencies done"
          (is (= [schema] (mapv :id (db/ready-tasks ds))))
          (db/update-task-status! ds schema "done")
          (is (= [docs] (mapv :id (db/ready-tasks ds)))))))))

(deftest graph-queries-follow-depends-on-direction
  (with-db
    (fn [ds]
      (let [design (:id (db/add-task! ds {:title "Design" :attributes {}}))
            schema (:id (db/add-task! ds {:title "Schema" :attributes {}}))
            docs (:id (db/add-task! ds {:title "Docs" :attributes {}}))]
        (db/add-edge! ds {:from schema :to design :type "depends-on" :attributes {}})
        (db/add-edge! ds {:from docs :to schema :type "depends-on" :attributes {}})

        (is (= [design] (mapv :id (db/task-dependencies ds schema))))
        (is (= [schema] (mapv :id (db/blocking-tasks ds design))))
        (is (= #{design schema} (set (mapv :id (db/transitive-dependencies ds docs)))))))))
