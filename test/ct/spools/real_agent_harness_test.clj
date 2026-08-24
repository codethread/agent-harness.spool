(ns ct.spools.real-agent-harness-test
  "Disposable external Mill + Weaver acceptance coverage for Agent Harness custody."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [ct.spools.test-support :as test-support]))

(def ^:private m0-sha
  "The Millstrand source revision exercised by the guarded acceptance test."
  "4df5f35b2ecd1833f18fe9a161f0181cac66c806")

(def ^:private module-roots
  "Agent Harness roots projected into the disposable M0 launch basis."
  {'ct.spools/agent-run "agent-run"
   'ct.spools/delegation "delegation"
   'ct.spools/harness-core "harness-core"
   'ct.spools/agent-cli "agent-cli"
   'ct.spools/claude-harness "claude-harness"
   'ct.spools/codex-harness "codex-harness"
   'ct.spools/pi-harness "pi-harness"
   'ct.spools/cursor-harness "cursor-harness"
   'ct.spools/bench "bench"})

(defn enabled?
  "Return whether the external Mill + Weaver acceptance is explicitly enabled."
  []
  (= "1" (System/getenv "MILLSTRAND_REAL_INTEGRATION")))

(defn- required-env
  [name default]
  (let [value (or (System/getenv name) default)]
    (when (str/blank? value)
      (throw (ex-info (str name " must be non-blank") {:env name})))
    value))

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

(defn- source-revision!
  [source cwd]
  (let [result (command-result ["git" "-C" source "rev-parse" "HEAD"] {:cwd cwd :env {}})]
    (when-not (zero? (:exit result))
      (throw (ex-info "Unable to inspect the pinned Millstrand source revision" result)))
    (let [revision (str/trim (:output result))]
      (when-not (= m0-sha revision)
        (throw (ex-info "External acceptance requires exact Millstrand M0"
                        {:expected m0-sha :actual revision :source source})))
      revision)))

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
  (doseq [[_ root] module-roots]
    (symlink! (io/file project-root root)
              (io/file (.getParentFile (io/file workspace)) root)))
  (let [spools-file (io/file workspace "spools.edn")
        spools (edn/read-string (slurp spools-file))
        spools (reduce-kv (fn [config lib root]
                            (assoc-in config [:spools lib] {:local/root
                                                            (str (io/file project-root root))}))
                          spools module-roots)]
    (spit spools-file (pr-str spools)))
  (spit (io/file workspace "init.clj")
        (str "(when-not (find-ns 'millstrand.api.current.alpha)\n"
             "  (require '[millstrand.api.current.alpha])\n"
             "  (require '[millstrand.api.runtime.alpha]))\n"
             "(def runtime ((var-get (ns-resolve 'millstrand.api.current.alpha 'runtime))))\n"
             "(def module! (var-get (ns-resolve 'millstrand.api.runtime.alpha 'module!)))\n"
             "(module! runtime :millstrand/spools-batteries\n"
             "                 {:ns 'millstrand.spools.batteries\n"
             "                  :spools ['millstrand.spools/batteries]\n"
             "                  :required? true})\n"
             "(module! runtime :millhouse/spools-identity\n"
             "                 {:ns 'millhouse.spools.identity\n"
             "                  :spools ['millhouse.spools/identity]\n"
             "                  :required? true})\n"
             "(module! runtime :millstrand/spools-agent-run\n"
             "                 {:ns 'ct.spools.agent-run\n"
             "                  :spools ['ct.spools/agent-run 'millhouse.spools/identity]\n"
             "                  :after [:millstrand/spools-batteries :millhouse/spools-identity]\n"
             "                  :required? true})\n"
             "(module! runtime :millstrand/spools-delegation\n"
             "                 {:ns 'ct.spools.delegation\n"
             "                  :spools ['ct.spools/delegation 'ct.spools/agent-run]\n"
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
             "                  :spools ['ct.spools/agent-run]\n"
             "                  :after [:millstrand/spools-agent-run]\n"
             "                  :required? true})\n"))
  (spit (io/file workspace "config/integration_harnesses.clj")
        (str "(ns integration-harnesses\n"
             "  (:require [ct.spools.agent-run :as shuttle]))\n"
             "(shuttle/defharnesses! integration-harnesses\n"
             "  \"Disposable harnesses for custody replacement acceptance.\"\n"
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
  "Run the guarded external Mill + Weaver Agent Harness acceptance world.

  The caller enables this only with `MILLSTRAND_REAL_INTEGRATION=1`; the world
  uses a short-lived M0 source overlay, exact process handles, and no shared
  or canonical Weaver state."
  []
  (when (enabled?)
    (let [project-root (.getCanonicalPath (io/file (System/getProperty "user.dir")))
          m0-source (required-env "MILLSTRAND_M0_SOURCE"
                                  (.getCanonicalPath (io/file project-root "../skein-src")))
          mill-bin (required-env "MILLSTRAND_MILL_BIN" (str (io/file m0-source "bin/mill")))
          strand-bin (required-env "MILLSTRAND_STRAND_BIN" (str (io/file m0-source "bin/strand")))
          _ (source-revision! m0-source project-root)
          root (.toFile (java.nio.file.Files/createTempDirectory
                         (.toPath (io/file "/tmp"))
                         "ah-"
                         (make-array java.nio.file.attribute.FileAttribute 0)))
          state-root (io/file root "s")
          workspace-root (io/file root "w")
          workspace (io/file workspace-root ".millstrand")
          overlay (io/file root "m")
          _ (.mkdirs state-root)
          _ (.mkdirs workspace-root)
          _ (.mkdirs overlay)
          _ (write-source-overlay! overlay m0-source project-root workspace)
          env {"XDG_STATE_HOME" (.getCanonicalPath state-root)
               "MILLSTRAND_SOURCE" (.getCanonicalPath overlay)}
          mill-env {:cwd project-root :env env :mill-bin mill-bin :strand-bin strand-bin}
          mill (start-process! [mill-bin "start"] mill-env)
          strand-env {:cwd (.getCanonicalPath workspace-root) :env env :strand-bin strand-bin}]
      (try
        (test-support/poll-until #(zero? (:exit (command-result [mill-bin "status"] mill-env)))
                                 {:timeout-ms 30000
                                  :interval-ms 100
                                  :on-timeout #(throw (ex-info "M0 Mill did not become ready" {:pid (:pid mill)}))})
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
                before (command! [mill-bin "weaver" "status" "--workspace" workspace] mill-env)
                restart (command! [mill-bin "weaver" "restart" "--workspace" workspace] mill-env)
                after (command! [mill-bin "weaver" "status" "--workspace" workspace] mill-env)
                _ (when-not (and (= "restart" (:operation restart))
                                 (= "running" (:state restart))
                                 (string? (:generation_id restart))
                                 (not= (:generation_id before) (:generation_id after)))
                    (throw (ex-info "Ordinary planned Weaver replacement was not performed"
                                    {:before before :restart restart :after after})))
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
        {:m0-sha m0-sha :replacement true :custody-reconciled true :delegated-once true}
        (finally
          (try
            (command-result [mill-bin "weaver" "stop" "--workspace" (.getCanonicalPath workspace)] mill-env)
            (catch Throwable _ nil))
          (stop-process! mill))))))
