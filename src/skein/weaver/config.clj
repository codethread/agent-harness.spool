(ns skein.weaver.config
  "Resolve Skein weaver world directories from an explicit selected config dir."
  (:require [clojure.java.io :as io]))

(defn- canonical-path [file]
  (.getCanonicalPath (io/file file)))

(defn- world-map [config-dir state-dir data-dir]
  {:config-dir config-dir
   :state-dir state-dir
   :data-dir data-dir
   :config-file (str config-dir "/config.json")
   :db-path (str data-dir "/skein.sqlite")})

(defn world
  "Return the config, state, and data paths for an explicit weaver world.

  `config-dir` is the selected Skein world directory supplied by the CLI or by
  tests/helpers that intentionally construct disposable worlds. State and data
  directories are derived below the canonical config directory. Calling without a
  config dir fails loudly so Clojure helpers do not silently target XDG global
  state when ordinary CLI use is repo-first."
  ([]
   (throw (ex-info "No Skein config dir selected; pass an explicit config-dir from the CLI, repo discovery, or a disposable test world"
                   {:code :skein.config/no-selected-world})))
  ([config-dir]
   (when-not (seq (str config-dir))
     (throw (ex-info "No Skein config dir selected; pass an explicit config-dir from the CLI, repo discovery, or a disposable test world"
                     {:code :skein.config/no-selected-world})))
   (let [dir (canonical-path config-dir)]
     (world-map dir (str dir "/state") (str dir "/data")))))
