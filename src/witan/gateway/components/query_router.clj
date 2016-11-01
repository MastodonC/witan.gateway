(ns witan.gateway.components.query-router
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [witan.gateway.protocols    :as p :refer [RouteQuery]]
            [clj-http.client            :as client]
            [graph-router.core          :refer [with dispatch]]
            ;;
            [witan.gateway.queries.data-acquisition :as qda]
            [witan.gateway.queries.workspace :as qw]))

(def test-query-fields
  [:foo/bar :hello/world])

(defn test-query
  [_]
  {:foo/bar "Hello"
   :hello/world "World"})

(defn make-graph
  [service-map]
  {;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; Workspaces
   ;; - workspaces by owner
   (with :workspace/list-by-owner qw/get-workspaces-by-owner)
   qw/workspace-fields
   ;; - workspace by id
   (with :workspace/by-id qw/get-workspace-by-id)
   qw/workspace-fields
   ;; - functions
   (with :workspace/available-functions qw/get-available-functions)
   qw/function-fields
   ;; - models
   (with :workspace/available-models qw/get-available-models)
   qw/model-fields
   (with :workspace/model-by-name-and-version qw/get-model-by-name-and-version)
   qw/model-fields

   ;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; Test
   (with :test-query test-query) test-query-fields})

(defn fix-list-entries
  [m]
  (apply hash-map (update-in (first m) [0] #(apply list %))))

(defrecord QueryRouter [service-map]
  RouteQuery
  (route-query [{:keys [graph]} payload]
    (log/info "Query:" payload)
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
