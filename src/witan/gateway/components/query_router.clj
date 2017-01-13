(ns witan.gateway.components.query-router
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [witan.gateway.protocols    :as p :refer [RouteQuery]]
            [clj-http.client            :as client]
            ;;
            [witan.gateway.queries.data-acquisition :as qda]
            [witan.gateway.queries.workspace :as qw]
            [witan.gateway.queries.datastore :as qds]))

(def functions
  {:workspace/list-by-owner qw/get-workspaces-by-owner
   :workspace/by-id         qw/get-workspace-by-id

   :workspace/available-models          qw/get-available-models
   :workspace/model-by-name-and-version qw/get-model-by-name-and-version

   :data-acquisition/requests-by-requester qda/requests-by-requester
   :data-acquisition/request-by-id         qda/request-by-id

   :datastore/metadata-with-activities qds/metadata-with-activities})

(defn blob->function
  [functions fb & params]
  (let [blob (if (vector? fb)
               fb
               (vector fb))
        f (first blob)]
    (fn []
      {f ((apply partial (get functions f) (concat params (rest blob))))})))

(defrecord QueryRouter [service-map]
  RouteQuery
  (route-query [{:keys [graph]} user payload]
    (log/info "Query:" payload)
    (let [function-blob (first payload)
          function (blob->function functions function-blob user service-map)]
      (function)))

  component/Lifecycle
  (start [component]
    (log/info "Starting Query Router")
    component)

  (stop [component]
    (log/info "Stopping Query Router")
    component))

(defn new-query-router [service-map]
  (->QueryRouter service-map))
