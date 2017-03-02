(ns witan.gateway.components.metrics
  (:require [clojure.string :as str]
            [com.stuartsierra.component :as component]
            [kixi.metrics.reporters.json-console :as reporter]
            [kixi.metrics.name-safety :refer [safe-name]]
            [metrics
             [core :refer [new-registry]]
             [histograms :refer [histogram update!]]
             [meters :refer [mark! meter]]]
            [metrics.jvm.core :as jvm]
            [metrics.ring.expose :as expose]
            [taoensso.timbre :as log]))

(defn meter-mark!
  [registry]
  (fn [meter-name]
    (let [met (meter registry (mapv safe-name meter-name))]
      (mark! met))))

(defn log-metrics
  [meter-mark!]
  (fn [data]
    (meter-mark! ["log" (name (:level data)) nil])
    data))

(defn timbre-metrics
  [reg]
  (log/merge-config! {:middleware [(log-metrics (meter-mark! reg))]}))

(defrecord Metrics
    [json-reporter registry]
    component/Lifecycle
    (start [component]
      (let [with-reg (update component :registry #(or %
                                                      (let [reg (new-registry)]
                                                        (jvm/instrument-jvm reg)
                                                        (timbre-metrics reg)
                                                        reg)))
            reg (:registry with-reg)]
        (-> with-reg
            (update :json-reporter-inst #(or %
                                             (let [reporter (reporter/reporter reg
                                                                               json-reporter)]
                                               (log/info "Starting JSON Metrics Reporter")
                                               (reporter/start reporter (:seconds json-reporter))
                                               reporter)))
            (update :meter-mark #(or % (meter-mark! reg))))))
    (stop [component]
      (-> component
          (update :json-reporter-inst #(when %
                                         (log/info "Stopping JSON Reporting")
                                         (reporter/stop %)
                                         nil))
          (dissoc :meter-mark)
          (update :registry #(when %
                               (log/info "Destroying metrics registry")
                               (.removeMatching % (com.codahale.metrics.MetricFilter/ALL))
                               nil)))))
