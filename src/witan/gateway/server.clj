(ns witan.gateway.server
  (:gen-class)
  (:require [org.httpkit.server         :as httpkit]
            [compojure.api.middleware   :refer [wrap-components]]
            [com.stuartsierra.component :as component]
            [witan.gateway.handler      :refer [app]]
            [witan.gateway.kafka        :as kafka]
            [taoensso.timbre            :as log]))

(defrecord HttpKit []
  component/Lifecycle
  (start [this]
    (log/info "Server started at http://localhost:3000")
    (assoc this :http-kit (httpkit/run-server
                           (wrap-components
                            #'app
                            (select-keys this [:kafka]))
                           {:port 3000})))
  (stop [this]
    (log/info "Stopping server")
    (if-let [http-kit (:http-kit this)]
      (http-kit))
    (dissoc this :http-kit)))

(defn new-system []
  (component/system-map
   :kafka (kafka/new-kafka-producer :command)
   :http-kit (component/using (->HttpKit) [:kafka])))

(defn -main []
  (component/start (new-system)))
