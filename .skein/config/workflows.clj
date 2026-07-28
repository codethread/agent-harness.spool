(ns workflows
  "Index of this workspace's hand-authored workflows.

  One line per workflow. `register!` loads its definition file and registers the
  static definition Var it defines under the name a worker starts it by, so
  adding a workflow is a new file plus a line here, and deleting the line drops
  the registration by omission at the next refresh.

  Two mechanics are worth knowing before editing a definition file. Definitions
  are loaded rather than required, because `.skein` is a config directory and
  not a classpath root. And registration happens here rather than beside each
  definition, because a module's authoring forms are collected only from the
  module's own source file — a `defworkflow` in a loaded file would be refused
  as a foreign contribution. The upshot is that definition files stay inert:
  loading one defines a Var and registers nothing."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [skein.api.runtime.alpha :as runtime]
            [skein.spools.workflow :as workflow]))

(def ^:private definitions-dir
  "The `workflows` directory beside this file, holding one file per workflow."
  (io/file (.getParentFile (io/file *file*)) "workflows"))

(defn- register!
  "Load workflow `id`'s definition file and register the Var it defines.

  File and Var follow `require`'s own convention, so the name is the only thing
  a caller states: `:feature-iteration` loads `workflows/feature_iteration.clj`
  and registers `workflows.feature-iteration/feature-iteration`. A file that
  declares a different namespace or Var fails the next refresh loudly, naming
  the symbol that did not resolve."
  [id]
  (let [stem (name id)]
    (load-file (str (io/file definitions-dir (str (str/replace stem "-" "_") ".clj"))))
    (runtime/collect-entry! workflow/definition-kind
                            id
                            (symbol (str "workflows." stem) stem))))

(register! :feature-iteration)
