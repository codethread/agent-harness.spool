(ns skein.shuttle-test
  "Tests for the shuttle agent-spawning spool against a real weaver runtime.

  Harness processes in these tests use the shipped `sh` harness (the prompt is
  the script), so runs are cheap and deterministic while still exercising the
  full readiness-driven spawn engine, result capture, notes, and reconciliation."
  (:require [clojure.java.io :as io]
            [clojure.java.shell]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [skein.core.db-test :as db-test]
            [skein.spools.shuttle :as shuttle]
            [skein.api.weaver.alpha :as api]
            [skein.core.weaver.config :as daemon-config]
            [skein.core.weaver.runtime :as runtime]))

(defn- temp-config-dir []
  (doto (.toFile (java.nio.file.Files/createTempDirectory
                  (.toPath (io/file "/tmp"))
                  "skein-shuttle-config"
                  (make-array java.nio.file.attribute.FileAttribute 0)))
    (.mkdirs)))

(defn- test-world [config-dir]
  (daemon-config/world config-dir
                       (str config-dir "/state")
                       (str config-dir "/data")))

(defn- with-shuttle
  "Run f with a fresh weaver runtime that has the shuttle installed."
  [f]
  (let [db-file (db-test/temp-db-file)
        config-dir (temp-config-dir)]
    (try
      (let [rt (runtime/start! db-file {:world (test-world (.getCanonicalPath config-dir))})]
        (try
          (shuttle/install!)
          (f rt)
          (finally
            (runtime/stop! rt))))
      (finally
        (db-test/delete-sqlite-family! db-file)))))

(defn- await-phase
  "Poll until the strand's shuttle/phase is in `phases` or timeout; return it."
  [rt id phases timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (let [strand (api/show rt id)
            phase (get-in strand [:attributes :shuttle/phase])]
        (cond
          (contains? phases phase) strand
          (> (System/currentTimeMillis) deadline)
          (throw (ex-info "Timed out waiting for run phase"
                          {:id id :want phases :strand strand}))
          :else (do (Thread/sleep 50) (recur)))))))

(deftest harness-registry-validates-and-resolves-aliases
  (with-shuttle
    (fn [_rt]
      (shuttle/register-default-harnesses!)
      (testing "definition validation fails loudly"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"argv"
                              (shuttle/defharness! :bad {:argv []})))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unknown keys"
                              (shuttle/defharness! :bad {:argv ["x"] :nope 1})))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"parse"
                              (shuttle/defharness! :bad {:argv ["x"] :parse :yaml}))))
      (testing "alias layering flattens onto the base harness"
        (shuttle/defharness! :base {:argv ["tool" "-p"] :parse :raw})
        (shuttle/defalias! :fast {:alias-of :base :extra-args ["--model" "fast"]})
        (shuttle/defalias! :fast-reviewer {:alias-of :fast :prompt-prefix "Review: "})
        (let [effective (shuttle/resolve-harness :fast-reviewer)]
          (is (= ["tool" "-p"] (:argv effective)))
          (is (= ["--model" "fast"] (:extra-args effective)))
          (is (= "Review: " (:prompt-prefix effective)))))
      (testing "alias cycles and missing harnesses fail loudly"
        (shuttle/defalias! :a {:alias-of :b})
        (shuttle/defalias! :b {:alias-of :a})
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"cycle"
                              (shuttle/resolve-harness :a)))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Harness not found"
                              (shuttle/resolve-harness :missing)))))))

(deftest run-spawns-when-ready-and-captures-result
  (with-shuttle
    (fn [rt]
      (let [run (shuttle/spawn-run! {:harness :sh :prompt "echo hello-shuttle"})
            done (await-phase rt (:id run) #{"done"} 10000)]
        (is (= "closed" (:state done)))
        (is (= "hello-shuttle" (get-in done [:attributes :shuttle/result])))
        (is (= 1 (get-in done [:attributes :shuttle/attempt])))
        (is (some? (get-in done [:attributes :shuttle/pid])))))))

(deftest failing-run-stays-active-and-loud
  (with-shuttle
    (fn [rt]
      (let [run (shuttle/spawn-run! {:harness :sh :prompt "echo boom >&2; exit 3"})
            failed (await-phase rt (:id run) #{"failed"} 10000)]
        (is (= "active" (:state failed)))
        (is (str/includes? (get-in failed [:attributes :shuttle/error]) "exited 3"))
        (is (str/includes? (get-in failed [:attributes :shuttle/error]) "boom"))))))

(deftest dependent-run-waits-for-blocker-and-fans-in
  (with-shuttle
    (fn [rt]
      (let [blocker (api/add rt {:title "external gate" :attributes {"k" "v"}})
            child-a (shuttle/spawn-run! {:harness :sh :prompt "echo a"})
            child-b (shuttle/spawn-run! {:harness :sh :prompt "echo b"})
            collector (shuttle/spawn-run! {:harness :sh :prompt "echo collected"
                                           :depends-on [(:id blocker) (:id child-a) (:id child-b)]})]
        (await-phase rt (:id child-a) #{"done"} 10000)
        (await-phase rt (:id child-b) #{"done"} 10000)
        (testing "collector stays pending while any dependency is active"
          (Thread/sleep 300)
          (is (= "pending" (get-in (api/show rt (:id collector)) [:attributes :shuttle/phase]))))
        (testing "closing the last dependency triggers the spawn via events"
          (api/update rt (:id blocker) {:state "closed"})
          (let [done (await-phase rt (:id collector) #{"done"} 10000)]
            (is (= "collected" (get-in done [:attributes :shuttle/result])))))))))

(deftest spawned-by-records-provenance-tree
  (with-shuttle
    (fn [rt]
      (let [parent (shuttle/spawn-run! {:harness :sh :prompt "echo parent"})
            child (shuttle/spawn-run! {:harness :sh :prompt "echo child"
                                       :spawned-by (:id parent)})]
        (await-phase rt (:id parent) #{"done"} 10000)
        (await-phase rt (:id child) #{"done"} 10000)
        (is (= (:id parent)
               (get-in (api/show rt (:id child)) [:attributes :shuttle/spawned-by])))
        (is (some #(and (= (:id child) (:to_strand_id %)) (= "parent-of" (:edge_type %)))
                  (:edges (api/subgraph rt [(:id parent)]))))
        (is (= (:id parent) (:spawned-by (shuttle/run-summary (api/show rt (:id child))))))
        (is (nil? (:for (shuttle/run-summary (api/show rt (:id child))))))))))

(deftest run-summary-reports-treadle-gate-provenance
  (with-shuttle
    (fn [rt]
      (let [gate (api/add rt {:title "gate"})
            run (shuttle/spawn-run! {:harness :sh
                                     :prompt "echo delegated"
                                     :attrs {"treadle/gate" (:id gate)}})]
        (is (= (:id gate) (:for (shuttle/run-summary (api/show rt (:id run))))))
        (await-phase rt (:id run) #{"done"} 10000)))))

(deftest notes-are-append-only-memory-with-rounds
  (with-shuttle
    (fn [rt]
      (let [target (api/add rt {:title "shared blackboard"})
            target-id (:id target)]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not found"
                              (shuttle/note! "missing-id" "x")))
        (shuttle/note! target-id "first finding" {:by "run-1" :round 1})
        (shuttle/note! target-id "second finding" {:by "run-2" :round 1})
        (shuttle/note! target-id "rebuttal" {:by "run-1" :round 2})
        (is (= ["first finding" "second finding" "rebuttal"]
               (mapv :note (shuttle/notes target-id))))
        (is (= ["rebuttal"] (mapv :note (shuttle/notes target-id {:round 2}))))
        (is (= ["run-1" "run-2"] (mapv :by (shuttle/notes target-id {:round 1}))))
        (testing "notes are closed strands linked by a notes annotation edge"
          (let [note-id (:id (first (shuttle/notes target-id)))
                note (api/show rt note-id)]
            (is (= "closed" (:state note)))))))))

(deftest await-runs-blocks-until-terminal-and-times-out
  (with-shuttle
    (fn [rt]
      (let [quick (shuttle/spawn-run! {:harness :sh :prompt "echo quick"})
            {:keys [timed-out runs]} (shuttle/await-runs [(:id quick)] {:timeout-secs 10})]
        (is (false? timed-out))
        (is (= "quick" (:result (first runs)))))
      (let [blocker (api/add rt {:title "never closes"})
            stuck (shuttle/spawn-run! {:harness :sh :prompt "echo never"
                                       :depends-on [(:id blocker)]})
            {:keys [timed-out]} (shuttle/await-runs [(:id stuck)] {:timeout-secs 1})]
        (is (true? timed-out))))))

(deftest kill-terminates-a-running-harness
  (with-shuttle
    (fn [rt]
      (let [run (shuttle/spawn-run! {:harness :sh :prompt "sleep 30; echo survived"})]
        (await-phase rt (:id run) #{"running"} 10000)
        (shuttle/kill! (:id run))
        (let [failed (await-phase rt (:id run) #{"failed"} 10000)]
          (is (str/includes? (get-in failed [:attributes :shuttle/error]) "killed")))))))

(deftest reconcile-respawns-orphans-and-exhausts-bounded-attempts
  (with-shuttle
    (fn [rt]
      (testing "an orphaned running run respawns and completes"
        (let [orphan (api/add rt {:title "orphan"
                                  :attributes {"shuttle/run" "true"
                                               "shuttle/harness" "sh"
                                               "shuttle/prompt" "echo recovered"
                                               "shuttle/phase" "running"
                                               "shuttle/attempt" 1
                                               "shuttle/pid" 99999999}})
              summary (shuttle/reconcile!)]
          (is (= [(:id orphan)] (:respawned summary)))
          (is (= "recovered"
                 (get-in (await-phase rt (:id orphan) #{"done"} 10000)
                         [:attributes :shuttle/result])))))
      (testing "a run out of attempts is marked exhausted, stays active"
        (let [spent (api/add rt {:title "spent"
                                 :attributes {"shuttle/run" "true"
                                              "shuttle/harness" "sh"
                                              "shuttle/prompt" "echo nope"
                                              "shuttle/phase" "running"
                                              "shuttle/attempt" 3}})
              summary (shuttle/reconcile!)]
          (is (= [(:id spent)] (:exhausted summary)))
          (let [strand (api/show rt (:id spent))]
            (is (= "active" (:state strand)))
            (is (= "exhausted" (get-in strand [:attributes :shuttle/phase])))
            (is (str/includes? (get-in strand [:attributes :shuttle/error]) "exhausted"))))))))

(deftest spawn-validates-inputs-before-creating-anything
  (with-shuttle
    (fn [rt]
      (testing "reserved control attributes cannot be overridden"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"control attributes"
                              (shuttle/spawn-run! {:harness :sh :prompt "echo x"
                                                   :attrs {"shuttle/phase" "done"}}))))
      (testing "provenance targets must exist before the run is created"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"parent strand not found"
                              (shuttle/spawn-run! {:harness :sh :prompt "echo x"
                                                   :spawned-by "missing-id"})))
        (is (empty? (filter #(= "echo x" (:title %)) (shuttle/runs))))))))

(deftest unresolvable-harness-fails-the-run-loudly
  (with-shuttle
    (fn [rt]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Harness not found"
                            (shuttle/spawn-run! {:harness :absent :prompt "x"})))
      (testing "a pending strand referencing a missing harness fails at spawn"
        (api/add rt {:title "handmade"
                     :attributes {"shuttle/run" "true"
                                  "shuttle/harness" "absent"
                                  "shuttle/prompt" "echo x"
                                  "shuttle/phase" "pending"}})
        (let [run-id (:id (first (filter #(= "handmade" (:title %))
                                         (api/list rt shuttle/run-query {}))))
              failed (await-phase rt run-id #{"failed"} 10000)]
          (is (str/includes? (get-in failed [:attributes :shuttle/error]) "Harness not found")))))))

;; ---------------------------------------------------------------------------
;; Interactive runs
;;
;; The fake-mux backend emulates a terminal multiplexer with plain detached
;; processes: :start nohups the launcher script and returns its pid as the
;; handle, :alive/:stop signal that pid. Real tmux is exercised by smoke, not
;; unit tests.

(def ^:private fake-mux
  {:start ["sh" "-c" "nohup \"$1\" >/dev/null 2>&1 & printf '{\"pid\":\"%s\"}' \"$!\"" "fake-mux" :command]
   :alive ["kill" "-0" :handle/pid]
   :stop ["kill" :handle/pid]
   :capture ["sh" "-c" "printf 'scrollback %s' \"$1\"" "fake-capture" :handle/pid]
   :attach ["echo" "attach" :handle/pid]
   :doc "test-only fake multiplexer over detached processes"})

(defn- process-alive? [pid]
  (zero? (:exit (clojure.java.shell/sh "kill" "-0" (str pid)))))

(defn- await-process-death [pid timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (cond
        (not (process-alive? pid)) true
        (> (System/currentTimeMillis) deadline) false
        :else (do (Thread/sleep 50) (recur))))))

(defn- await-attr
  "Poll until the strand carries attribute `k` or timeout; return the strand.
  Needed for interactive launches: phase running is written durably before
  the backend starts, so the handle lands strictly after running."
  [rt id k timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (let [strand (api/show rt id)]
        (cond
          (some? (get-in strand [:attributes k])) strand
          (> (System/currentTimeMillis) deadline)
          (throw (ex-info "Timed out waiting for attribute" {:id id :attr k :strand strand}))
          :else (do (Thread/sleep 50) (recur)))))))

(defn- forget-in-flight!
  "Simulate a weaver crash: drop this runtime's in-flight ownership so
  reconcile! sees still-running strands as orphans."
  []
  (reset! ((var-get #'shuttle/in-flight)) {}))

(defn- spawn-interactive!
  "Spawn a long-lived fake-mux run and wait for its session handle; returns
  {:run <strand> :pid <handle pid>}."
  [rt & [opts]]
  (shuttle/defbackend! :fake-mux fake-mux)
  (let [run (shuttle/spawn-run! (merge {:harness :sh :prompt "sleep 300"
                                        :mode :interactive :backend :fake-mux}
                                       opts))
        running (await-attr rt (:id run) (keyword "shuttle" "handle.pid") 10000)
        pid (get-in running [:attributes (keyword "shuttle" "handle.pid")])]
    (is (process-alive? pid))
    {:run running :pid pid}))

(deftest backend-registry-validates-defs
  (with-shuttle
    (fn [_rt]
      (testing "required ops and unknown keys fail loudly"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"missing required op"
                              (shuttle/defbackend! :bad {:start ["x"]})))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unknown keys"
                              (shuttle/defbackend! :bad (assoc fake-mux :nope ["x"])))))
      (testing "argv token namespaces are validated statically"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"cannot reference handle"
                              (shuttle/defbackend! :bad (assoc fake-mux :start ["x" :handle/session]))))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not an available input"
                              (shuttle/defbackend! :bad (assoc fake-mux :alive ["x" :cwd]))))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unknown namespace"
                              (shuttle/defbackend! :bad (assoc fake-mux :stop ["x" :nope/what])))))
      (testing "missing backends fail loudly"
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Backend not found"
                              (shuttle/resolve-backend :absent-backend)))))))

(deftest spawn-validates-interactive-options
  (with-shuttle
    (fn [_rt]
      (shuttle/defbackend! :fake-mux fake-mux)
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"require :backend"
                            (shuttle/spawn-run! {:harness :sh :prompt "x" :mode :interactive})))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"apply only to interactive"
                            (shuttle/spawn-run! {:harness :sh :prompt "x" :backend :fake-mux})))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"reap must be auto or manual"
                            (shuttle/spawn-run! {:harness :sh :prompt "x" :mode :interactive
                                                 :backend :fake-mux :reap :sometimes})))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"control attributes"
                            (shuttle/spawn-run! {:harness :sh :prompt "x"
                                                 :attrs {"shuttle/handle.pid" "1"}}))))))

(deftest interactive-run-reaps-when-served-strand-closes
  (with-shuttle
    (fn [rt]
      (let [target (api/add rt {:title "hitl task"})
            {:keys [run pid]} (spawn-interactive! rt {:parent (:id target)})]
        (is (= "claim" (get-in run [:attributes :shuttle/completion])))
        (is (= (:id target) (get-in run [:attributes :shuttle/for])))
        (is (str/starts-with? (get-in run [:attributes :shuttle/session]) "skein-"))
        (testing "summary carries interactive fields"
          (let [summary (shuttle/run-summary (api/show rt (:id run)))]
            (is (= "interactive" (:mode summary)))
            (is (= "fake-mux" (:backend summary)))
            (is (= (str "echo attach " pid) (:attach summary)))))
        (testing "closing the served strand reaps the session"
          (api/update rt (:id target) {:state "closed"})
          (let [done (await-phase rt (:id run) #{"done"} 10000)]
            (is (= "closed" (:state done)))
            (is (nil? (get-in done [:attributes :shuttle/teardown-error])))
            (is (true? (await-process-death pid 5000)))
            (testing "scrollback was captured before teardown"
              (let [log (get-in done [:attributes :shuttle/log])]
                (is (some? log))
                (is (= (str "scrollback " pid) (slurp log)))))))))))

(deftest manual-close-run-tears-down-on-own-close
  (with-shuttle
    (fn [rt]
      (let [{:keys [run pid]} (spawn-interactive! rt)]
        (is (= "manual-close" (get-in run [:attributes :shuttle/completion])))
        (api/update rt (:id run) {:state "closed"})
        (let [done (await-phase rt (:id run) #{"done"} 10000)]
          (is (= "closed" (:state done)))
          (is (true? (await-process-death pid 5000))))))))

(deftest reap-manual-leaves-the-session-to-the-human
  (with-shuttle
    (fn [rt]
      (let [target (api/add rt {:title "keep my terminal"})
            {:keys [run pid]} (spawn-interactive! rt {:parent (:id target) :reap :manual})]
        (api/update rt (:id target) {:state "closed"})
        (let [done (await-phase rt (:id run) #{"done"} 10000)]
          (is (= "closed" (:state done)))
          (is (process-alive? pid))
          (clojure.java.shell/sh "kill" (str pid)))))))

(deftest dead-session-fails-loudly-and-completion-wins-races
  (with-shuttle
    (fn [rt]
      (testing "a session dying before its target closes fails the run"
        (let [target (api/add rt {:title "abandoned"})
              {:keys [run pid]} (spawn-interactive! rt {:parent (:id target)})]
          (clojure.java.shell/sh "kill" "-9" (str pid))
          (is (true? (await-process-death pid 5000)))
          (shuttle/supervise!)
          (let [failed (await-phase rt (:id run) #{"failed"} 10000)]
            (is (= "active" (:state failed)))
            (is (str/includes? (get-in failed [:attributes :shuttle/error]) "session ended")))
          (is (= "active" (:state (api/show rt (:id target)))))))
      (testing "a dead session whose target already closed is done, not failed"
        (let [target (api/add rt {:title "finished then exited"})
              {:keys [run pid]} (spawn-interactive! rt {:parent (:id target)})]
          ;; close the target and kill the session without letting the event
          ;; handler win the race: supervision must classify this as complete
          (api/update rt (:id target) {:state "closed"})
          (clojure.java.shell/sh "kill" "-9" (str pid))
          (shuttle/supervise!)
          (let [done (await-phase rt (:id run) #{"done"} 10000)]
            (is (= "closed" (:state done)))))))))

(deftest harness-capture-overrides-backend-scrollback
  (with-shuttle
    (fn [rt]
      (shuttle/defbackend! :fake-mux fake-mux)
      ;; a harness-aware capture source (stands in for hook-written dialogue
      ;; logs) keyed by the run id the engine exports as SKEIN_RUN_ID
      ;; the harness sets a default cwd: capture must receive that effective
      ;; launch cwd, not the workspace root
      (shuttle/defharness! :sh-hooked
        {:argv ["sh" "-c"] :preamble? false :cwd "/tmp"
         :capture ["sh" "-c" "printf 'dialogue log for %s in %s' \"$1\" \"$2\"" "hook-capture" :run-id :cwd]})
      (let [target (api/add rt {:title "captured task"})
            run (shuttle/spawn-run! {:harness :sh-hooked :prompt "sleep 300"
                                     :mode :interactive :backend :fake-mux
                                     :parent (:id target)})
            running (await-attr rt (:id run) (keyword "shuttle" "handle.pid") 10000)
            pid (get-in running [:attributes (keyword "shuttle" "handle.pid")])]
        (testing "capture! peeks a live session without killing it"
          (let [{:keys [text path]} (shuttle/capture! (:id run))]
            (is (= (str "dialogue log for " (:id run) " in /tmp") text))
            (is (= path (get-in (api/show rt (:id run)) [:attributes :shuttle/log])))
            (is (process-alive? pid))))
        (testing "teardown persists the harness capture, not backend scrollback"
          (api/update rt (:id target) {:state "closed"})
          (let [done (await-phase rt (:id run) #{"done"} 10000)
                log (get-in done [:attributes :shuttle/log])]
            (is (str/starts-with? (slurp log) "dialogue log for"))))
        (testing "capture! fails loudly when nothing provides a capture op"
          (shuttle/defbackend! :bare-mux (dissoc fake-mux :capture :attach))
          (let [bare (shuttle/spawn-run! {:harness :sh :prompt "sleep 300"
                                          :mode :interactive :backend :bare-mux})]
            (await-attr rt (:id bare) (keyword "shuttle" "handle.pid") 10000)
            (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no capture op"
                                  (shuttle/capture! (:id bare))))
            (shuttle/kill! (:id bare))))))))

(deftest harness-capture-argv-is-validated
  (with-shuttle
    (fn [_rt]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not an available input"
                            (shuttle/defharness! :bad-capture
                              {:argv ["x"] :capture ["cat" :command]}))))))

(deftest kill-terminates-an-interactive-session
  (with-shuttle
    (fn [rt]
      (let [{:keys [run pid]} (spawn-interactive! rt)]
        (shuttle/kill! (:id run))
        (is (true? (await-process-death pid 5000)))
        (let [failed (api/show rt (:id run))]
          (is (= "failed" (get-in failed [:attributes :shuttle/phase])))
          (is (str/includes? (get-in failed [:attributes :shuttle/error]) "killed")))
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"no live session"
                              (shuttle/kill! (:id run))))))))

(deftest reconcile-adopts-live-sessions-and-fails-dead-ones
  (with-shuttle
    (fn [rt]
      (let [target (api/add rt {:title "survives restarts"})
            {:keys [run pid]} (spawn-interactive! rt {:parent (:id target)})]
        (testing "a live session is adopted, not respawned"
          (forget-in-flight!)
          (let [summary (shuttle/reconcile!)]
            (is (= [(:id run)] (:adopted summary)))
            (is (empty? (:respawned summary))))
          (is (= "running" (get-in (api/show rt (:id run)) [:attributes :shuttle/phase])))
          (is (process-alive? pid)))
        (testing "a dead orphan fails loudly instead of respawning"
          (clojure.java.shell/sh "kill" "-9" (str pid))
          (is (true? (await-process-death pid 5000)))
          (forget-in-flight!)
          (let [summary (shuttle/reconcile!)]
            (is (= [(:id run)] (:failed summary))))
          (let [failed (api/show rt (:id run))]
            (is (= "active" (:state failed)))
            (is (= "failed" (get-in failed [:attributes :shuttle/phase])))))))))

(deftest malformed-handle-stops-the-started-session
  (with-shuttle
    (fn [rt]
      ;; :start launches a real detached process keyed by the suggested session
      ;; name but reports a garbage handle. The engine must stop what it
      ;; started (via the suggested-session fallback) before failing the run.
      (shuttle/defbackend! :broken-mux
        {:start ["sh" "-c" "nohup \"$1\" >/dev/null 2>&1 & echo \"$!\" > \"/tmp/$2.pid\"; printf 'not-a-json-handle'"
                 "broken-mux" :command :session]
         :alive ["sh" "-c" "kill -0 \"$(cat \"/tmp/$1.pid\")\"" "broken-mux" :handle/session]
         :stop ["sh" "-c" "kill \"$(cat \"/tmp/$1.pid\")\"" "broken-mux" :handle/session]})
      (let [run (shuttle/spawn-run! {:harness :sh :prompt "sleep 300"
                                     :mode :interactive :backend :broken-mux})
            failed (await-phase rt (:id run) #{"failed"} 10000)
            session (get-in failed [:attributes :shuttle/session])
            pid-file (io/file "/tmp" (str session ".pid"))
            pid (str/trim (slurp pid-file))]
        (is (str/includes? (get-in failed [:attributes :shuttle/error]) "not a JSON handle"))
        (testing "the leaked session process was stopped"
          (is (true? (await-process-death pid 5000))))
        (.delete pid-file)))))

(deftest await-detects-dead-interactive-sessions
  (with-shuttle
    (fn [rt]
      (let [target (api/add rt {:title "await target"})
            {:keys [run pid]} (spawn-interactive! rt {:parent (:id target)})
            killer (future (Thread/sleep 500)
                           (clojure.java.shell/sh "kill" "-9" (str pid)))
            {:keys [timed-out runs]} (shuttle/await-runs [(:id run)] {:timeout-secs 30})]
        @killer
        (is (false? timed-out))
        (is (= "failed" (:phase (first runs))))))))

(deftest spool-loads-through-approved-spool-workspace-flow
  (let [db-file (db-test/temp-db-file)
        config-dir (temp-config-dir)
        repo-root (.getCanonicalPath (io/file "spools/shuttle"))]
    (try
      (spit (io/file config-dir "spools.edn")
            (pr-str {:spools {'skein.spools/shuttle {:local/root repo-root}}}))
      (let [rt (runtime/start! db-file {:world (test-world (.getCanonicalPath config-dir))})]
        (try
          (let [synced ((requiring-resolve 'skein.api.runtime.alpha/sync!) rt)
                used ((requiring-resolve 'skein.api.runtime.alpha/use!)
                      rt :shuttle {:ns 'skein.spools.shuttle
                                :spools ['skein.spools/shuttle]
                                :call 'skein.spools.shuttle/install!
                                :required? true})]
            (is (contains? #{:loaded :already-available}
                           (get-in synced [:spools 'skein.spools/shuttle :status])))
            (is (= :loaded (:status used)))
            (is (not-any? #(= "agent" (:name %)) (api/ops rt))))
          (finally
            (runtime/stop! rt))))
      (finally
        (db-test/delete-sqlite-family! db-file)))))
