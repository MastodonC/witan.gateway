(ns witan.gateway.components.server
  (:gen-class)
  (:require [org.httpkit.server           :as httpkit]
            [ring.middleware.cookies      :refer [wrap-cookies]]
            [ring.middleware.params       :refer [wrap-params]]
            [com.stuartsierra.component   :as component]
            [witan.gateway.handler        :refer [app]]
            [taoensso.timbre              :as log]
            [clojure.string :as str]
            [metrics.timers :refer [time! timer]]
            [kixi.metrics.name-safety :refer [safe-name]]
            [ring.middleware.cors         :refer [wrap-cors]])
  (:import [java.util.concurrent TimeUnit]))

(defn wrap-log [handler]
  (fn [request]
    (log/info "REQUEST:" request)
    (handler request)))

(defn wrap-catch-exceptions [handler]
  (fn [request]
    (try (handler request)
         (catch Throwable t (log/error t (str "An exception was thrown whilst processing a request:" (pr-str request)))))))

(defn wrap-components
  "Assoc given components to the request."
  [handler components]
  (fn [req]
    (handler (assoc req :components components))))

(defn wrap-directory
  "Assoc directory to the request."
  [handler directory]
  (fn [req]
    (handler (assoc req :directory directory))))

(defn metric-name
  [request response]
  (let [name (-> (str/upper-case (name (:request-method request)))
                 (str "." (:uri request))
                 safe-name
                 (str "." (:status response)))]
    ["info" "resources" name]))

(defn wrap-per-resource-metrics
  "A middleware function to add metrics for all routes in the given
  handler. The simpler form adds default aggregators that replace GUIDs,
  Mongo IDs and Numbers with the constants GUID, MONGOID and NUMBER so
  that metric paths are sensible limited. Use the second form to specify
  your own replacements."
  [handler metrics]
  (fn [request]
    (let [start (System/currentTimeMillis)
          response (handler request)
          duration (- (System/currentTimeMillis) start)
          timer (timer (:registry metrics) (metric-name request response))]
      (.update timer duration TimeUnit/MILLISECONDS)
      response)))

(defrecord HttpKit [port directory]
  component/Lifecycle
  (start [this]
    (log/info (str "Server started at http://localhost:" port))
    (assoc this :http-kit (httpkit/run-server
                           (-> #'app
                               (wrap-catch-exceptions)
                               (wrap-cookies)
                               (wrap-params)
                               (wrap-directory directory)
                               (wrap-components this)
                               ;;(wrap-log)
                               (wrap-per-resource-metrics (:metrics this))
                               (wrap-cors :access-control-allow-origin [#".*"]
                                          :access-control-allow-methods [:get :post]))
                           {:port port})))
  (stop [this]
    (log/info "Stopping server")
    (if-let [http-kit (:http-kit this)]
      (http-kit))
    (dissoc this :http-kit)))

(defn new-http-server
  [args directory]
  (->HttpKit (:port args) directory))
