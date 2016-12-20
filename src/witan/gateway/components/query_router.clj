(ns witan.gateway.components.query-router
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [witan.gateway.protocols    :as p :refer [RouteQuery]]
            [clj-http.client            :as client]
            [graph-router.core          :refer [with dispatch]]
            ;;
            [witan.gateway.queries.data-acquisition :as qda]
            [witan.gateway.queries.workspace :as qw]
            [witan.gateway.queries.datastore :as qds]))

(def function-graph
  {:workspace/list-by-owner [qw/get-workspaces-by-owner qw/workspace-fields]
   :workspace/by-id         [qw/get-workspace-by-id     qw/workspace-fields]

   :workspace/available-models          [qw/get-available-models          qw/model-fields]
   :workspace/model-by-name-and-version [qw/get-model-by-name-and-version qw/model-fields]

   :data-acquisition/requests-by-requester [qda/requests-by-requester qda/request-fields]
   :data-acquisition/request-by-id         [qda/request-by-id         qda/request-fields]

   :datastore/files-by-author [qds/files-by-author qds/data-fields]})

(defn make-graph
  [service-map]
  (reduce-kv (fn [a k [fnc fields]]
               (assoc a (with k (partial fnc service-map)) fields)) {} function-graph))

(defn fix-list-entries
  [m]
  (apply hash-map (update-in (first m) [0] #(apply list %))))

(defrecord QueryRouter [service-map]
  RouteQuery
  (route-query [{:keys [graph]} payload]
    (log/info "Query:" payload service-map)
    (if (vector? (-> payload first first))
      (dispatch graph (fix-list-entries payload))
      (dispatch graph payload)))

  component/Lifecycle
  (start [component]
    (log/info "Starting Query Router")
    (assoc component :graph (make-graph service-map)))

  (stop [component]
    (log/info "Stopping Query Router")
    (dissoc component :graph)))

(defn new-query-router [service-map]
  (->QueryRouter service-map))
