(ns ct.spools.real-agent-harness-test
  "Disposable external Mill + Weaver acceptance coverage for Agent Harness custody."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [ct.spools.test-support :as test-support]))

(def ^:private millstrand-sha
  "The Millstrand source revision exercised by the guarded acceptance test."
  "8312ad49d02f0f9f20fa167a8305e86a36f3fcae")

(def ^:private module-roots
  "Agent Harness roots projected into the disposable Millstrand launch basis."
  {'ct.spools/agent-run "agent-run"
   'ct.spools/delegation "delegation"
   'ct.spools/harness-core "harness-core"
   'ct.spools/agent-cli "agent-cli"
   'ct.spools/claude-harness "claude-harness"
   'ct.spools/codex-harness "codex-harness"
   'ct.spools/pi-harness "pi-harness"
   'ct.spools/cursor-harness "cursor-harness"
   'ct.spools/bench "bench"})

(defn- required-value
  [name value]
  (when (str/blank? value)
    (throw (ex-info (str name " must be non-blank") {:env name})))
  value)

(defn- explicit-path!
  [setting value predicate expected]
  (when-not (and (string? value) (predicate value))
    (throw (ex-info (str "Invalid external setting: setting=" setting
                         "; value=" (pr-str value)
                         "; expected " expected)
                    {:setting setting :value value :expected expected})))
  value)

(defn- command-result
  [argv {:keys [cwd env]}]
  (let [builder (doto (ProcessBuilder. (mapv str argv))
                  (.redirectErrorStream true))
        environment (.environment builder)]
    (when cwd
      (.directory builder (io/file cwd)))
    (doseq [[key value] env]
      (.put environment key value))
    (let [process (.start builder)
          output (slurp (.getInputStream process))
          exit (.waitFor process)]
      {:argv argv :exit exit :output output})))

(defn- start-process!
  [argv {:keys [cwd env]}]
  (let [builder (doto (ProcessBuilder. (mapv str argv))
                  (.redirectErrorStream true))
        environment (.environment builder)]
    (when cwd
      (.directory builder (io/file cwd)))
    (doseq [[key value] env]
      (.put environment key value))
    (let [process (.start builder)
          output (future (slurp (.getInputStream process)))]
      {:process process :pid (.pid process) :output output})))

(defn- stop-process!
  [{:keys [^Process process]}]
  (when (and process (.isAlive process))
    (.destroy process)
    (.waitFor process)))

(defn- parse-json
  [text]
  (json/read-str text :key-fn keyword))

(defn- command!
  [argv opts]
  (let [result (command-result argv opts)]
    (when-not (zero? (:exit result))
      (throw (ex-info "Disposable integration command failed" result)))
    (parse-json (:output result))))

(defn- run-command!
  [argv opts]
  (let [result (command-result argv opts)]
    (when-not (zero? (:exit result))
      (throw (ex-info "Disposable setup command failed" result)))
    result))

(defn- copy-tree!
  [source target]
  (let [source (.toPath (io/file source))
        target (.toPath (io/file target))]
    (doseq [path (iterator-seq (.iterator (java.nio.file.Files/walk
                                           source
                                           (make-array java.nio.file.FileVisitOption 0))))]
      (let [relative (.relativize source path)
            destination (.resolve target relative)]
        (if (java.nio.file.Files/isDirectory path
                                             (make-array java.nio.file.LinkOption 0))
          (java.nio.file.Files/createDirectories
           destination
           (make-array java.nio.file.attribute.FileAttribute 0))
          (java.nio.file.Files/copy path destination
                                    (into-array java.nio.file.CopyOption
                                                [java.nio.file.StandardCopyOption/REPLACE_EXISTING])))))))

(defn- symlink!
  [target link]
  (java.nio.file.Files/createSymbolicLink (.toPath (io/file link))
                                          (.toPath (io/file target))
                                          (make-array java.nio.file.attribute.FileAttribute 0)))

(defn- delete-tree!
  [root]
  (when (.exists ^java.io.File root)
    (with-open [paths (java.nio.file.Files/walk
                       (.toPath root)
                       (make-array java.nio.file.FileVisitOption 0))]
      (doseq [path (sort-by #(.getNameCount ^java.nio.file.Path %)
                            >
                            (iterator-seq (.iterator paths)))]
        (java.nio.file.Files/deleteIfExists path)))))

(defn- source-revision!
  [source cwd]
  (let [result (command-result ["git" "-C" source "rev-parse" "HEAD"] {:cwd cwd :env {}})]
    (when-not (zero? (:exit result))
      (throw (ex-info "Unable to inspect the pinned Millstrand source revision" result)))
    (let [revision (str/trim (:output result))]
      (when-not (= millstrand-sha revision)
        (throw (ex-info "External acceptance requires the current pinned Millstrand"
                        {:expected millstrand-sha :actual revision :source source})))
      revision)))

(defn- materialize-millstrand-source!
  [origin target]
  (run-command! ["git" "clone" "--shared" "--no-checkout" origin target]
                {:cwd origin :env {}})
  (run-command! ["git" "-C" target "checkout" "--detach" millstrand-sha]
                {:cwd origin :env {}})
  (run-command! ["make" "build"] {:cwd target :env {}})
  target)

(defn- configured-path
  [env name default]
  (required-value name (if (contains? env name) (get env name) default)))

(defn- cleanup!
  [weaver-stop! mill-stop!]
  (let [failures (->> [[:weaver-shutdown weaver-stop!]
                       [:mill-shutdown mill-stop!]]
                      (keep (fn [[operation cleanup]]
                              (try
                                (let [result (cleanup)]
                                  (when (and (= :weaver-shutdown operation)
                                             (map? result)
                                             (contains? result :exit)
                                             (not (zero? (:exit result))))
                                    (throw (ex-info (str "Disposable " (name operation)
                                                         " exited with status " (:exit result))
                                                    result))))
                                nil
                                (catch Throwable error
                                  {:operation operation :error error}))))
                      vec)]
    (when (seq failures)
      (throw (ex-info (str "Disposable acceptance cleanup failed: "
                           (str/join ", " (map (comp name :operation) failures)))
                      {:failures failures}))))
  nil)

(defn- resolve-millstrand-tools!
  [project-root root env]
  (let [source-override (when (contains? env "AGENT_HARNESS_MILLSTRAND_SOURCE")
                          (explicit-path! "AGENT_HARNESS_MILLSTRAND_SOURCE"
                                          (get env "AGENT_HARNESS_MILLSTRAND_SOURCE")
                                          #(.isDirectory (io/file %))
                                          "an existing directory"))
        source (or source-override
                   (materialize-millstrand-source!
                    (.getCanonicalPath (io/file project-root "../skein-src"))
                    (.getCanonicalPath (io/file root "millstrand"))))
        source (.getCanonicalPath (io/file source))
        mill-bin (if (contains? env "MILLSTRAND_MILL_BIN")
                   (explicit-path! "MILLSTRAND_MILL_BIN" (get env "MILLSTRAND_MILL_BIN")
                                   #(let [file (io/file %)]
                                      (and (.isFile file) (.canExecute file)))
                                   "an existing executable file")
                   (let [derived (str (io/file source "bin/mill"))]
                     (if source-override
                       (explicit-path! "MILLSTRAND_MILL_BIN" derived
                                       #(let [file (io/file %)]
                                          (and (.isFile file) (.canExecute file)))
                                       "an existing executable file")
                       (configured-path env "MILLSTRAND_MILL_BIN" derived))))
        strand-bin (if (contains? env "MILLSTRAND_STRAND_BIN")
                     (explicit-path! "MILLSTRAND_STRAND_BIN" (get env "MILLSTRAND_STRAND_BIN")
                                     #(let [file (io/file %)]
                                        (and (.isFile file) (.canExecute file)))
                                     "an existing executable file")
                     (let [derived (str (io/file source "bin/strand"))]
                       (if source-override
                         (explicit-path! "MILLSTRAND_STRAND_BIN" derived
                                         #(let [file (io/file %)]
                                            (and (.isFile file) (.canExecute file)))
                                         "an existing executable file")
                         (configured-path env "MILLSTRAND_STRAND_BIN" derived))))]
    {:source source
     :mill-bin mill-bin
     :strand-bin strand-bin}))

(defn- write-source-overlay!
  [overlay source project-root workspace]
  (doseq [directory ["src" "dev" "resources" "spools"]]
    (symlink! (io/file source directory) (io/file overlay directory)))
  (let [basis (edn/read-string (slurp (io/file source "deps.edn")))
        extra-deps (get-in basis [:aliases :millstrand :extra-deps])
        projected (reduce-kv (fn [deps lib root]
                               (assoc deps lib {:local/root (str (io/file project-root root))}))
                             extra-deps module-roots)]
    (spit (io/file overlay "deps.edn")
          (pr-str (assoc-in basis [:aliases :millstrand :extra-deps] projected))))
  (symlink! workspace (io/file overlay ".millstrand")))

(defn- write-workspace!
  [workspace project-root]
  (copy-tree! (io/file project-root ".millstrand") workspace)
  (spit (io/file workspace "deps.local.edn")
        (pr-str {:deps (reduce-kv (fn [deps lib root]
                                    (assoc deps lib {:local/root
                                                     (str (io/file project-root root))}))
                                  {}
                                  module-roots)}))
  (spit (io/file workspace "init.clj")
        (str "(when-not (find-ns 'millstrand.api.current.alpha)\n"
             "  (require '[millstrand.api.current.alpha])\n"
             "  (require '[millstrand.api.runtime.alpha]))\n"
             "(def runtime ((var-get (ns-resolve 'millstrand.api.current.alpha 'runtime))))\n"
             "(def module! (var-get (ns-resolve 'millstrand.api.runtime.alpha 'module!)))\n"
             "(module! runtime :millstrand/spools-batteries\n"
             "                 {:ns 'millstrand.spools.batteries\n"
             "                  :required? true})\n"
             "(module! runtime :millhouse/spools-identity\n"
             "                 {:ns 'millhouse.spools.identity\n"
             "                  :required? true})\n"
             "(module! runtime :millstrand/spools-agent-run\n"
             "                 {:ns 'ct.spools.agent-run\n"
             "                  :after [:millstrand/spools-batteries :millhouse/spools-identity]\n"
             "                  :required? true})\n"
             "(module! runtime :millstrand/spools-delegation\n"
             "                 {:ns 'ct.spools.delegation\n"
             "                  :after [:millstrand/spools-agent-run]\n"
             "                  :required? true})\n"))
  (spit (io/file workspace "init.local.clj")
        (str "(when-not (find-ns 'millstrand.api.current.alpha)\n"
             "  (require '[millstrand.api.current.alpha])\n"
             "  (require '[millstrand.api.runtime.alpha]))\n"
             "(def runtime ((var-get (ns-resolve 'millstrand.api.current.alpha 'runtime))))\n"
             "(def module! (var-get (ns-resolve 'millstrand.api.runtime.alpha 'module!)))\n"
             "(module! runtime :integration-harnesses\n"
             "                 {:file \"config/integration_harnesses.clj\"\n"
             "                  :after [:millstrand/spools-agent-run]\n"
             "                  :required? true})\n"))
  (spit (io/file workspace "config/integration_harnesses.clj")
        (str "(ns integration-harnesses\n"
             "  (:require [ct.spools.agent-run :as shuttle]))\n"
             "(shuttle/defharnesses! integration-harnesses\n"
             "  \"Disposable harnesses for custody acceptance.\"\n"
             "  {:a {:argv [\"sh\" \"-c\" \"sleep 30; printf A\"]\n"
             "       :parse :raw :preamble? false}\n"
             "   :b {:argv [\"sh\" \"-c\" \"sleep 30; printf B\"]\n"
             "       :parse :raw :preamble? false}})\n")))

(defn- show!
  [strand-env workspace id]
  (command! [(:strand-bin strand-env) "--workspace" workspace "show" id]
            strand-env))

(defn- agent!
  [strand-env workspace args]
  (command! (into [(:strand-bin strand-env) "--workspace" workspace "agent"] args)
            strand-env))

(defn- poll-show!
  [strand-env workspace id predicate]
  (test-support/poll-until #(let [strand (show! strand-env workspace id)]
                              (when (predicate strand) strand))
                           {:timeout-ms 60000
                            :interval-ms 100
                            :on-timeout #(throw (ex-info "Timed out waiting for disposable run"
                                                         {:id id
                                                          :strand (show! strand-env workspace id)}))}))

(defn- run-fact
  [strand]
  (let [attributes (:attributes strand)]
    {:id (:id strand)
     :phase (get attributes :agent-run/phase)
     :attempt (get attributes :agent-run/attempt)
     :owner (get attributes :agent-run/process-owner)
     :key (get attributes :agent-run/process-key)
     :handle (get attributes :agent-run/process-handle)}))

(defn- assert-run-fact!
  [fact]
  (when-not (and (= "running" (:phase fact))
                 (= 1 (:attempt fact))
                 (= "agent-harness/run" (:owner fact))
                 (= (str (:id fact) "/attempt-1") (:key fact))
                 (string? (:handle fact))
                 (not (str/blank? (:handle fact)))
                 (not= "pending" (:handle fact)))
    (throw (ex-info "Disposable run did not publish a durable custody fact" {:fact fact}))))

(defn run-acceptance!
  "Run the external Mill + Weaver Agent Harness acceptance world.

  The world uses a short-lived Millstrand source overlay, exact process handles, and no
  shared or canonical Weaver state."
  []
  (let [project-root (.getCanonicalPath (io/file (System/getProperty "user.dir")))
        root (.toFile (java.nio.file.Files/createTempDirectory
                       (.toPath (io/file "/tmp"))
                       "ah-"
                       (make-array java.nio.file.attribute.FileAttribute 0)))]
    (try
      (let [source-tools (resolve-millstrand-tools! project-root root (into {} (System/getenv)))
            millstrand-source (:source source-tools)
            mill-bin (:mill-bin source-tools)
            strand-bin (:strand-bin source-tools)
            _ (source-revision! millstrand-source project-root)
            state-root (io/file root "s")
            workspace-root (io/file root "w")
            workspace (io/file workspace-root ".millstrand")
            overlay (io/file root "m")
            _ (.mkdirs state-root)
            _ (.mkdirs workspace-root)
            _ (.mkdirs overlay)
            _ (write-source-overlay! overlay millstrand-source project-root workspace)
            env {"XDG_STATE_HOME" (.getCanonicalPath state-root)
                 "MILLSTRAND_SOURCE" (.getCanonicalPath overlay)}
            mill-env {:cwd project-root :env env :mill-bin mill-bin :strand-bin strand-bin}
            strand-env {:cwd (.getCanonicalPath workspace-root) :env env :strand-bin strand-bin}
            mill (start-process! [mill-bin "start"] mill-env)]
        (try
          (test-support/poll-until #(zero? (:exit (command-result [mill-bin "status"] mill-env)))
                                   {:timeout-ms 30000
                                    :interval-ms 100
                                    :on-timeout #(throw (ex-info "Mill did not become ready" {:pid (:pid mill)}))})
          (command! [mill-bin "init" "--workspace" (.getCanonicalPath workspace)] mill-env)
          (write-workspace! workspace project-root)
          (command! [mill-bin "weaver" "start" "--workspace" (.getCanonicalPath workspace)] mill-env)
          (let [a-task (:id (command! [strand-bin "--workspace" workspace "add" "A"
                                       "--attr" "body=body" "--attr" "agent-run/harness=a"] strand-env))
                b-task (:id (command! [strand-bin "--workspace" workspace "add" "B"
                                       "--attr" "body=body" "--attr" "agent-run/harness=b"] strand-env))
                c-task (:id (command! [strand-bin "--workspace" workspace "add" "C"
                                       "--attr" "body=body" "--attr" "agent-run/harness=b"
                                       "--edge" (str "depends-on:" b-task)] strand-env))
                a-run (:id (:run (agent! strand-env workspace ["delegate" a-task "--harness" "a"])))
                b-run (:id (:run (agent! strand-env workspace ["delegate" b-task "--harness" "b"])))
                blocked (command-result [strand-bin "--workspace" workspace "agent"
                                         "delegate" c-task "--harness" "b"] strand-env)]
            (when (zero? (:exit blocked))
              (throw (ex-info "B-to-C delegation was sent before readiness" {:result blocked})))
            (when (seq (agent! strand-env workspace ["ps" "--for" c-task]))
              (throw (ex-info "Blocked B-to-C delegation created a run" {:task c-task})))
            (let [a-before (poll-show! strand-env workspace a-run
                                       #(= "running" (get-in % [:attributes :agent-run/phase])))
                  b-before (poll-show! strand-env workspace b-run
                                       #(= "running" (get-in % [:attributes :agent-run/phase])))
                  a-fact (run-fact a-before)
                  b-fact (run-fact b-before)
                  _ (assert-run-fact! a-fact)
                  _ (assert-run-fact! b-fact)
                  a-after (poll-show! strand-env workspace a-run
                                      #(contains? #{"running" "done"}
                                                  (get-in % [:attributes :agent-run/phase])))
                  b-after (poll-show! strand-env workspace b-run
                                      #(contains? #{"running" "done"}
                                                  (get-in % [:attributes :agent-run/phase])))
                  a-after-fact (run-fact a-after)
                  b-after-fact (run-fact b-after)]
              (when-not (= (select-keys a-fact [:id :attempt :owner :key :handle])
                           (select-keys a-after-fact [:id :attempt :owner :key :handle]))
                (throw (ex-info "A was replayed or lost its stable custody fact"
                                {:before a-fact :after a-after-fact})))
              (when-not (= (select-keys b-fact [:id :attempt :owner :key :handle])
                           (select-keys b-after-fact [:id :attempt :owner :key :handle]))
                (throw (ex-info "B was replayed or lost its stable custody fact"
                                {:before b-fact :after b-after-fact})))
              (poll-show! strand-env workspace a-run
                          #(= "done" (get-in % [:attributes :agent-run/phase])))
              (poll-show! strand-env workspace b-run
                          #(= "done" (get-in % [:attributes :agent-run/phase])))
              (command! [strand-bin "--workspace" workspace "update" b-task "--state" "closed"] strand-env)
              (let [c-result (agent! strand-env workspace ["delegate" c-task "--harness" "b"])
                    c-run (:id (:run c-result))
                    second-send (command-result [strand-bin "--workspace" workspace "agent"
                                                 "delegate" c-task "--harness" "b"] strand-env)]
                (when (zero? (:exit second-send))
                  (throw (ex-info "B-to-C delegation was sent more than once" {:result second-send})))
                (when-not (= 1 (count (agent! strand-env workspace ["ps" "--for" c-task])))
                  (throw (ex-info "B-to-C delegation did not have exactly one run" {:task c-task})))
                (poll-show! strand-env workspace c-run
                            #(= "done" (get-in % [:attributes :agent-run/phase]))))))
          {:millstrand-sha millstrand-sha :custody-reconciled true :delegated-once true}
          (finally
            (cleanup! #(command-result [mill-bin "weaver" "stop" "--workspace"
                                        (.getCanonicalPath workspace)] mill-env)
                      #(stop-process! mill)))))
      (finally
        (delete-tree! root)))))

(deftest default-millstrand-tools-materialize-from-the-sibling-repository
  (testing "the default uses a disposable source at the pinned Millstrand revision"
    (let [root (.toFile (java.nio.file.Files/createTempDirectory
                         (.toPath (io/file "/tmp"))
                         "ah-tools-test-"
                         (make-array java.nio.file.attribute.FileAttribute 0)))
          project-root (.getCanonicalPath (io/file "/tmp/agent-harness"))
          expected-source (.getCanonicalPath (io/file root "millstrand"))
          calls (atom [])]
      (try
        (with-redefs [materialize-millstrand-source!
                      (fn [origin target]
                        (swap! calls conj [origin target])
                        target)]
          (is (= {:source expected-source
                  :mill-bin (str (io/file expected-source "bin/mill"))
                  :strand-bin (str (io/file expected-source "bin/strand"))}
                 (resolve-millstrand-tools! project-root root {})))
          (is (= [[(.getCanonicalPath (io/file project-root "../skein-src")) expected-source]]
                 @calls)))
        (finally
          (delete-tree! root))))))

(deftest explicit-millstrand-source-and-tool-overrides-remain-authoritative
  (testing "explicit source and binaries bypass default materialization"
    (let [root (.toFile (java.nio.file.Files/createTempDirectory
                         (.toPath (io/file "/tmp"))
                         "ah-tools-override-test-"
                         (make-array java.nio.file.attribute.FileAttribute 0)))
          source (io/file root "explicit-millstrand")
          mill-bin (io/file root "bin/mill")
          strand-bin (io/file root "bin/strand")]
      (try
        (.mkdirs source)
        (.mkdirs (.getParentFile mill-bin))
        (spit mill-bin "#!/bin/sh\n")
        (spit strand-bin "#!/bin/sh\n")
        (.setExecutable mill-bin true)
        (.setExecutable strand-bin true)
        (with-redefs [materialize-millstrand-source!
                      (fn [_ _] (throw (ex-info "default materialization should not run" {})))]
          (is (= {:source (.getCanonicalPath source)
                  :mill-bin (.getPath mill-bin)
                  :strand-bin (.getPath strand-bin)}
                 (resolve-millstrand-tools! "/project" root
                                            {"AGENT_HARNESS_MILLSTRAND_SOURCE" (.getPath source)
                                             "MILLSTRAND_MILL_BIN" (.getPath mill-bin)
                                             "MILLSTRAND_STRAND_BIN" (.getPath strand-bin)}))))
        (finally
          (delete-tree! root))))))

(deftest explicit-millstrand-source-validates-derived-tool-paths
  (let [root (.toFile (java.nio.file.Files/createTempDirectory
                       (.toPath (io/file "/tmp"))
                       "ah-source-derived-tools-test-"
                       (make-array java.nio.file.attribute.FileAttribute 0)))
        source (io/file root "explicit-millstrand")
        expected (.getCanonicalPath (io/file source "bin/mill"))]
    (try
      (.mkdirs source)
      (with-redefs [materialize-millstrand-source!
                    (fn [_ _] (throw (ex-info "default materialization should not run" {})))]
        (let [error (try
                      (resolve-millstrand-tools! "/project" root
                                                 {"AGENT_HARNESS_MILLSTRAND_SOURCE" (.getPath source)})
                      nil
                      (catch clojure.lang.ExceptionInfo error error))]
          (is error "a missing derived executable must fail at the boundary")
          (is (str/includes? (.getMessage error) "setting=MILLSTRAND_MILL_BIN"))
          (is (str/includes? (.getMessage error) (str "value=\"" expected "\"")))
          (is (str/includes? (.getMessage error) "expected an existing executable file"))))
      (finally
        (delete-tree! root)))))

(deftest explicit-millstrand-tool-overrides-fail-at-the-boundary
  (let [root (.toFile (java.nio.file.Files/createTempDirectory
                       (.toPath (io/file "/tmp"))
                       "ah-tools-boundary-test-"
                       (make-array java.nio.file.attribute.FileAttribute 0)))
        source (io/file root "explicit-millstrand")
        valid-mill (io/file root "bin/valid-mill")
        valid-strand (io/file root "bin/valid-strand")]
    (try
      (.mkdirs source)
      (.mkdirs (.getParentFile valid-mill))
      (spit valid-mill "#!/bin/sh\n")
      (spit valid-strand "#!/bin/sh\n")
      (.setExecutable valid-mill true)
      (.setExecutable valid-strand true)
      (doseq [[setting value expected]
              [["AGENT_HARNESS_MILLSTRAND_SOURCE" (str (io/file root "missing"))
                "an existing directory"]
               ["MILLSTRAND_MILL_BIN" (str (io/file root "missing-mill"))
                "an existing executable file"]
               ["MILLSTRAND_STRAND_BIN" (str (io/file root "missing-strand"))
                "an existing executable file"]]]
        (let [env {"AGENT_HARNESS_MILLSTRAND_SOURCE" (.getPath source)
                   "MILLSTRAND_MILL_BIN" (.getPath valid-mill)
                   "MILLSTRAND_STRAND_BIN" (.getPath valid-strand)}
              env (assoc env setting value)
              error (try
                      (resolve-millstrand-tools! "/project" root env)
                      nil
                      (catch clojure.lang.ExceptionInfo error error))]
          (is error (str setting " must fail at the boundary"))
          (is (str/includes? (.getMessage error) (str "setting=" setting)))
          (is (str/includes? (.getMessage error) (str "value=\"" value "\"")))
          (is (str/includes? (.getMessage error) (str "expected " expected)))))
      (finally
        (delete-tree! root)))))

(deftest cleanup-reports-weaver-failure-after-attempting-mill-stop
  (let [events (atom [])
        error (try
                (cleanup! #(do (swap! events conj :weaver)
                               {:exit 1 :output "weaver stop failed"})
                          #(swap! events conj :mill))
                nil
                (catch clojure.lang.ExceptionInfo error error))]
    (is (= [:weaver :mill] @events))
    (is (str/includes? (.getMessage error) "weaver-shutdown"))
    (is (= :weaver-shutdown (-> error ex-data :failures first :operation)))
    (is (= 1
           (-> error ex-data :failures first :error ex-data :exit)))))
