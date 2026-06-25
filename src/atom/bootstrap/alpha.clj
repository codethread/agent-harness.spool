(ns atom.bootstrap.alpha
  "Blessed bootstrap helpers for trusted daemon startup config."
  (:require [atom.plugin.alpha :as plugin]))

(def built-in-alpha-libraries
  "Initial built-in Atom alpha libraries registered by use-defaults!."
  [{:format-version 1
    :name 'atom.plugin.alpha
    :provides ['atom/plugin-helpers]}
   {:format-version 1
    :name 'atom.bootstrap.alpha
    :provides ['atom/bootstrap-defaults]}])

(defn use-defaults!
  "Register Atom's intentionally small built-in alpha library metadata.

  This is side-effectful and safe to call repeatedly during daemon startup or REPL
  reload workflows: duplicate registrations replace prior metadata by canonical
  name. It does not load local plugins from disk and does not require or load the
  optional prelude namespace. Returns useful bootstrap state for inspection."
  []
  (let [registered (mapv plugin/register! built-in-alpha-libraries)]
    {:registered registered
     :plugins (plugin/plugins)}))
