(ns quality.source-forms
  "Read authored Clojure forms while preserving source metadata."
  (:require [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as reader-types]))

(defn read-all
  "Return every form read from `file`, preserving line metadata."
  [file]
  (with-open [r (reader-types/indexing-push-back-reader (slurp file))]
    (binding [reader/*alias-map* (fn [alias]
                                  (symbol (str "quality.source-forms." alias)))
              *read-eval* false]
      (loop [forms []]
        (let [form (reader/read {:eof ::eof :read-cond :allow} r)]
          (if (= ::eof form)
            forms
            (recur (conj forms form))))))))
