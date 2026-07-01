(require '[skein.runtime.alpha :as runtime-alpha])

(runtime-alpha/sync!)
(runtime-alpha/use! :skein/spools-ephemeral
  {:ns 'skein.spools.ephemeral
   :call 'skein.spools.ephemeral/install!})
(runtime-alpha/use! :config
  {:file "config.clj"
   :after [:skein/spools-ephemeral]
   :call 'config/install!})
