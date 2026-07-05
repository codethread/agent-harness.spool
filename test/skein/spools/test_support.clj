(ns skein.spools.test-support
  "Shared fixtures for spool tests: disposable temp config-dir
  workspaces and a started weaver runtime wrapper, used by skein.spools-test,
  and skein.spools.workflow-test; also the shared await-budget-ms poll-deadline
  knob used by tests that wait on cross-thread/subprocess readiness."
  (:require [clojure.java.io :as io]
            [skein.core.db-test :as db-test]
            [skein.core.weaver.config :as daemon-config]
            [skein.core.weaver.runtime :as runtime]))

(defn test-world [config-dir]
  (daemon-config/world config-dir
                       (str config-dir "/state")
                       (str config-dir "/data")))

(defn temp-config-dir []
  (doto (.toFile (java.nio.file.Files/createTempDirectory
                  (.toPath (io/file "/tmp"))
                  "skein-spools-config"
                  (make-array java.nio.file.attribute.FileAttribute 0)))
    (.mkdirs)))

(defn await-budget-ms
  "Poll deadline for cross-thread/subprocess readiness waits, in ms. Scales
  `base-ms` (default 10000) via the SKEIN_TEST_AWAIT_SCALE env var (a
  multiplier, default 1) so a single knob widens every test's fixed poll
  deadlines under fork-heavy or otherwise loaded machines instead of editing
  each call site."
  ([] (await-budget-ms 10000))
  ([base-ms]
   (long (* base-ms (if-let [scale (System/getenv "SKEIN_TEST_AWAIT_SCALE")]
                      (try (Double/parseDouble scale)
                           (catch NumberFormatException _
                             (throw (ex-info "SKEIN_TEST_AWAIT_SCALE must be a number"
                                             {:env "SKEIN_TEST_AWAIT_SCALE" :value scale}))))
                      1.0)))))

(defn await-budget-secs
  "await-budget-ms in whole seconds, for CLI/API surfaces that take a
  :timeout-secs rather than a millisecond budget."
  ([] (long (/ (await-budget-ms) 1000)))
  ([base-secs] (long (/ (await-budget-ms (* base-secs 1000)) 1000))))

(defn with-runtime [f]
  (let [db-file (db-test/temp-db-file)
        config-dir (temp-config-dir)]
    (try
      (let [rt (runtime/start! db-file {:world (test-world (.getCanonicalPath config-dir))
                                        :publish? false})]
        (try
          (runtime/with-runtime-binding rt #(f rt config-dir))
          (finally
            (runtime/stop! rt))))
      (finally
        (db-test/delete-sqlite-family! db-file)
        ;; Runtime-added local roots are retained for the process lifetime by tools.deps.
        ;; Keep temp config dirs so later add-libs calls do not see stale basis entries.
        nil))))
