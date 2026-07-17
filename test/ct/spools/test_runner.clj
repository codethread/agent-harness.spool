(ns ct.spools.test-runner
  (:require [clojure.test :as test]
            [ct.spools.agent-run-test]
            [ct.spools.bench-metrics-test]
            [ct.spools.bench-test]
            [ct.spools.delegation-test]
            [ct.spools.subagent-test]))

(def test-namespaces
  '[ct.spools.agent-run-test
    ct.spools.bench-metrics-test
    ct.spools.bench-test
    ct.spools.delegation-test
    ct.spools.subagent-test])

(defn -main [& _]
  (let [summary (apply test/run-tests test-namespaces)]
    (System/exit (if (pos? (+ (:fail summary) (:error summary))) 1 0))))
