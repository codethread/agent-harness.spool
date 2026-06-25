(ns atom.prelude.alpha
  "Optional interactive conveniences for trusted Atom REPL workflows."
  (:require [atom.bootstrap.alpha :as bootstrap]
            [atom.plugin.alpha :as plugin]))

(def use-defaults! bootstrap/use-defaults!)
(def register! plugin/register!)
(def plugins plugin/plugins)
(def plugin plugin/plugin)
(def load-plugin! plugin/load-plugin!)
