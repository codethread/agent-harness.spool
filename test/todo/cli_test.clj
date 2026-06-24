(ns todo.cli-test
  (:require [clojure.test :refer [deftest is testing]]
            [todo.cli :as cli]
            [todo.db :as db]))

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

(deftest parses-repeatable-command-attributes
  (testing "repeatable CLI attributes are string-valued and merged into a map"
    (is (= {:priority "high" :owner "agent"}
           (cli/parse-attrs ["--attr" "priority=high" "--attr" "owner=agent"] "summary")))))
