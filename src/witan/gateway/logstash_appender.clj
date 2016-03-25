(ns witan.gateway.logstash-appender
  (:require [taoensso.timbre :as timbre]
            [cheshire.core :as cheshire]))

;; https://github.com/ptaoussanis/timbre/blob/master/src/taoensso/timbre/appenders/3rd_party/logstash.clj

(def iso-format "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

(defn data->json-stream
  [data writer opts]
  ;; Note: this it meant to target the logstash-filter-json; especially "message" and "@timestamp" get a special meaning there.
  (let [stacktrace-str (if-let [pr (:pr-stacktrace opts)]
                         #(with-out-str (pr %))
                         timbre/stacktrace)]
    (cheshire/generate-stream
     (merge (:context data)
            {:level (:level data)
             :namespace (:?ns-str data)
             :file (:?file data)
             :line (:?line data)
             :stacktrace (some-> (force (:?err_ data)) (stacktrace-str))
             :hostname (force (:hostname_ data))
             :message (force (:msg_ data))
             "@timestamp" (:instant data)})
     writer
     (merge {:date-format iso-format
             :pretty false}
            opts))))

(defn output-fn
  [data]
  (let [out (java.io.StringWriter.)]
    (data->json-stream data out nil)
    (str out)))
