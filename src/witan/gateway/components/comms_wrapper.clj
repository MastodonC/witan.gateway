(ns witan.gateway.components.comms-wrapper
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [zookeeper                  :as zk]
            [amazonica.aws.dynamodbv2 :as ddb]
            ;;
            [kixi.comms :as comms]
            [kixi.comms.components.kinesis :as kinesis]))

(defn- fix-app-name
  [{:keys [host port]}]
  (fn [app]
    (let [zk-conn (zk/connect (str host ":" port))
          seq-name (zk/create-all zk-conn "/kixi/gateway/app-name-" :sequential? true)
          consumer-name (clojure.string/replace (subs seq-name 1) #"/" "-")
          number (second (re-find #".+-([0-9]{10})" consumer-name))]
      (zk/close zk-conn)
      (str app "-" number))))

(defn- table-exists?
  [endpoint table]
  (try
    (ddb/describe-table {:endpoint endpoint} table)
    (catch Exception e false)))

(defn- clear-tables
  [endpoint table-names]
  (doseq [sub-table-names (partition-all 10 table-names)]
    (doseq [table-name sub-table-names]
      (ddb/delete-table {:endpoint endpoint} :table-name table-name))
    (loop [tables sub-table-names]
      (when (not-empty tables)
        (recur (doall (filter (partial table-exists? endpoint) tables)))))))

(defn- tear-down!
  [kinesis]
  (log/info "Deleting dynamo tables ...")
  (clear-tables (:dynamodb-endpoint kinesis)
                [(kinesis/event-worker-app-name (:app kinesis) (:profile kinesis))
                 (kinesis/command-worker-app-name (:app kinesis) (:profile kinesis))]))

(defrecord CommsWrapper [config
                         inner-comms]
  comms/Communications
  (send-event! [this event version payload]
    (comms/send-event! inner-comms event version payload))
  (send-event! [this event version payload opts]
    (comms/send-event! inner-comms event version payload opts))
  (-send-event! [this event opts]
    (comms/-send-event! inner-comms event opts))
  (send-command! [this command version user payload]
    (comms/send-command! inner-comms command version user payload))
  (send-command! [this command version user payload opts]
    (comms/send-command! inner-comms command version user payload opts))
  (-send-command! [this command opts]
    (comms/-send-command! inner-comms command opts))
  (attach-event-handler! [this group-id event version handler]
    (comms/attach-event-handler! inner-comms group-id event version handler))
  (attach-event-with-key-handler! [this group-id map-key handler]
    (comms/attach-event-with-key-handler! inner-comms group-id map-key handler))
  (attach-validating-event-handler! [this group-id event version handler]
    (comms/attach-validating-event-handler! inner-comms group-id event version handler))
  (attach-command-handler! [this group-id event version handler]
    (comms/attach-command-handler! inner-comms group-id event version handler))
  (attach-validating-command-handler! [this group-id command version handler]
    (comms/attach-validating-command-handler! inner-comms group-id command version handler))
  (detach-handler! [this handler]
    (comms/detach-handler! inner-comms handler))
  component/Lifecycle
  (start [component]
    (let [comms (kinesis/map->Kinesis config)]
      (assoc component
             :profile (:profile config)
             :app (:app config)
             :inner-comms (component/start comms))))
  (stop [{:keys [inner-comms] :as component}]
    (component/stop inner-comms)
    (tear-down! inner-comms)
    (dissoc component
            :profile
            :app
            :inner-comms)))

(defn new-comms-wrapper [config zk-config]
  (map->CommsWrapper {:config (update config :app (fix-app-name zk-config))}))
