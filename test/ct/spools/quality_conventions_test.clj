(ns ct.spools.quality-conventions-test
  "Focused tests for the repository convention checks."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [quality.conventions-check :as conventions]))

(defn- temp-dir
  []
  (.toFile
   (java.nio.file.Files/createTempDirectory
    "agent-harness-quality-test"
    (make-array java.nio.file.attribute.FileAttribute 0))))

(deftest authored-source-checks-report-portable-risk-patterns
  (let [dir (temp-dir)
        source (io/file dir "sample.clj")]
    (spit source
          (str "(ns sample \"sample\")\n"
               "(def spool {:contribute identity})\n"
               "(def json \"{\\\"name\\\":\\\"agent\\\"}\")\n"
               "(def wide \"" (str/join (repeat 181 "x")) "\")\n"))
    (let [findings (vec (conventions/source-findings [(.getPath dir)]))]
      (is (some #(re-find #"public `spool`" %) findings))
      (is (some #(re-find #"exceeds 180 columns" %) findings)))
    (is (conventions/reproducible-json? "{\"name\":\"agent\"}"))
    (is (not (conventions/reproducible-json? "{\"name\": \"agent\"}")))))

(deftest analysis-checks-report-public-doc-and-tier-violations
  (let [analysis {:namespace-definitions [{:filename "bench/src/x.clj"
                                           :name 'x
                                           :doc "X."}]
                  :var-definitions [{:filename "bench/src/x.clj"
                                     :row 3
                                     :name 'public-api
                                     :private false}]
                  :namespace-usages [{:filename "bench/src/x.clj"
                                      :row 2
                                      :from 'x
                                      :to 'skein.core.db}]}
        findings (vec (conventions/analysis-findings analysis))]
    (is (some #(re-find #"public var `public-api`" %) findings))
    (is (some #(re-find #"uses internal namespace `skein.core.db`" %) findings))))
