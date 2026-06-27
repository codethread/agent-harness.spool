(ns skein.events.alpha
  (:require [skein.client :as client]
            [skein.weaver.runtime :as runtime]
            [skein.repl :as repl]))

(defn- call-daemon [op & args]
  (if-let [rt @runtime/current-runtime]
    (apply (requiring-resolve (symbol "skein.weaver.api" (name op))) rt args)
    (apply client/call-world (repl/connected-config-dir) {} op args)))

(defn register!
  "Register or replace an event handler in the selected weaver runtime.

  `key` is a stable keyword/symbol/string, `types` is a non-empty set of event
  type keywords, and `fn-sym` is a fully qualified function symbol resolvable in
  the weaver JVM. Optional `metadata` must be data-first. The handler receives
  one event map."
  ([key types fn-sym]
   (register! key types fn-sym {}))
  ([key types fn-sym metadata]
   (call-daemon :register-event-handler! key types fn-sym metadata)))

(defn unregister!
  "Unregister an event handler by stable key."
  [key]
  (call-daemon :unregister-event-handler! key))

(defn handlers
  "Return data-first event handler registry entries."
  []
  (call-daemon :event-handlers))

(defn recent-failures
  "Return recent event handler failures as data-first maps."
  []
  (call-daemon :recent-event-failures))
