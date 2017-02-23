(ns witan.gateway.system
  (:gen-class)
  (:require [com.stuartsierra.component :as component]
            [aero.core :refer [read-config]]
            [kixi.log :as kixi-log]
            [taoensso.timbre :as timbre]
            [kixi.comms.components.kafka :as kafka]
            [witan.gateway.protocols :refer [process-event!]]
            [witan.gateway.components.server :refer [new-http-server]]
            [witan.gateway.components.query-router :refer [new-query-router]]
            [witan.gateway.components.connection-manager :refer [new-connection-manager]]
            [witan.gateway.components.auth :refer [new-authenticator]]
            [witan.gateway.components.downloads :refer [new-download-manager]]
            [witan.gateway.components.events :refer [new-event-aggregator]]))

(defn new-system [profile]
  (let [config (read-config (clojure.java.io/resource "config.edn") {:profile profile})
        log-config (assoc (:log config)
                          :timestamp-opts kixi-log/default-timestamp-opts)]

    ;; logging config
    (timbre/merge-config!
     (if (= profile :production)
       (assoc log-config
              :appenders {:direct-json (kixi-log/timbre-appender-logstash "witan.gateway")})
       log-config))

    (component/system-map
     :auth        (new-authenticator (-> config :auth))
     :comms       (kafka/map->Kafka (-> config :comms :kafka))
     :downloads   (new-download-manager (-> config :downloads) (:directory config))
     :events      (component/using
                   (new-event-aggregator (-> config :events))
                   [:comms])
     :connections (component/using
                   (new-connection-manager (-> config :connections))
                   [:comms :events])
     :queries     (new-query-router (:directory config))
     :http-kit    (component/using
                   (new-http-server (:webserver config) (:directory config))
                   [:connections :comms :queries :auth :downloads]))))

(defn -main [& [arg]]
  (let [profile (or (keyword arg) :production)]

    ;; https://stuartsierra.com/2015/05/27/clojure-uncaught-exceptions
    (Thread/setDefaultUncaughtExceptionHandler
     (reify Thread$UncaughtExceptionHandler
       (uncaughtException [_ thread ex]
         (timbre/error "Unhandled exception:" ex))))

    (component/start
     (new-system profile))))
