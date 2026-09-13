(require '[millstrand.api.current.alpha :as current]
         '[millstrand.api.runtime.alpha :as runtime]
         '[ct.spools.codethread.bootstrap :as codethread])

(def runtime (current/runtime))

(runtime/module! runtime :millstrand/spools-batteries
                 {:ns 'millstrand.spools.batteries
                  :required? true})

;; Register shared identity, Workflow, Harnesses, aliases, reviewers, and
;; landing before workspace-specific providers, aliases, and workflows. The
;; bootstrap deliberately leaves the :agent executor inactive until those
;; consumers have reconciled.
(codethread/register! runtime)

;; Workspace-owned Workflow providers and Devflow remain explicit consumer
;; choices; their published modules provide the CLI and adapter surfaces used
;; by this repository.
(runtime/module! runtime :millhouse/spools-workflow-providers
                 {:ns 'millhouse.spools.workflow.spool
                  :after [:millhouse/spools-workflow]
                  :required? true})

(runtime/module! runtime :devflow
                 {:ns 'ct.spools.devflow
                  :after [:millhouse/spools-workflow]
                  :required? true})

(runtime/module! runtime :devflow/kanban-adapter
                 {:ns 'ct.spools.devflow-kanban-adapter
                  :after [:devflow :millhouse/spools-kanban
                          :millhouse/spools-workflow]
                  :required? true})

;; Hand-authored workflows and the repository's configuration remain consumer
;; modules, so the shared catalog does not absorb this workspace's policy.
(runtime/module! runtime :workflows
                 {:file "config/workflows.clj"
                  :after [:millhouse/spools-workflow]
                  :required? true})

(runtime/module! runtime :codethread/config-help
                 {:ns 'ct.spools.codethread.help
                  :after [:millstrand/spools-batteries]
                  :required? true})
(runtime/module! runtime :codethread/config-devflow
                 {:ns 'ct.spools.codethread.devflow
                  :required? true})
(runtime/module! runtime :codethread/config
                 {:ns 'ct.spools.codethread.config
                  :after [:codethread/config-help
                          :codethread/config-devflow
                          :millstrand/spools-batteries
                          :devflow/kanban-adapter]
                  :required? true})
(runtime/module! runtime :codethread/ralph
                 {:ns 'ct.spools.codethread.ralph
                  :after [:millhouse/spools-workflow]
                  :required? true})

;; The shared bootstrap owns the sole :agent executor. Register it last and
;; name every consumer whose resources must reconcile before its initial scan.
(codethread/register-executor!
 runtime [:millhouse/spools-workflow-providers
          :devflow
          :devflow/kanban-adapter
          :workflows
          :codethread/config-help
          :codethread/config-devflow
          :codethread/config
          :codethread/ralph])
