(require '[skein.api.current.alpha :as current]
         '[skein.api.runtime.alpha :as runtime])

(def runtime (current/runtime))

(runtime/module! runtime :skein/spools-batteries
                 {:ns 'skein.spools.batteries
                  :spools ['skein.spools/batteries]})
(runtime/module! runtime :module-adapters
                 {:file "config/module_adapters.clj"
                  :after [:skein/spools-batteries]})

(runtime/module! runtime :skein/spools-workflow
                 {:ns 'skein.spools.workflow
                  :spools ['skein.spools/workflow]
                  :required? true})
(runtime/module! runtime :skein/spools-workflow-cli
                 {:ns 'skein.spools.workflow.cli
                  :spools ['skein.spools/workflow]
                  :after [:skein/spools-workflow]
                  :required? true})
(runtime/module! runtime :skein/spools-shell
                 {:ns 'skein.spools.executors.shell
                  :spools ['skein.spools/workflow]
                  :after [:skein/spools-workflow]
                  :required? true})

(runtime/module! runtime :skein/spools-agent-run
                 {:ns 'ct.spools.agent-run
                  :spools ['ct.spools/agent-run]
                  :required? true})
(runtime/module! runtime :skein/spools-delegation
                 {:ns 'ct.spools.delegation
                  :spools ['ct.spools/delegation 'ct.spools/agent-run]
                  :after [:skein/spools-agent-run]
                  :required? true})
(runtime/module! runtime :harnesses
                 {:file "config/harnesses.clj"
                  :spools ['ct.spools/delegation 'ct.spools/agent-run]
                  :after [:skein/spools-agent-run :skein/spools-delegation]
                  :required? true})

(runtime/module! runtime :skein/spools-harness-core
                 {:ns 'ct.spools.harness-core
                  :spools ['ct.spools/harness-core]
                  :required? true})
(runtime/module! runtime :skein/spools-claude-harness
                 {:ns 'ct.spools.claude-harness
                  :spools ['ct.spools/claude-harness 'ct.spools/harness-core]
                  :after [:skein/spools-harness-core]
                  :required? true})
(runtime/module! runtime :skein/spools-codex-harness
                 {:ns 'ct.spools.codex-harness
                  :spools ['ct.spools/codex-harness 'ct.spools/harness-core]
                  :after [:skein/spools-harness-core]
                  :required? true})
(runtime/module! runtime :skein/spools-agent-cli
                 {:ns 'ct.spools.agent-cli
                  :spools ['ct.spools/agent-cli 'ct.spools/harness-core]
                  :after [:skein/spools-harness-core
                          :skein/spools-claude-harness
                          :skein/spools-codex-harness]
                  :required? true})
(runtime/module! runtime :harness-next
                 {:file "config/harness-next.clj"
                  :spools ['ct.spools/harness-core]
                  :after [:skein/spools-harness-core
                          :skein/spools-claude-harness
                          :skein/spools-codex-harness
                          :skein/spools-agent-cli]
                  :required? true})

(runtime/module! runtime :workflows
                 {:file "config/workflows.clj"
                  :spools ['skein.spools/workflow]
                  :after [:skein/spools-workflow]
                  :required? true})

(runtime/module! runtime :skein/spools-subagent
                 {:ns 'ct.spools.executors.subagent
                  :spools ['ct.spools/agent-run 'skein.spools/workflow]
                  :after [:skein/spools-agent-run :skein/spools-workflow
                          :harnesses :workflows]
                  :required? true})

(runtime/module! runtime :skein/spools-kanban
                 {:ns 'ct.spools.kanban
                  :spools ['codethread/kanban]
                  :required? true})
