(ns witan.gateway.components.connection-manager
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [clj-time.core              :as t]
            [schema.coerce              :as coerce]
            [witan.gateway.protocols    :as p :refer [ManageConnections]]
            [witan.gateway.schema       :as wgs]))

(defonce channels (atom #{}))
(defonce receipts (atom {}))

(defrecord ConnectionManager []
  ManageConnections
  (process-event! [this event]
    (when-let [{:keys [cb]} (get @receipts (:command/receipt event))]
      (let [result ((coerce/coercer (get wgs/Event "1.0") coerce/json-coercion-matcher) event)]
        (if (contains? result :error)
          (log/error "Event schema coercion failed: " (pr-str (:error result)) event)
          (cb result)))))
  (add-connection! [this connection]
    (swap! channels conj connection)
    (log/info "Added connection. Total:" (count @channels)))
  (remove-connection! [this connection]
    (swap! channels #(remove #{connection} %))
    (log/info "Removed connection. Total:" (count @channels)))
  (add-receipt! [this cb id]
    (swap! receipts assoc (str id) {:cb cb :at (t/now)}))


  component/Lifecycle
  (start [component]
    (log/info "Starting Connection Manager")
    component)

  (stop [component]
    (log/info "Stopping Connection Manager")
    component))

(defn new-connection-manager []
  (->ConnectionManager))
