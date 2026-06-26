(ns skein.app
  (:require [clojure.string :as str]
            [skein.db :as db]))

(defn prompt [label]
  (print label)
  (flush)
  (read-line))

(defn parse-attrs [s]
  (if (str/blank? s)
    {}
    (into {}
          (for [pair (str/split s #",")
                :let [[k v] (map str/trim (str/split pair #"=" 2))]
                :when (and (seq k) (some? v))]
            [(keyword k) v]))))

(defn print-rows [rows]
  (if (seq rows)
    (doseq [row rows] (println row))
    (println "(no rows)")))

(defn add-strand [ds]
  (let [title (prompt "title: ")
        attrs (prompt "attributes (priority=high,due-date=2026-07-01): ")]
    (println (db/add-strand! ds {:title title :attributes (parse-attrs attrs)}))))

(defn add-dependency [ds]
  (let [from (prompt "strand id: ")
        to (prompt "depends on strand id: ")]
    (println (db/add-edge! ds {:from from :to to :type "depends-on" :attributes {}}))))

(defn menu []
  (println)
  (println "Todo graph")
  (println "1. list strands")
  (println "2. add strand")
  (println "3. add dependency")
  (println "4. blocked strands")
  (println "5. strands by priority")
  (println "6. dependencies for strand")
  (println "7. related edges for strand")
  (println "q. quit"))

(defn loop! [ds]
  (menu)
  (case (str/trim (or (prompt "> ") ""))
    "1" (do (print-rows (db/all-strands ds)) (recur ds))
    "2" (do (add-strand ds) (recur ds))
    "3" (do (add-dependency ds) (recur ds))
    "4" (do (print-rows (db/blocked-strands ds)) (recur ds))
    "5" (do (print-rows (db/strands-by-priority ds (prompt "priority: "))) (recur ds))
    "6" (do (print-rows (db/strand-dependencies ds (prompt "strand id: "))) (recur ds))
    "7" (do (print-rows (db/related-strands ds (prompt "strand id: "))) (recur ds))
    "q" (println "bye")
    (do (println "unknown command") (recur ds))))

(defn -main [& [db-file]]
  (let [ds (db/init! (db/datasource (or db-file db/default-db-file)))]
    (loop! ds)))
