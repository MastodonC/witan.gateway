(ns witan.gateway.components.cassandra
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [qbits.alia                 :as alia]
            [qbits.hayt                 :as hayt]
            [witan.gateway.protocols :refer [Database]]))

(defn create-keyspace!
  [host keyspace replication-factor]
  (alia/execute
   (alia/connect (alia/cluster {:contact-points [host]}))
   (hayt/create-keyspace keyspace
                         (hayt/if-exists false)
                         (hayt/with {:replication
                                     {:class "SimpleStrategy"
                                      :replication_factor replication-factor}}))))

(defn create-connection
  [host keyspace]
  (alia/connect (alia/cluster {:contact-points [host]}) keyspace))

(defn exec
  [this x]
  (if-let [conn (get this :connection)]
    (try
      (log/debug "Executing" (hayt/->raw x))
      (alia/execute conn x)
      (catch Exception e (log/error "Failed to execute database command:" (str e))))
    (log/error "Unable to execute Cassandra comment - no connection")))

(defrecord Cassandra [host keyspace replication-factor]
  Database
  (drop-table! [this table]
    (exec this (hayt/drop-table table (hayt/if-exists))))
  (create-table! [this table columns]
    (exec this (hayt/create-table table (hayt/column-definitions columns))))
  (insert! [this table row {:keys [using]}]
    (cond
      using (exec this (hayt/insert table (hayt/values row) (apply hayt/using using)))
      :else (exec this (hayt/insert table (hayt/values row)))))
  (select [this table where]
    (exec this (hayt/select table (hayt/where where))))

  component/Lifecycle
  (start [component]
    (log/info "Bootstrapping Cassandra...")
    (create-keyspace! host keyspace replication-factor)
    (log/info "Connecting to Cassandra..." host keyspace)
    (let [conn (create-connection host keyspace)]
      (assoc component :connection conn)))

  (stop [component]
    (log/info "Disconnecting from Cassandra...")
    (dissoc component :connection)))

(defn new-cassandra-connection [args]
  (map->Cassandra args))
