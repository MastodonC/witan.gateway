(ns witan.gateway.components.query-router
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [witan.gateway.protocols    :as p :refer [RouteQuery]]
            [clj-http.client            :as client]
            [clojure.data.codec.base64  :as b64]))

(defn detect-namespace
  [p]
  (let [x (-> p first first)]
    (if (keyword? x)
      (keyword (namespace x))
      (when (and (coll? x)
                 (keyword? (first x)))
        (keyword (namespace (first x)))))))

(defrecord QueryRouter [service-map]
  RouteQuery
  (route-query [this payload]
    (log/debug payload )
    (let [nsp (detect-namespace payload)]
      (if (and nsp (contains? service-map nsp))
        (let [{:keys [host port]} (get service-map nsp)
              encoded-query (-> payload
                                (pr-str)
                                (.getBytes "utf-8")
                                (b64/encode)
                                (String.))
              url (str "http://" host ":" port "/query/" encoded-query)]
          (log/debug "Routing query to" url)
          (try
            (let [{:keys [status body headers] :as response} (client/get url)]
              (when-not (= status 200)
                (log/warn "Query returned an odd status code:" response))
              {:query/result (:body response)})
            (catch java.net.ConnectException _ (do
                                                 (log/error "Couldn't connect to" url)
                                                 {:query/error "Service not available" :query/original payload}))
            (catch Exception e (let [{:keys [status body]} (ex-data e)]
                                 (log/error "Response returned a bad status code" status body "||" url)
                                 {:query/error "Service returned an error status." :query/original (pr-str payload) :query/error-details body}))))
        {:query/error "Service not found." :query/original payload})))

  component/Lifecycle
  (start [component]
    (log/info "Starting Query Router")
    component)

  (stop [component]
    (log/info "Stopping Query Router")
    component))

(defn new-query-router [service-map]
  (->QueryRouter service-map))
