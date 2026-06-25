(ns todo.repl
  (:require [next.jdbc :as jdbc]
            [todo.db :as db]
            [todo.query :as query]))

(defonce ^:private active-datasource (atom nil))
(defonce ^:private query-registry (atom {}))

(defn- ds []
  (or @active-datasource
      (throw (ex-info "No todo database is open. Call (open! \"path/to/todo.sqlite\") before using todo.repl helpers."
                      {:helper 'open!}))))

(def ^:private json-columns #{:attributes :edge_attributes :blockers})

(defn- unpack-json [row]
  (reduce-kv (fn [m k v]
               (assoc m k (if (and (json-columns k) (string? v))
                            (db/<-json v)
                            v)))
             {}
             row))

(defn- unpack [x]
  (cond
    (map? x) (unpack-json x)
    (sequential? x) (mapv unpack-json x)
    :else x))

(defn open! [db-file]
  (reset! active-datasource (db/datasource db-file)))

(defn init! []
  (db/init! (ds)))

(defn task!
  ([title]
   (task! title {}))
  ([title attributes]
   (unpack (db/add-task! (ds) {:title title :attributes attributes})))
  ([title status attributes]
   (unpack (db/add-task! (ds) {:title title :status status :attributes attributes}))))

(defn update!
  ([id patch]
   (let [{:keys [title status attributes edges]} patch]
     (jdbc/with-transaction [tx (ds)]
       (doseq [{:keys [to type attributes]} edges]
         (db/add-edge! tx {:from id :to to :type type :attributes attributes}))
       (unpack (db/update-task! tx id {:title title :status status :attributes attributes}))))))

(defn task [id]
  (unpack (db/get-task (ds) id)))

(defn defquery! [query-name query-def]
  (query/validate-query-def! query-def)
  (swap! query-registry assoc query-name query-def)
  query-name)

(defn load-queries! [path]
  (let [registry (query/read-edn-file path)]
    (when-not (map? registry)
      (throw (ex-info "Query file must contain one EDN map of query names to query definitions" {:path path})))
    (doseq [[query-name query-def] registry]
      (when-not (or (symbol? query-name) (keyword? query-name))
        (throw (ex-info "Query names must be symbols or keywords" {:query query-name})))
      (query/validate-query-def! query-def))
    (swap! query-registry merge registry)
    (keys registry)))

(defn queries []
  @query-registry)

(defn- resolve-query [query-or-def]
  (if (or (symbol? query-or-def) (keyword? query-or-def))
    (query/query-def @query-registry query-or-def)
    query-or-def))

(defn query
  ([query-or-def]
   (query query-or-def {}))
  ([query-or-def params]
   (unpack (db/query-tasks (ds) (resolve-query query-or-def) params))))

(defn tasks
  ([]
   (unpack (db/all-tasks (ds))))
  ([query-or-def]
   (query query-or-def))
  ([query-or-def params]
   (query query-or-def params)))

(defn ready
  ([]
   (unpack (db/ready-tasks (ds))))
  ([query-or-def]
   (ready query-or-def {}))
  ([query-or-def params]
   (unpack (db/ready-tasks (ds) (resolve-query query-or-def) params))))
