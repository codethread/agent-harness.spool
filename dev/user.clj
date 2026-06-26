(ns user
  (:require [skein.weaver.runtime :as runtime]
            [skein.repl :refer :all]))

(def demo-db "/tmp/skein-demo.sqlite")
(defonce ^:private demo-runtime (atom nil))

(defn start-demo-weaver!
  "Start the demo weaver explicitly. Call before demo!/seed-demo!, or start an equivalent weaver from the CLI."
  []
  (when @demo-runtime
    (throw (ex-info "Demo weaver is already started from this REPL" {:database demo-db})))
  (reset! demo-runtime (runtime/start! demo-db))
  {:database demo-db
   :status :daemon-started})

(defn stop-demo-weaver!
  "Stop the demo weaver started by start-demo-weaver!."
  []
  (let [rt (or @demo-runtime
               (throw (ex-info "No demo daemon was started from this REPL" {:database demo-db})))]
    (runtime/stop! rt)
    (reset! demo-runtime nil)
    {:database demo-db
     :status :daemon-stopped}))

(defn demo!
  "Connect to an already-running demo daemon and initialize the database."
  []
  (connect!)
  (init!)
  {:database demo-db
   :status :ready})

(defn seed-demo!
  "Initialize the demo database and add a small dependency graph."
  []
  (demo!)
  (let [design (strand! "Sketch model" {:priority "high" :demo-id "design"} {:active false})
        docs (strand! "Write docs" {:owner "agent" :demo-id "docs"})
        impl (strand! "Build feature" {:owner "agent" :demo-id "impl"})]
    (update! (:id docs) {:edges [{:type "depends-on" :to (:id design)}]})
    (update! (:id impl) {:edges [{:type "depends-on" :to (:id docs)}]})
    (strands)))

(comment
  (start-demo-weaver!)
  (demo!)
  (seed-demo!)
  (ready)
  (def docs-id (:id (first (filter #(= "docs" (get-in % [:attributes :demo-id])) (strands)))))
  (update! docs-id {:active false})
  (ready)
  (stop-demo-weaver!))
