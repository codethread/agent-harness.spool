(require '[millstrand.api.current.alpha :as current]
         '[millstrand.api.runtime.alpha :as runtime])

(def runtime (current/runtime))

(runtime/module! runtime :millstrand/spools-batteries
                 {:ns 'millstrand.spools.batteries
                  :spools ['millstrand.spools/batteries]})
(runtime/module! runtime :module-adapters
                 {:file "config/module_adapters.clj"
                  :after [:millstrand/spools-batteries]})

(runtime/module! runtime :millhouse/spools-workflow
                 {:ns 'millhouse.spools.workflow
                  :spools ['millhouse.spools/workflow]
                  :required? true})
(runtime/module! runtime :millhouse/spools-workflow-cli
                 {:ns 'millhouse.spools.workflow.cli
                  :spools ['millhouse.spools/workflow]
                  :after [:millhouse/spools-workflow]
                  :required? true})
(runtime/module! runtime :millhouse/spools-shell
                 {:ns 'millhouse.spools.executors.shell
                  :spools ['millhouse.spools.executors/shell
                           'millhouse.spools/workflow]
                  :after [:millhouse/spools-workflow]
                  :required? true})

(runtime/module! runtime :millstrand/spools-agent-run
                 {:ns 'ct.spools.agent-run
                  :spools ['ct.spools/agent-run]
                  :required? true})
(runtime/module! runtime :millstrand/spools-delegation
                 {:ns 'ct.spools.delegation
                  :spools ['ct.spools/delegation 'ct.spools/agent-run]
                  :after [:millstrand/spools-agent-run]
                  :required? true})
(runtime/module! runtime :harnesses
                 {:file "config/harnesses.clj"
                  :spools ['ct.spools/delegation 'ct.spools/agent-run]
                  :after [:millstrand/spools-agent-run :millstrand/spools-delegation]
                  :required? true})

(runtime/module! runtime :millstrand/spools-harness-core
                 {:ns 'ct.spools.harness-core
                  :spools ['ct.spools/harness-core]
                  :required? true})
(runtime/module! runtime :millstrand/spools-claude-harness
                 {:ns 'ct.spools.claude-harness
                  :spools ['ct.spools/claude-harness 'ct.spools/harness-core]
                  :after [:millstrand/spools-harness-core]
                  :required? true})
(runtime/module! runtime :millstrand/spools-codex-harness
                 {:ns 'ct.spools.codex-harness
                  :spools ['ct.spools/codex-harness 'ct.spools/harness-core]
                  :after [:millstrand/spools-harness-core]
                  :required? true})
(runtime/module! runtime :millstrand/spools-pi-harness
                 {:ns 'ct.spools.pi-harness
                  :spools ['ct.spools/pi-harness 'ct.spools/harness-core]
                  :after [:millstrand/spools-harness-core]
                  :required? true})
(runtime/module! runtime :millstrand/spools-cursor-harness
                 {:ns 'ct.spools.cursor-harness
                  :spools ['ct.spools/cursor-harness 'ct.spools/harness-core]
                  :after [:millstrand/spools-harness-core]
                  :required? true})
(runtime/module! runtime :millstrand/spools-agent-cli
                 {:ns 'ct.spools.agent-cli
                  :spools ['ct.spools/agent-cli 'ct.spools/harness-core]
                  :after [:millstrand/spools-harness-core
                          :millstrand/spools-claude-harness
                          :millstrand/spools-codex-harness
                          :millstrand/spools-pi-harness
                          :millstrand/spools-cursor-harness]
                  :required? true})
(runtime/module! runtime :harness-next
                 {:file "config/harness-next.clj"
                  :spools ['ct.spools/harness-core]
                  :after [:millstrand/spools-harness-core
                          :millstrand/spools-claude-harness
                          :millstrand/spools-codex-harness
                          :millstrand/spools-pi-harness
                          :millstrand/spools-cursor-harness
                          :millstrand/spools-agent-cli]
                  :required? true})

(runtime/module! runtime :workflows
                 {:file "config/workflows.clj"
                  :spools ['millhouse.spools/workflow]
                  :after [:millhouse/spools-workflow]
                  :required? true})

(runtime/module! runtime :millstrand/spools-subagent
                 {:ns 'ct.spools.executors.subagent
                  :spools ['ct.spools/agent-run 'millhouse.spools/workflow]
                  :after [:millstrand/spools-agent-run :millhouse/spools-workflow
                          :harnesses :workflows]
                  :required? true})

(runtime/module! runtime :millstrand/spools-kanban
                 {:ns 'ct.spools.kanban
                  :spools ['codethread/kanban]
                  :required? true})
