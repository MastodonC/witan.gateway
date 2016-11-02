(ns witan.gateway.components.connection-manager
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [clj-time.core              :as t]
            [clojure.spec               :as s]
            [kixi.comms                 :as c]
            [witan.gateway.protocols    :as p :refer [ManageConnections]]
            [zookeeper                  :as zk]))

(defonce channels (atom #{}))
(defonce receipts (atom {}))

(defrecord ConnectionManager [host port]
  ManageConnections
  (process-event! [{:keys [receipts]} event]
    (log/info "Got event:" event)
    (when-let [id (:kixi.comms.command/id event)]
      (try
        (when-let [{:keys [cb]} (get @receipts id)]
          (if-let [error (s/explain-data :kixi.comms.message/event event)]
            (log/error "Event schema coercion failed: " (pr-str error) event)
            (do
              (cb event)
              (swap! receipts dissoc id)
              nil)))
        (catch Exception e
          (log/error "Error whilst processing an event:" event e)))))
  (add-connection! [{:keys [channels]} connection]
    (swap! channels conj connection)
    (log/info "Added connection. Total:" (count @channels)))
  (remove-connection! [{:keys [channels]} connection]
    (swap! channels #(remove #{connection} %))
    (log/info "Removed connection. Total:" (count @channels)))
  (add-receipt! [{:keys [receipts]} cb id]
    (swap! receipts assoc (str id) {:cb cb :at (t/now)}))

  component/Lifecycle
  (start [{:keys [comms] :as component}]
    (log/info "Starting Connection Manager")
    (let [zk (zk/connect (str host ":" port))
          seq-name (zk/create-all zk "/kixi/gateway/connections-manager/consumer-" :sequential? true)
          consumer-name (clojure.string/replace seq-name #"/" "-")
          c (assoc component
                   :channels  (atom #{})
                   :receipts  (atom {}))]
      (zk/close zk)
      (c/attach-event-with-key-handler!
       (assoc-in comms [:consumer-config :auto.offset.reset] :latest)
       consumer-name
       :kixi.comms.command/id
       (partial p/process-event! c))
      c))

  (stop [component]
    (log/info "Stopping Connection Manager")
    (dissoc component
            :channels
            :receipts)))

(defn new-connection-manager [args]
  (map->ConnectionManager args))
