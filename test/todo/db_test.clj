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

(deftest task-upsert-and-attribute-validation
  (with-db
    (fn [ds]
      (testing "tasks can be created and upserted with open-ended JSON attributes"
        (is (= {:id "design"
                :title "Sketch model"
                :attributes {:priority "high"}}
               (-> (db/add-task! ds {:id "design"
                                      :title "Sketch model"
                                      :attributes {:priority "high"}})
                   (update :attributes db/<-json))))
        (is (= {:id "design"
                :title "Refine model"
                :attributes {:status "done"}}
               (-> (db/add-task! ds {:id "design"
                                      :title "Refine model"
                                      :attributes {:status "done"}})
                   (update :attributes db/<-json)))))
      (testing "invalid task inputs fail before SQLite writes"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Invalid task"
                              (db/add-task! ds {:id "" :title "No id" :attributes {}})))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo
                              #"Invalid task"
                              (db/add-task! ds {:id "bad-attrs"
                                                :title "Bad attrs"
                                                :attributes [:nope]})))))))

(deftest dependency-readiness-semantics
  (with-db
    (fn [ds]
      (db/add-task! ds {:id "design" :title "Design" :attributes {:status "done"}})
      (db/add-task! ds {:id "schema" :title "Schema" :attributes {:status "todo"}})
      (db/add-task! ds {:id "docs" :title "Docs" :attributes {:status "todo"}})
      (db/add-edge! ds {:from "schema"
                        :to "design"
                        :type "depends-on"
                        :attributes {:reason "follows design"}})
      (db/add-edge! ds {:from "docs" :to "schema" :type "depends-on" :attributes {}})

      (testing "ready tasks are incomplete tasks with all direct dependencies done"
        (is (= ["schema"] (mapv :id (db/ready-tasks ds))))
        (db/update-task-status! ds "schema" "done")
        (is (= ["docs"] (mapv :id (db/ready-tasks ds))))))))

(deftest graph-queries-follow-depends-on-direction
  (with-db
    (fn [ds]
      (doseq [task [{:id "design" :title "Design" :attributes {}}
                    {:id "schema" :title "Schema" :attributes {}}
                    {:id "docs" :title "Docs" :attributes {}}]]
        (db/add-task! ds task))
      (db/add-edge! ds {:from "schema" :to "design" :type "depends-on" :attributes {}})
      (db/add-edge! ds {:from "docs" :to "schema" :type "depends-on" :attributes {}})

      (is (= ["design"] (mapv :id (db/task-dependencies ds "schema"))))
      (is (= ["schema"] (mapv :id (db/blocking-tasks ds "design"))))
      (is (= ["design" "schema"] (mapv :id (db/transitive-dependencies ds "docs")))))))
