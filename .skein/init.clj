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

;; kanban board for this repo's own coordination cards. The reviewed v10
;; candidate exports its entry points from `ct.spools.kanban/spool`; release
;; agent-harness v14 only after kanban v10 is tagged and its pin is publishable.
(runtime/module! runtime :skein/spools-kanban
                 {:ns 'ct.spools.kanban
                  :spools ['codethread/kanban]
                  :required? true})
