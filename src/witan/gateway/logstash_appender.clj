(ns witan.gateway.logstash-appender
  (:require [taoensso.timbre :as timbre]
            [clojure.string :as str]
            [cheshire.core :as cheshire]))

;; https://github.com/MastodonC/whiner-timbre/blob/master/src/whiner/handler.clj

(def logback-timestamp-opts
  "Controls (:timestamp_ data)"
  {:pattern  "yyyy-MM-dd HH:mm:ss,SSS"
   :locale   :jvm-default
   :timezone :utc})


(defn output-fn
  "Default (fn [data]) -> string output fn.
  Use`(partial default-output-fn <opts-map>)` to modify default opts."
  ([     data] (output-fn nil data))
  ([opts data] ; For partials
   (let [{:keys [no-stacktrace? stack-fonts]} opts
         {:keys [level ?err #_vargs msg_ ?ns-str hostname_
                 timestamp_ ?line]} data]
     (str
      (force timestamp_)  " "
      (str/upper-case (name level))  " "
      "[" (or ?ns-str "?") ":" (or ?line "?") "] - "
      (force msg_)
      (when-not no-stacktrace?
        (when-let [err ?err]
          (str "\n" (log/stacktrace err opts))))))))
