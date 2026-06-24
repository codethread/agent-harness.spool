(ns todo.cli-test
  (:require [clojure.test :refer [deftest is testing]]
            [todo.cli :as cli]
            [todo.db :as db]))

(defn delete-sqlite-family! [db-file]
  (doseq [suffix ["" "-journal" "-wal" "-shm"]]
    (.delete (java.io.File. (str db-file suffix)))))

(defn temp-db-file []
  (let [file (java.io.File/createTempFile "todo-cli-test" ".sqlite")]
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

(deftest parses-global-options-before-command
  (testing "global options are parsed before the command and command args are preserved"
    (is (= [{:db "/tmp/todo.sqlite" :format "json"}
            "ready"
            []]
           (let [[opts command args _summary]
                 (cli/parse-global-options ["--db" "/tmp/todo.sqlite" "--format" "json" "ready"])]
             [opts command args]))))
  (testing "options after the command are command arguments, not global options"
    (is (= [{:db db/default-db-file :format "human"}
            "ready"
            ["--format" "json"]]
           (let [[opts command args _summary]
                 (cli/parse-global-options ["ready" "--format" "json"])]
             [opts command args])))))

(deftest parses-repeatable-command-options
  (testing "repeatable CLI attributes are string-valued and merged into a map"
    (is (= {:priority "high" :owner "agent"}
           (cli/parse-attrs ["--attr" "priority=high" "--attr" "owner=agent"] "summary"))))
  (testing "repeatable CLI links parse edge type and target id"
    (is (= {:attrs {}
            :links [{:type "depends-on" :to "ue72w"}
                    {:type "related-to" :to "ab123"}]}
           (cli/parse-command-options ["--link" "depends-on:ue72w" "--link" "related-to:ab123"] "summary")))))

(deftest add-command-generates-id-and-creates-links
  (with-db
    (fn [ds]
      (let [design (cli/run-command! ds "add" ["Design" "--attr" "status=done"] "summary")
            review (cli/run-command! ds "add" ["Review" "--link" (str "depends-on:" (:id design))] "summary")]
        (is (re-matches #"[a-z0-9]+" (:id design)))
        (is (re-matches #"[a-z0-9]+" (:id review)))
        (is (= [(:id design)] (mapv :id (db/task-dependencies ds (:id review)))))))))

(deftest add-command-rolls-back-when-link-target-is-missing
  (with-db
    (fn [ds]
      (is (thrown? Exception
                   (cli/run-command! ds "add" ["Review" "--link" "depends-on:missing"] "summary")))
      (is (empty? (db/all-tasks ds))))))

(deftest batch-command-reads-edn-from-stdin
  (with-db
    (fn [ds]
      (let [result (binding [*in* (java.io.StringReader.
                                   "[{:ref design :title \"Design\"} {:ref docs :title \"Docs\" :edges [{:type \"depends-on\" :to design}]}]")]
                     (cli/run-command! ds "batch" [] "summary"))
            refs (:refs result)]
        (is (= #{"design" "docs"} (set (keys refs))))
        (is (= [(get refs "design")]
               (mapv :id (db/task-dependencies ds (get refs "docs")))))))))

(deftest batch-command-rejects-trailing-edn
  (with-db
    (fn [ds]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"exactly one EDN value"
                            (binding [*in* (java.io.StringReader. "[{:title \"A\"}] [{:title \"B\"}]")]
                              (cli/run-command! ds "batch" [] "summary")))))))
