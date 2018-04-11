(ns witan.gateway.components.query-router
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [witan.gateway.protocols    :as p :refer [RouteQuery]]
            ;;
            [witan.gateway.queries.data-acquisition :as qda]
            [witan.gateway.queries.workspace :as qw]
            [witan.gateway.queries.datastore :as qds]
            [witan.gateway.queries.heimdall :as qh]
            [witan.gateway.queries.search :as search]))

(def functions
  {;;:workspace/list-by-owner qw/get-workspaces-by-owner
   ;;:workspace/by-id         qw/get-workspace-by-id

   ;;:workspace/available-models          qw/get-available-models
   ;;:workspace/model-by-name-and-version qw/get-model-by-name-and-version

   ;;:data-acquisition/requests-by-requester qda/requests-by-requester
   ;;:data-acquisition/request-by-id         qda/request-by-id

   :datastore/metadata-with-activities qds/metadata-with-activities
   :datastore/metadata-by-id qds/metadata-by-id

   :search/dashboard search/execute-search
   :search/datapack-files search/execute-search
   :search/datapack-files-expand search/execute-search
   :search/metadata-by-id search/metadata-by-id

   :groups/search qh/group-search
   :groups/by-ids qh/get-groups-info})

(defrecord QueryRouter [service-map]
  RouteQuery
  (route-query [{:keys [graph]} user [query [params fields]]]
    (log/info "Query:" query params user)
    (if-let [func (get functions query)]
      (try
        (let [result (apply (partial func user service-map) params)
              additional (when (:error result) {:original {:params params
                                                           :fields fields}})
              final (merge result additional)]
          (log/debug "Query succeeded")
          {query final})
        (catch clojure.lang.ArityException e
          (log/warn e "Query failed - arity exception")
          {query {:error "incorrect amount of arguments were supplied"}})
        (catch Exception e
          (log/error e "Query failed - unknown exception")
          {query {:error "an unknown exception occurred"}}))
      (do
        (log/debug "Query failed")
        {query {:error "does not exist"}})))

  component/Lifecycle
  (start [component]
    (log/info "Starting Query Router")
    component)

  (stop [component]
    (log/info "Stopping Query Router")
    component))

(defn new-query-router [service-map]
  (->QueryRouter service-map))
