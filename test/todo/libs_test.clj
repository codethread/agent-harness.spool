(ns todo.libs-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [atom.libs.alpha :as libs]
            [todo.daemon.config :as daemon-config]
            [todo.daemon.runtime :as runtime]
            [todo.db-test :as db-test]
            [todo.repl :as repl]))

(defn- temp-config-dir []
  (doto (.toFile (java.nio.file.Files/createTempDirectory
                  (.toPath (io/file "/tmp"))
                  "atom-libs-config"
                  (make-array java.nio.file.attribute.FileAttribute 0)))
    (.mkdirs)))

(defn- delete-recursive [file]
  (doseq [child (reverse (file-seq file))]
    (.delete child)))

(defn- with-runtime [f]
  (let [db-file (db-test/temp-db-file)
        config-dir (temp-config-dir)]
    (try
      (let [rt (runtime/start! db-file {:world (daemon-config/world (.getCanonicalPath config-dir))})]
        (try
          (f rt config-dir)
          (finally
            (runtime/stop! rt))))
      (finally
        (db-test/delete-sqlite-family! db-file)
        (delete-recursive config-dir)))))

(defn- write-libs! [config-dir content]
  (spit (io/file config-dir "libs.edn") content))

(deftest approved-returns-empty-libs-when-file-is-missing
  (with-runtime
    (fn [_ _]
      (is (= {:libs {}} (libs/approved))))))

(deftest approved-fails-when-libs-edn-is-not-a-file
  (with-runtime
    (fn [_ config-dir]
      (.mkdirs (io/file config-dir "libs.edn"))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"malformed or unreadable"
                            (libs/approved))))))

(deftest approved-normalizes-relative-and-absolute-roots
  (with-runtime
    (fn [_ config-dir]
      (let [relative-root (io/file config-dir "libs" "demo")
            absolute-root (io/file config-dir "external" "abs")]
        (.mkdirs relative-root)
        (.mkdirs absolute-root)
        (write-libs! config-dir
                     (pr-str {:libs {'demo/relative {:local/root "libs/demo"}
                                     'demo/absolute {:local/root (.getAbsolutePath absolute-root)}}}))
        (is (= {:libs {'demo/absolute {:local/root (.getAbsolutePath absolute-root)
                                       :root (.getCanonicalPath absolute-root)}
                       'demo/relative {:local/root "libs/demo"
                                       :root (.getCanonicalPath relative-root)}}}
               (libs/approved)))))))

(deftest approved-canonicalizes-symlink-roots
  (with-runtime
    (fn [_ config-dir]
      (let [target (io/file config-dir "libs" "target")
            link (io/file config-dir "libs" "link")]
        (.mkdirs target)
        (java.nio.file.Files/createSymbolicLink (.toPath link) (.toPath target)
                                                (make-array java.nio.file.attribute.FileAttribute 0))
        (write-libs! config-dir (pr-str {:libs {'demo/link {:local/root "libs/link"}}}))
        (is (= {:libs {'demo/link {:local/root "libs/link"
                                   :root (.getCanonicalPath target)}}}
               (libs/approved)))))))

(deftest approved-does-not-reject-missing-local-roots
  (with-runtime
    (fn [_ config-dir]
      (let [missing (io/file config-dir "libs" "missing")]
        (write-libs! config-dir (pr-str {:libs {'demo/missing {:local/root "libs/missing"}}}))
        (is (= {:libs {'demo/missing {:local/root "libs/missing"
                                      :root (.getCanonicalPath missing)}}}
               (libs/approved)))))))

(deftest approved-routes-through-connected-helper-context
  (with-redefs [runtime/current-runtime (atom nil)
                repl/connected-config-dir (constantly "/tmp/atom-connected-world")
                todo.client/call-world (fn [config-dir opts op]
                                         {:config-dir config-dir
                                          :opts opts
                                          :op op})]
    (is (= {:config-dir "/tmp/atom-connected-world"
            :opts {}
            :op :approved-libs}
           (libs/approved)))))

(deftest approved-fails-loudly-on-structural-errors
  (with-runtime
    (fn [_ config-dir]
      (doseq [[label content pattern]
              [["malformed EDN" "{:libs" #"malformed or unreadable"]
               ["unknown top-level key" (pr-str {:libs {} :extra true}) #"unknown top-level keys"]
               ["missing :libs" (pr-str {}) #"requires :libs map"]
               ["non-map :libs" (pr-str {:libs []}) #"requires :libs map"]
               ["non-symbol coordinate" (pr-str {:libs {"demo/lib" {:local/root "libs/demo"}}}) #"coordinate must be a symbol"]
               ["non-map entry" (pr-str {:libs {'demo/lib "libs/demo"}}) #"entry must be a map"]
               ["unknown per-lib key" (pr-str {:libs {'demo/lib {:local/root "libs/demo" :extra true}}}) #"unknown keys"]
               ["missing root" (pr-str {:libs {'demo/lib {}}}) #"requires non-blank string"]
               ["non-string root" (pr-str {:libs {'demo/lib {:local/root 1}}}) #"requires non-blank string"]
               ["blank root" (pr-str {:libs {'demo/lib {:local/root "  "}}}) #"requires non-blank string"]]]
        (testing label
          (write-libs! config-dir content)
          (is (thrown-with-msg? clojure.lang.ExceptionInfo pattern (libs/approved))))))))
