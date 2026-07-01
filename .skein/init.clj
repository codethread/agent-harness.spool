(require '[skein.runtime.alpha :as runtime-alpha])

(runtime-alpha/sync!)
(runtime-alpha/use! :skein/libs-ephemeral
  {:ns 'skein.libs.ephemeral
   :call 'skein.libs.ephemeral/install!})
(runtime-alpha/use! :config
  {:file "config.clj"
   :after [:skein/libs-ephemeral]
   :call 'config/install!})
