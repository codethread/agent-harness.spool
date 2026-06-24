(ns todo.cli
  (:gen-class)
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [todo.db :as db]))

(def usage
  (str "Todo agent CLI\n"
       "\n"
       "Usage:\n"
       "  clojure -M:todo [--db <path>] [--format human|edn|json] <command> [args]\n"
       "\n"
       "Commands:\n"
       "  init\n"
       "  add <id> <title> [--attr key=value ...]\n"
       "  link <from-id> <to-id> <edge-type> [--attr key=value ...]\n"
       "  show <id>\n"
       "  list\n"
       "  deps <id>\n"
       "  transitive-deps <id>\n"
       "  blocking <id>\n"
       "  ready\n"
       "  by-attr <key> <value>\n"
       "  done <id>\n"
       "\n"
       "Options:\n"
       "  --db <path>             SQLite database path (default: todo.sqlite)\n"
       "  --format <mode>        Output mode for query commands: human, edn, json\n"
       "  --attr key=value       Repeatable string task or edge attribute.\n"))

(def query-commands #{"show" "list" "deps" "transitive-deps" "blocking" "ready" "by-attr"})
(def output-formats #{"human" "edn" "json"})
(def commands (conj query-commands "init" "add" "link" "done"))

(defn fail! [message]
  (binding [*out* *err*]
    (println "Error:" message)
    (println)
    (println usage))
  (System/exit 1))

(defn parse-attr [s]
  (let [[k v] (str/split s #"=" 2)]
    (when (or (str/blank? k) (nil? v))
      (fail! (str "Malformed attribute: " s)))
    [(keyword k) v]))

(defn collect-attrs [args]
  (loop [remaining args
         attrs {}]
    (if (empty? remaining)
      attrs
      (let [[flag value & more] remaining]
        (when-not (= "--attr" flag)
          (fail! (str "Unknown or misplaced argument: " flag)))
        (when (nil? value)
          (fail! "Missing value after --attr"))
        (let [[k v] (parse-attr value)]
          (recur more (assoc attrs k v)))))))

(defn parse-global-options [args]
  (loop [remaining args
         opts {:db db/default-db-file :format "human"}]
    (let [[arg value & more] remaining]
      (case arg
        nil [opts nil []]
        "--db" (do
                 (when (nil? value) (fail! "Missing value after --db"))
                 (recur more (assoc opts :db value)))
        "--format" (do
                     (when (nil? value) (fail! "Missing value after --format"))
                     (when-not (output-formats value)
                       (fail! (str "Invalid output format: " value)))
                     (recur more (assoc opts :format value)))
        [opts arg (rest remaining)]))))

(defn normalize-row [row]
  (reduce-kv (fn [m k v]
               (assoc m k (if (and (string? v)
                                    (or (= k :attributes)
                                        (str/ends-with? (name k) "attributes")))
                             (db/<-json v)
                             v)))
             {}
             row))

(defn normalize [result]
  (cond
    (map? result) (normalize-row result)
    (sequential? result) (mapv normalize-row result)
    :else result))

(defn print-result [format result]
  (let [result (normalize result)]
    (case format
      "human" (if (and (sequential? result) (empty? result))
                (println "(no rows)")
                (doseq [row (if (sequential? result) result [result])]
                  (prn row)))
      "edn" (prn result)
      "json" (println (json/write-str result)))))

(defn require-args [command args n]
  (when (not= (count args) n)
    (fail! (str command " requires exactly " n " argument" (when-not (= 1 n) "s")))))

(defn run-command! [ds command args]
  (case command
    "init" (do
             (require-args command args 0)
             (db/init! ds)
             {:database "initialized"})
    "add" (let [[id title & attrs] args]
            (when (< (count args) 2)
              (fail! "add requires at least 2 arguments"))
            (db/add-task! ds {:id id :title title :attributes (collect-attrs attrs)}))
    "link" (let [[from to type & attrs] args]
             (when (< (count args) 3)
               (fail! "link requires at least 3 arguments"))
             (db/add-edge! ds {:from from :to to :type type :attributes (collect-attrs attrs)}))
    "show" (do (require-args command args 1) (db/get-task ds (first args)))
    "list" (do (require-args command args 0) (db/all-tasks ds))
    "deps" (do (require-args command args 1) (db/task-dependencies ds (first args)))
    "transitive-deps" (do (require-args command args 1) (db/transitive-dependencies ds (first args)))
    "blocking" (do (require-args command args 1) (db/blocking-tasks ds (first args)))
    "ready" (do (require-args command args 0) (db/ready-tasks ds))
    "by-attr" (do (require-args command args 2) (db/tasks-by-attribute ds (keyword (first args)) (second args)))
    "done" (do (require-args command args 1) (db/update-task-status! ds (first args) "done"))))

(defn -main [& args]
  (let [[opts command command-args] (parse-global-options args)]
    (when (nil? command)
      (fail! "Missing command"))
    (when-not (commands command)
      (fail! (str "Unknown command: " command)))
    (try
      (let [result (run-command! (db/datasource (:db opts)) command command-args)]
        (when (or (query-commands command) (not= "human" (:format opts)))
          (print-result (:format opts) result)))
      (catch clojure.lang.ExceptionInfo e
        (fail! (.getMessage e)))
      (catch Exception e
        (fail! (.getMessage e))))))
