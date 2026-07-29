(ns ct.spools.codex-harness-test
  "Provider-boundary tests for Codex argv and JSONL normalization."
  (:require [clojure.test :refer [deftest is testing]]
            [ct.spools.codex-harness :as codex]))

(def ^:private runtime {})
(def ^:private definition (codex/harness runtime))

(defn- run
  ([mode]
   (run mode {}))
  ([mode attributes]
   {:id "run"
    :title "Codex run"
    :state "active"
    :attributes
    (merge {:harness/mode mode
            :harness/session-id "provisional"
            :harness/prompt "Do the work"
            :harness.codex/model "gpt-test"
            :harness.codex/reasoning-effort "high"
            :harness.codex/extra-argv ["--skip-git-repo-check"]}
           attributes)}))

(deftest prepare-builds-new-and-resumed-commands
  (testing "new headless runs read their prompt from stdin"
    (is (= ["codex" "exec" "--json"
            "--model" "gpt-test"
            "--config" "model_reasoning_effort=high"
            "--skip-git-repo-check"]
           (codex/prepare runtime definition (run "headless")))))
  (testing "headless resume names the provider session"
    (is (= ["codex" "exec" "resume" "--json"
            "--model" "gpt-test"
            "--config" "model_reasoning_effort=high"
            "--skip-git-repo-check"
            "provisional"
            "-"]
           (codex/prepare runtime definition
                          (run "headless" {:harness/resumes "prior"})))))
  (testing "interactive resume keeps the initial prompt in argv for the host TTY"
    (is (= ["codex" "resume"
            "--model" "gpt-test"
            "--config" "model_reasoning_effort=high"
            "--skip-git-repo-check"
            "provisional"
            "Do the work"]
           (codex/prepare runtime definition
                          (run "interactive" {:harness/resumes "prior"}))))))

(deftest finish-normalizes-final-message-and-provider-session
  (let [stdout (str "{\"type\":\"thread.started\",\"thread_id\":\"thread-1\"}\n"
                    "{\"type\":\"item.completed\",\"item\":"
                    "{\"type\":\"agent_message\",\"text\":\"draft\"}}\n"
                    "{\"type\":\"item.completed\",\"item\":"
                    "{\"type\":\"agent_message\",\"text\":\"final\"}}\n")]
    (is (= {:status :done
            :exit-code 0
            :result "final"
            :session-id "thread-1"}
           (codex/finish runtime definition (run "headless")
                         {:exit-code 0 :stdout stdout :stderr ""})))))

(deftest finish-fails-loudly-on-incomplete-success-output
  (testing "a successful process without a provider thread cannot be resumed safely"
    (let [outcome (codex/finish
                   runtime definition (run "headless")
                   {:exit-code 0
                    :stdout (str "{\"type\":\"item.completed\",\"item\":"
                                 "{\"type\":\"agent_message\",\"text\":\"final\"}}\n")
                    :stderr ""})]
      (is (= :failed (:status outcome)))
      (is (re-find #"no thread id" (:error outcome)))))
  (testing "malformed JSONL becomes a normalized failure"
    (let [outcome (codex/finish runtime definition (run "headless")
                                {:exit-code 0 :stdout "{nope}\n" :stderr ""})]
      (is (= :failed (:status outcome)))
      (is (re-find #"JSONL parse failed" (:error outcome))))))
