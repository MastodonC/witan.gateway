(ns witan.gateway.logstash-appender
  (:require [taoensso.timbre :as log]
            [clojure.string :as str]
            [cheshire.core :as json]))

;; https://github.com/MastodonC/whiner-timbre/blob/master/src/whiner/handler.clj

(def logback-timestamp-opts
  "Controls (:timestamp_ data)"
  {:pattern  "yyyy-MM-dd HH:mm:ss,SSS"
   :locale   :jvm-default
   :timezone :utc})

(defn stacktrace-element->vec
  [^StackTraceElement ste]
  [(.getFileName ste) (.getLineNumber ste) (.getMethodName ste)])

(defn exception->map
  [^Throwable e]
  (merge
   {:type (str (type e))
    :trace (mapv stacktrace-element->vec (.getStackTrace e))}
   (when-let [m (.getMessage e)]
     {:message m})
   (when-let [c (.getCause e)]
     {:cause (exception->map c)})))

(defn not-empty-str
  [s]
  (when-not (clojure.string/blank? s) s))

(defn log->json
  [data app-name]
  (let [opts (get-in data [:config :options])
        exp (some-> (force (:?err data)) exception->map)
        msg (or (not-empty-str (force (:msg_ data))) (:message exp))]
    {:level (:level data)
     :namespace (:?ns-str data)
     :application app-name
     :file (:?file data)
     :line (:?line data)
     :exception exp
     :hostname (force (:hostname_ data))
     :message msg
     "@timestamp" (force (:timestamp_ data))}))

(defn json->out
  [app-name]
  (fn [data]
    (json/generate-stream
     (log->json data app-name)
     *out*)))
