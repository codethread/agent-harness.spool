(ns atom.libs.alpha
  (:require [todo.client :as client]
            [todo.daemon.runtime :as runtime]
            [todo.repl :as repl]))

(defn- call-daemon [op & args]
  (if-let [rt @runtime/current-runtime]
    (apply (requiring-resolve (symbol "todo.daemon.api" (name op))) rt args)
    (apply client/call-world (repl/connected-config-dir) {} op args)))

(defn approved
  "Return normalized approved library config from the selected daemon config-dir libs.edn."
  []
  (call-daemon :approved-libs))
