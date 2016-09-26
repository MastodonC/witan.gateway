(ns witan.gateway.system
  (:gen-class)
  (:require [com.stuartsierra.component            :as component]
            [aero.core                             :refer [read-config]]
            [witan.gateway.logstash-appender       :as logstash]
            [taoensso.timbre                       :as timbre]
            ;;
            [witan.gateway.protocols               :refer [process-event!]]
            ;;
            [witan.gateway.components.kafka        :refer [new-kafka-producer
                                                           new-kafka-consumer]]
            [witan.gateway.components.server       :refer [new-http-server]]
            [witan.gateway.components.query-router :refer [new-query-router]]
            [witan.gateway.components.connection-manager :refer [new-connection-manager]]))

(defn new-system [profile]
  (let [config (read-config (clojure.java.io/resource "config.edn") {:profile profile})]

    ;; logging config
    (if (= profile :production)
      (timbre/merge-config! (assoc (:log config) :output-fn logstash/output-fn))
      (timbre/merge-config! (:log config)))

    (component/system-map
     :kafka (new-kafka-producer (-> config :kafka :zk))
     :connections (new-connection-manager)
     :queries (new-query-router (:queries config))
     :http-kit (component/using
                (new-http-server (:webserver config))
                [:connections :kafka :queries])
     :kafka-consumer-events   (component/using
                               (new-kafka-consumer (merge {:topic :event
                                                           :receiver #(process-event! %2 %1)} (-> config :kafka :zk)))
                               {:receiver-ctx :connections}))))

(defn -main [& [arg]]
  (let [profile (or (keyword arg) :production)]
    (component/start
     (new-system profile))))
