(ns workflows
  "Index of this workspace's hand-authored workflows.

  One line per workflow. `load-definition!` loads its declaration file and the
  typed `use-workflow!` form below selects the static definition Var it defines,
  so adding a workflow is a new file plus a line here, and deleting the line
  drops the selection by omission at the next refresh.

  Two mechanics are worth knowing before editing a definition file. Definitions
  are loaded rather than required, because `.millstrand` is a config directory and
  not a classpath root. And registration happens here rather than beside each
  definition, because a module's authoring forms are collected only from the
  module's own source file — a `defworkflow` in a loaded file would be refused
  as a foreign contribution. The upshot is that definition files stay inert:
  loading one defines a Var and selecting it here publishes it for this module."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [millhouse.spools.workflow :as workflow]))

(def ^:private definitions-dir
  "The `workflows` directory beside this file, holding one file per workflow."
  (io/file (.getParentFile (io/file *file*)) "workflows"))

(defn- load-definition!
  "Load workflow `id`'s definition file and return its defined Var.

  File and Var follow `require`'s own convention, so the name is the only thing
  a caller states: `:feature-iteration` loads `workflows/feature_iteration.clj`
  and defines `workflows.feature-iteration/feature-iteration`. A file that
  declares a different namespace or Var fails the selection below loudly."
  [id]
  (let [stem (name id)]
    (load-file (str (io/file definitions-dir
                              (str (str/replace stem "-" "_") ".clj"))))))

(load-definition! :feature-iteration)
(workflow/use-workflow! workflows.feature-iteration/feature-iteration)
