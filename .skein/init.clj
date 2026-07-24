(require '[skein.api.current.alpha :as current]
         '[skein.api.runtime.alpha :as runtime])

(def runtime (current/runtime))

;; batteries is approved as a shipped source-root spool (spools.edn resolves it
;; relative to the mill-selected skein checkout). The module guard keeps source
;; loading behind that visible approval; contribute publishes its CLI ops and
;; reconcile seeds the glossary outcomes they reference.
(runtime/module! runtime :skein/spools-batteries
                 {:ns 'skein.spools.batteries
                  :spools ['skein.spools/batteries]
                  :contribute 'skein.spools.batteries/contribute
                  :reconcile 'skein.spools.batteries/reconcile})

;; kanban board for this repo's own coordination cards
(runtime/module! runtime :skein/spools-kanban
                 {:ns 'ct.spools.kanban
                  :spools ['codethread/kanban]
                  :contribute 'ct.spools.kanban/contribute
                  :reconcile 'ct.spools.kanban/reconcile
                  :required? true})
