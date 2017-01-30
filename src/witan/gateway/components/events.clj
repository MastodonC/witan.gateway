(ns witan.gateway.components.events
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [clj-time.core              :as t]
            [clojure.spec               :as s]
            [kixi.comms                 :as c]
            [witan.gateway.protocols    :as p :refer [AggregateEvents]]
            [zookeeper                  :as zk]))

(defn handle-events
  [receivers event]
  (log/info "Observed event" (:kixi.comms.event/id event) (:kixi.comms.event/key event))
  (when (and receivers (not-empty @receivers))
    (log/debug "Forwarding to" (count @receivers) "receiver(s)...")
    (run! (fn [x] (x event)) @receivers))
  nil)

(defrecord EventAggregator [host port]
  AggregateEvents
  (register-event-receiver! [{:keys [receivers]} handler-fn]
    (swap! receivers conj handler-fn))
  (unregister-event-receiver! [{:keys [receivers]} handler-fn]
    (swap! receivers disj handler-fn))
  component/Lifecycle
  (start [{:keys [comms] :as component}]
    (log/info "Starting Event Aggregator")
    (let [zk (zk/connect (str host ":" port))
          seq-name (zk/create-all zk "/kixi/gateway/events/consumer-" :sequential? true)
          consumer-name (clojure.string/replace seq-name #"/" "-")
          receivers (atom #{})
          eh (c/attach-event-with-key-handler!
              (assoc-in comms [:consumer-config :auto.offset.reset] :latest)
              consumer-name
              :kixi.comms.command/id
              (partial handle-events receivers))]
      (zk/close zk)
      (assoc component
             :event-handler eh
             :receivers receivers)))

  (stop [component]
    (log/info "Stopping Event Aggregator")
    (dissoc component
            :event-handler
            :receivers)))

(defn new-event-aggregator [args]
  (map->EventAggregator args))
