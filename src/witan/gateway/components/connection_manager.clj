(ns witan.gateway.components.connection-manager
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [clj-time.core              :as t]
            [clojure.spec               :as s]
            [witan.gateway.protocols    :as p :refer [ManageConnections]]
            [cheshire.core              :refer [parse-string]]))

(defonce channels (atom #{}))
(defonce receipts (atom {}))

(defrecord ConnectionManager []
  ManageConnections
  (process-event! [this event]
    (try
      (when-let [{:keys [cb]} (get @receipts (:command/receipt event))]
        (let [result (parse-string event)]
          (if-let [error (s/explain-data :kixi.comms.message/event result)]
            (log/error "Event schema coercion failed: " (pr-str (:error result)) event)
            (cb result))))
      (catch Exception e
        (log/error "Error whilst processing an event:" event e))))
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
