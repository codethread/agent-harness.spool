(ns skein.kanban-test
  "Tests for the kanban board spool against a disposable weaver runtime."
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [skein.api.runtime.alpha :as runtime-alpha]
            [skein.api.weaver.alpha :as api]
            [skein.spools.test-support :refer [with-runtime]]))

(defn- spool-root []
  (.getCanonicalPath (io/file "spools/kanban")))

(defn- install-kanban! [rt config-dir]
  (spit (io/file config-dir "spools.edn")
        (pr-str {:spools {'skein.spools/kanban {:local/root (spool-root)}}}))
  (runtime-alpha/sync! rt)
  (runtime-alpha/use! rt :skein/spools-kanban
                      {:ns 'skein.spools.kanban
                       :spools ['skein.spools/kanban]
                       :call 'skein.spools.kanban/install!
                       :required? true}))

(defn- op! [rt & argv]
  (api/op! rt 'kanban argv))

(deftest kanban-add-next-claim-and-finish-round-trip
  (with-runtime
    (fn [rt config-dir]
      (install-kanban! rt config-dir)
      (is (some #(= "kanban" (:name %)) (api/ops rt)))
      (testing "add creates a pending feature card"
        (let [added (op! rt "add" "Build active work convention" "--source" "devflow/rfcs/2026-07-02-feature-tracking-registry.md")
              id (get-in added [:card :id])
              stored (api/show rt id)]
          (is (= "Build active work convention" (:title stored)))
          (is (= "true" (get-in stored [:attributes :kanban/card])))
          (is (= "pending" (get-in stored [:attributes :kanban/status])))
          (is (= "feature" (get-in stored [:attributes :kanban/type])))
          (is (= "devflow/rfcs/2026-07-02-feature-tracking-registry.md"
                 (get-in stored [:attributes :kanban/source])))
          (testing "next serves the oldest pending feature"
            (is (= id (get-in (op! rt "next") [:next :id]))))
          (testing "claim requires owner and branch"
            (is (thrown-with-msg? clojure.lang.ExceptionInfo #"requires --owner"
                                  (op! rt "claim" id "--branch" "feature-branch")))
            (is (thrown-with-msg? clojure.lang.ExceptionInfo #"requires --branch"
                                  (op! rt "claim" id "--owner" "agent"))))
          (testing "claim stamps status and work-root attributes"
            (let [claimed (op! rt "claim" id "--owner" "agent" "--branch" "kanban-spool"
                               "--worktree" "/tmp/wt")]
              (is (= "claimed" (get-in claimed [:card :attributes :kanban/status])))
              (is (= "agent" (get-in claimed [:card :attributes :owner])))
              (is (= "kanban-spool" (get-in claimed [:card :attributes :branch])))
              ;; regression: the claimed status must survive the round trip to
              ;; storage (string/keyword attr-key collisions once dropped it)
              (is (= "claimed" (get-in (api/show rt id) [:attributes :kanban/status])))
              (is (nil? (:next (op! rt "next"))))
              (is (thrown-with-msg? clojure.lang.ExceptionInfo #"must be pending"
                                    (op! rt "claim" id "--owner" "other" "--branch" "b")))))
          (testing "finish closes the card with an outcome status"
            (let [finished (op! rt "finish" id)]
              (is (= "closed" (get-in finished [:card :state])))
              (is (= "done" (get-in finished [:card :attributes :kanban/status]))))))))))

(deftest kanban-refinement-lane-and-promote
  (with-runtime
    (fn [rt config-dir]
      (install-kanban! rt config-dir)
      (let [idea (op! rt "add" "Vague idea" "--status" "refinement")
            idea-id (get-in idea [:card :id])]
        (is (= "refinement" (get-in idea [:card :attributes :kanban/status])))
        (testing "refinement cards are not actionable"
          (is (nil? (:next (op! rt "next"))))
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"must be pending"
                                (op! rt "claim" idea-id "--owner" "a" "--branch" "b"))))
        (testing "promote moves the card into the pending lane"
          (is (= "pending" (get-in (op! rt "promote" idea-id)
                                   [:card :attributes :kanban/status])))
          (is (= idea-id (get-in (op! rt "next") [:next :id])))
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"must be refinement"
                                (op! rt "promote" idea-id))))
        (testing "add rejects unknown statuses and types"
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"pending or refinement"
                                (op! rt "add" "Bad lane" "--status" "someday")))
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"feature or epic"
                                (op! rt "add" "Bad type" "--type" "story"))))))))

(deftest kanban-epics-group-features
  (with-runtime
    (fn [rt config-dir]
      (install-kanban! rt config-dir)
      (let [epic-id (get-in (op! rt "add" "Big theme" "--type" "epic") [:card :id])
            feat-id (get-in (op! rt "add" "First slice" "--epic" epic-id) [:card :id])]
        (testing "epic features are linked with parent-of and shown on the board"
          (let [edges (:edges (api/subgraph rt [epic-id] {:type "parent-of"}))]
            (is (some #(and (= epic-id (:from_strand_id %))
                            (= feat-id (:to_strand_id %))) edges)))
          (let [board (op! rt "board")]
            (is (= [epic-id] (mapv :id (:epics board))))
            (is (= epic-id (:epic (first (:pending board)))))))
        (testing "epics are never served or claimed as work"
          (is (= feat-id (get-in (op! rt "next") [:next :id])))
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"cannot be claimed"
                                (op! rt "claim" epic-id "--owner" "a" "--branch" "b"))))
        (testing "epics cannot nest and epic targets must be epics"
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"cannot nest"
                                (op! rt "add" "Nested" "--type" "epic" "--epic" epic-id)))
          (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not an epic"
                                (op! rt "add" "Bad parent" "--epic" feat-id))))))))

(deftest kanban-notes-handover-and-card-view
  (with-runtime
    (fn [rt config-dir]
      (install-kanban! rt config-dir)
      (let [card-id (get-in (op! rt "add" "Crashable feature") [:card :id])]
        (op! rt "claim" card-id "--owner" "agent-a" "--branch" "crashable")
        (let [task (api/add rt {:title "Implement it" :attributes {:kind "task"}})
              review (api/add rt {:title "Review it" :attributes {:kind "review"}})]
          (api/update rt card-id {:edges [{:type "parent-of" :to (:id task)}
                                          {:type "parent-of" :to (:id review)}]})
          (api/update rt (:id review) {:edges [{:type "depends-on" :to (:id task)}]})
          (op! rt "note" card-id "Decided to keep lane names" "--author" "agent-a")
          (let [handover (op! rt "note" card-id
                              "Done: impl. Next: review. Validation: tests green."
                              "--author" "agent-a" "--handover")]
            (is (= "true" (get-in handover [:note :attributes :kanban/handover])))
            (is (= "closed" (get-in handover [:note :state]))))
          (testing "card view joins notes, latest handover, work, and frontier"
            (let [view (op! rt "card" card-id)]
              (is (= card-id (get-in view [:card :id])))
              (is (= 2 (count (:notes view))))
              (is (true? (get-in view [:latest-handover :handover])))
              (is (= "Done: impl. Next: review. Validation: tests green."
                     (get-in view [:latest-handover :body])))
              (is (= #{(:id task) (:id review)}
                     (set (map :id (:active-work view)))))
              ;; review depends on the task, so only the task is ready
              (is (= [(:id task)] (mapv :id (:ready view))))))
          (testing "board surfaces the latest handover on claimed cards"
            (let [claimed (first (:claimed (op! rt "board")))]
              (is (= card-id (:id claimed)))
              (is (true? (get-in claimed [:latest-handover :handover])))))
          (testing "notes reject non-card targets and blank text"
            (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not a kanban card"
                                  (op! rt "note" (:id task) "text")))
            (is (thrown-with-msg? clojure.lang.ExceptionInfo #"non-blank"
                                  (op! rt "note" card-id)))))))))

(deftest kanban-board-groups-lanes
  (with-runtime
    (fn [rt config-dir]
      (install-kanban! rt config-dir)
      (let [idea-id (get-in (op! rt "add" "Idea" "--status" "refinement") [:card :id])
            queued-id (get-in (op! rt "add" "Queued") [:card :id])
            working-id (get-in (op! rt "add" "Working") [:card :id])
            done-id (get-in (op! rt "add" "Done already") [:card :id])]
        (op! rt "claim" working-id "--owner" "agent" "--branch" "feature-x")
        (op! rt "finish" done-id "--outcome" "abandoned")
        (let [board (op! rt "board")]
          (is (= [idea-id] (mapv :id (:refinement board))))
          (is (= [queued-id] (mapv :id (:pending board))))
          (is (= [working-id] (mapv :id (:claimed board))))
          (is (= "feature-x" (:branch (first (:claimed board)))))
          (is (= 1 (get-in board [:closed :count])))
          (is (not (contains? board :unknown-status))))
        (is (= "abandoned" (get-in (api/show rt done-id) [:attributes :kanban/status])))))))

(deftest kanban-batch-weave-creates-cards-and-dependencies
  (with-runtime
    (fn [rt config-dir]
      (install-kanban! rt config-dir)
      (let [existing (api/add rt {:title "Existing blocker"})
            result (api/weave! rt :kanban-batch
                               {:items [{:key "design"
                                         :title "Design batch"
                                         :body "Design body"}
                                        {:key "docs"
                                         :title "Write docs"
                                         :deps ["design" (:id existing)]}]})
            design-id (get-in result [:refs "design"])
            docs-id (get-in result [:refs "docs"])
            design (api/show rt design-id)
            docs (api/show rt docs-id)
            edge-set (set (map (juxt :from_strand_id :to_strand_id :edge_type)
                               (:edges (api/subgraph rt [docs-id] {:type "depends-on"}))))]
        (is (= "Design batch" (:title design)))
        (is (= "Design body" (get-in design [:attributes :body])))
        (is (= "true" (get-in docs [:attributes :kanban/card])))
        (is (= "pending" (get-in docs [:attributes :kanban/status])))
        (is (contains? edge-set [docs-id design-id "depends-on"]))
        (is (contains? edge-set [docs-id (:id existing) "depends-on"]))))))

(deftest kanban-batch-weave-fails-loudly
  (with-runtime
    (fn [rt config-dir]
      (install-kanban! rt config-dir)
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Pattern input failed spec validation"
                            (api/weave! rt :kanban-batch
                                        {:items [{:key "x" :title "X" :surprise true}]})))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"item keys must be unique"
                            (api/weave! rt :kanban-batch
                                        {:items [{:key "x" :title "X"}
                                                 {:key "x" :title "Again"}]})))
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"target strand not found"
                            (api/weave! rt :kanban-batch
                                        {:items [{:key "x" :title "X" :deps ["missing-strand"]}]}))))))
