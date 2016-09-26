(ns witan.gateway.components.server
  (:gen-class)
  (:require [org.httpkit.server           :as httpkit]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [com.stuartsierra.component   :as component]
            [witan.gateway.handler        :refer [app]]
            [taoensso.timbre              :as log]
            [ring.middleware.cors         :refer [wrap-cors]]))

(defn wrap-log [handler]
  (fn [request]
    (log/info "REQUEST:" request)
    (handler request)))

(defn wrap-catch-exceptions [handler]
  (fn [request]
    (try (handler request)
         (catch Throwable t (log/error t)))))

(defn wrap-components
  "Assoc given components to the request."
  [handler components]
  (fn [req]
    (handler (assoc req ::components components))))

(defrecord HttpKit [port]
  component/Lifecycle
  (start [this]
    (log/info (str "Server started at http://localhost:" port))
    (assoc this :http-kit (httpkit/run-server
                           (-> #'app
                               (wrap-catch-exceptions)
                               (wrap-components this)
                               (wrap-log)
                               (wrap-content-type "application/json")
                               (wrap-cors :access-control-allow-origin [#".*"]
                                          :access-control-allow-methods [:get :post]))
                           {:port port})))
  (stop [this]
    (log/info "Stopping server")
    (if-let [http-kit (:http-kit this)]
      (http-kit))
    (dissoc this :http-kit)))

(defn new-http-server
  [args]
  (map->HttpKit args))
