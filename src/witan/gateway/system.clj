(ns witan.gateway.system
  (:gen-class)
  (:require [com.stuartsierra.component            :as component]
            [aero.core                             :refer [read-config]]
            [witan.gateway.logstash-appender       :as logstash]
            [taoensso.timbre                       :as timbre]
            ;;
            [witan.gateway.protocols               :refer [store-event!]]
            ;;
            [witan.gateway.components.kafka        :refer [new-kafka-producer
                                                           new-kafka-consumer]]
            [witan.gateway.components.server       :refer [new-http-server]]
            [witan.gateway.components.query-router :refer [new-query-router]]
            [witan.gateway.components.cassandra    :refer [new-cassandra-connection]]
            [witan.gateway.components.receipts     :refer [new-receipt-manager]]))

(defn new-system [profile]
  (let [config (read-config (clojure.java.io/resource "config.edn") {:profile profile})]

    ;; logging config
    (if (= profile :production)
      (timbre/merge-config! (assoc (:log config) :output-fn logstash/output-fn))
      (timbre/merge-config! (:log config)))

    (component/system-map
     :db (new-cassandra-connection (:cassandra config))
     :kafka (new-kafka-producer (-> config :kafka :zk))
     :receipts (component/using (new-receipt-manager) [:db])
     :queries (new-query-router (:queries config))
     :http-kit (component/using (new-http-server (:webserver config)) [:receipts :kafka :queries])
     :kafka-consumer-events   (component/using
                               (new-kafka-consumer (merge {:topic :event
                                                           :receiver #(store-event! %2 %1)} (-> config :kafka :zk)))
                               {:receiver-ctx :receipts})
     :kafka-consumer-errors   (component/using
                               (new-kafka-consumer (merge {:topic :error
                                                           :receiver #(store-event! %2 %1)} (-> config :kafka :zk)))
                               {:receiver-ctx :receipts}))))

(defn -main [& [arg]]
  (let [profile (or (keyword arg) :production)]
    (component/start
     (new-system profile))))
