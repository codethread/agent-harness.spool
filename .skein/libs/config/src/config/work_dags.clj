(ns config.work-dags
  "Repo-local operations for inspecting active Skein work DAGs."
  (:require [clojure.set :as set]
            [skein.weaver.api :as api]
            [skein.weaver.runtime :as runtime]))

(def current-dag-roots-query
  "Query for active strands that own active child work through `parent-of`."
  [:and
   [:= :state "active"]
   [:edge/out "parent-of" [:= :state "active"]]])

(defn- active-strands-by-id
  "Return active strands keyed by id."
  [rt]
  (into {}
        (map (juxt :id identity))
        (api/list rt [:= :state "active"] {})))

(defn- internal-active-edges
  "Return edges whose endpoints are both active strands."
  [active-ids edges]
  (->> edges
       (filter #(and (contains? active-ids (:from_strand_id %))
                     (contains? active-ids (:to_strand_id %))))
       (sort-by (juxt :from_strand_id :to_strand_id :edge_type))
       vec))

(defn- parent-root-ids
  "Return active root ids for parent-child work DAGs."
  [active-ids parent-edges]
  (let [parents (set (map :from_strand_id parent-edges))
        children (set (map :to_strand_id parent-edges))]
    (->> (set/difference parents children)
         (filter active-ids)
         sort
         vec)))

(defn- descendants-by-root
  "Return the active parent-of subgraph below one root id."
  [rt active-ids root-id]
  (let [{:keys [strands edges]} (api/subgraph rt [root-id] {:type "parent-of"})
        active-strand-ids (set (keep (fn [{:keys [id state]}]
                                       (when (= "active" state) id))
                                     strands))
        included-ids (conj active-strand-ids root-id)]
    {:root-id root-id
     :strand-ids (->> included-ids (filter active-ids) sort vec)
     :parent-of (internal-active-edges active-ids edges)}))

(defn- dependency-edges-for
  "Return active depends-on edges reachable from the included strand ids."
  [rt active-ids strand-ids]
  (let [{:keys [edges]} (api/subgraph rt strand-ids {:type "depends-on"})]
    (internal-active-edges active-ids edges)))

(defn- summarize-strand
  "Return the compact strand shape used by the current-dags operation."
  [strand]
  (select-keys strand [:id :title :state :attributes]))

(defn current-dags-op
  "Return active parent-of work DAGs and their active depends-on edges.

  This is intentionally an operation rather than only a named query because the
  CLI query surface returns flat strand rows; this handler projects the active
  parent-child roots, hierarchy edges, dependency edges, and compact strand rows
  into one JSON-compatible structure for agents and humans."
  [_ctx]
  (let [rt @runtime/current-runtime
        active-by-id (active-strands-by-id rt)
        active-ids (set (keys active-by-id))
        all-active-ids (sort active-ids)
        parent-edges (->> (:edges (api/subgraph rt all-active-ids {:type "parent-of"}))
                          (internal-active-edges active-ids))
        roots (parent-root-ids active-ids parent-edges)
        dags (mapv (fn [root-id]
                     (let [{:keys [strand-ids parent-of]} (descendants-by-root rt active-ids root-id)]
                       {:root (summarize-strand (active-by-id root-id))
                        :strands (mapv (comp summarize-strand active-by-id) strand-ids)
                        :parent_of_edges parent-of
                        :depends_on_edges (dependency-edges-for rt active-ids strand-ids)}))
                   roots)]
    {:query "current-dag-roots"
     :roots roots
     :dags dags}))

(defn install!
  "Install repo-local work DAG query and operation."
  []
  {:queries (api/register-query! 'current-dag-roots current-dag-roots-query)
   :ops [(api/register-op!
          'current-dags
          "Show active parent-of work DAGs with active depends-on edges"
          'config.work-dags/current-dags-op)]})
