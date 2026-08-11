(ns ct.spools.kondo-export-test
  "Proof that a tools.deps consumer imports and uses the spool Kondo exports."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest is]]))

(def ^:private clj-kondo-version "2025.06.05")

(def ^:private config-import-command
  ["sh" "-c"
   "clojure -M:lint --lint \"$(clojure -Spath)\" --dependencies --parallel --copy-configs --skip-lint"])

(def ^:private source-lint-command
  ["clojure" "-M:lint" "--lint" "src" "--cache" "false"])

(defn- repository-root
  "Return this checkout's root from the test process working directory."
  []
  (.getCanonicalFile (io/file (System/getProperty "user.dir"))))

(defn- write-consumer-file!
  "Write `content` below `root`, creating parent directories."
  [^java.io.File root relative-path content]
  (let [file (io/file root relative-path)]
    (.mkdirs (.getParentFile file))
    (spit file content)
    file))

(defn- delete-tree!
  "Delete a temporary consumer tree from its leaves upward."
  [^java.io.File root]
  (doseq [file (reverse (file-seq root))]
    (io/delete-file file true)))

(defn- consumer-deps
  "Return local-development tools.deps data for both spool export roots."
  [^java.io.File root]
  (let [millstrand-root (.getCanonicalPath (io/file root "../skein-src"))
        agent-run-root (.getCanonicalPath (io/file root "agent-run"))]
    {:paths ["src"]
     :deps {'io.millstrand/millstrand {:local/root millstrand-root}
            'ct.spools/agent-run {:local/root agent-run-root}
            'millhouse.spools/workflow
            {:git/url "https://github.com/codethread/millhouse.spool.git"
             :git/sha "5581f0aef638a1744521fe95282de5a969a999fd"
             :deps/root "spools/workflow"}
            'clj-kondo/clj-kondo {:mvn/version clj-kondo-version}}
     :aliases {:lint {:main-opts ["-m" "clj-kondo.main"]}}}))

(def ^:private consumer-source
  (str
   "(ns example.consumer\n"
   "  \"Consumer source covering agent-run declaration macros.\"\n"
   "  (:require [ct.spools.agent-run :as agent-run]\n"
   "            [millhouse.spools.workflow :as workflow]))\n"
   "\n"
   "(agent-run/defharnesses harnesses \"Harnesses.\"\n"
   "  {:sh {:argv [\"sh\"]}})\n"
   "(agent-run/defaliases aliases \"Aliases.\"\n"
   "  {:fast {:alias-of :sh}})\n"
   "(workflow/defexecutor consumer\n"
   "  \"Return a stall diagnostic.\"\n"
   "  {}\n"
   "  [gate]\n"
   "  {:gate gate})\n"
   "(consumer-stalled? {:id :gate})\n"))

(defn- run-consumer-command!
  "Run one command in the temporary consumer and return its output and exit."
  [^java.io.File root command]
  (let [process (doto (ProcessBuilder. ^java.util.List command)
                  (.directory root)
                  (.redirectErrorStream true))
        started (.start process)
        output (slurp (.getInputStream started))]
    {:command command
     :exit (.waitFor started)
     :output output}))

(defn- run-consumer-kondo!
  "Import dependency configs once, then lint the consumer source."
  [^java.io.File root]
  (let [{import-exit :exit import-output :output} (run-consumer-command!
                                                   root config-import-command)
        {lint-exit :exit lint-output :output} (run-consumer-command!
                                               root source-lint-command)]
    {:import-exit import-exit
     :import-output import-output
     :lint-exit lint-exit
     :lint-output lint-output
     :exit (if (zero? import-exit) lint-exit import-exit)}))

(deftest consumer-imports-and-lints-agent-run-macros
  (let [root (.toFile (java.nio.file.Files/createTempDirectory
                       "agent-run-kondo-consumer"
                       (make-array java.nio.file.attribute.FileAttribute 0)))]
    (try
      (let [repository (repository-root)]
        (write-consumer-file! root "deps.edn" (pr-str (consumer-deps repository)))
        (write-consumer-file! root "src/example/consumer.clj" consumer-source)
        (.mkdirs (io/file root ".clj-kondo"))
        (let [{:keys [exit import-exit import-output lint-exit lint-output]}
              (run-consumer-kondo! root)
              imported-config (io/file root
                                       ".clj-kondo/imports/ct.spools/agent-run/config.edn")]
          (is (zero? exit) (str import-output lint-output))
          (is (zero? import-exit) import-output)
          (is (zero? lint-exit) lint-output)
          (is (.isFile imported-config))
          (is (str/includes? (slurp imported-config) "defharnesses"))
          (is (str/includes? (slurp imported-config) "defaliases"))
          (let [workflow-config (io/file root
                                         ".clj-kondo/imports/millhouse.spools/workflow/config.edn")]
            (is (.isFile workflow-config))
            (is (str/includes? (slurp workflow-config) "defexecutor"))
            (is (str/includes? (slurp workflow-config)
                               "hooks.millhouse.spools.workflow/defexecutor")))))
      (finally
        (delete-tree! root)))))
