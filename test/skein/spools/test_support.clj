(ns skein.spools.test-support
  "Shared fixtures for spool tests: disposable temp config-dir
  workspaces and a started weaver runtime wrapper, used by skein.spools-test,
  skein.spools.workflow-test, and skein.spools.devflow-test."
  (:require [clojure.java.io :as io]
            [skein.core.db-test :as db-test]
            [skein.core.weaver.config :as daemon-config]
            [skein.core.weaver.runtime :as runtime]))

(defn spool-checkout-root
  "Resolve the checkout root of a spool from one of its classpath source files.

  `resource-path` is the spool source's classpath-relative path (e.g.
  \"skein/spools/devflow.clj\"). The returned root is the directory holding the
  spool's `src/`, whichever checkout backs the classpath — a tools.deps gitlib
  procurement or a developer's local override. Fails loudly when the resource
  is not on the classpath."
  [resource-path]
  (let [resource (io/resource resource-path)]
    (when-not resource
      (throw (ex-info "Spool source not on the test classpath"
                      {:resource resource-path})))
    (let [segments (inc (count (clojure.string/split resource-path #"/")))]
      (reduce (fn [f _] (.getParentFile f))
              (io/file (.toURI resource))
              (range segments)))))

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
