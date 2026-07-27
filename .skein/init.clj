(require '[skein.api.current.alpha :as current]
         '[skein.api.runtime.alpha :as runtime])

(def runtime (current/runtime))

;; batteries is approved as a shipped source-root spool (spools.edn resolves it
;; relative to the mill-selected skein checkout). The module guard keeps source
;; loading behind that visible approval; its entry points come from the
;; `skein.spools.batteries/spool` var (ADR-004), so this declaration names only
;; the source target and world policy. The selected Skein checkout must contain
;; or descend from 343f886880092bc38ed3e0522eca2d95a7cf04bc.
(runtime/module! runtime :skein/spools-batteries
                 {:ns 'skein.spools.batteries
                  :spools ['skein.spools/batteries]})

;; This checkout consumes its own agent-run and delegation roots so its
;; coordination world can use the same harness seats as skein-src.
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
                 {:file "harnesses.clj"
                  :spools ['ct.spools/delegation 'ct.spools/agent-run]
                  :after [:skein/spools-agent-run :skein/spools-delegation]
                  :required? true})

;; kanban board for this repo's own coordination cards. The reviewed v10
;; candidate exports its entry points from `ct.spools.kanban/spool`; release
;; agent-harness v14 only after kanban v10 is tagged and its pin is publishable.
(runtime/module! runtime :skein/spools-kanban
                 {:ns 'ct.spools.kanban
                  :spools ['codethread/kanban]
                  :required? true})
