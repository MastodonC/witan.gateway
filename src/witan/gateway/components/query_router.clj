(ns witan.gateway.components.query-router
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [ring.util.http-response    :refer [ok not-found failed-dependency]]
            [witan.gateway.protocols    :as p :refer [RouteQuery]]
            [clj-http.client            :as client]))



(defrecord QueryRouter [service-map]
  RouteQuery
  (route-query [this route params]
    (let [[nsp query] ((juxt (comp keyword namespace) name) route)]
      (if (contains? service-map nsp)
        (let [{:keys [host port]} (get service-map nsp)
              url (str "http://" host ":" port "/" query)]
          (log/debug "Routing query to" url params)
          (try
            (let [{:keys [status body headers] :as response} (client/get url {:query-params params})]
              (when-not (= status 200)
                (log/warn "Query returned an odd status code:" response))
              response)
            (catch java.net.ConnectException _ (do
                                                 (log/error "Couldn't connect to" url)
                                                 (failed-dependency)))
            (catch Exception e (let [{:keys [status body]} (ex-data e)]
                                 (log/error "Response returned a bad status code" status body "||" url params)
                                 {:status status}))))
        (not-found))))

  component/Lifecycle
  (start [component]
    (log/info "Starting Query Router")
    component)

  (stop [component]
    (log/info "Stopping Query Router")
    component))

(defn new-query-router [service-map]
  (->QueryRouter service-map))
