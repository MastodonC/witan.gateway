(ns witan.gateway.handler
  (:require [compojure.api.sweet :refer :all]
            [compojure.route :as route]
            [ring.util.http-response :refer :all]
            [taoensso.timbre :as log]
            [schema.core :as s]
            [witan.gateway.command :as command]
            [witan.gateway.components.receipts :as receipts]
            [witan.gateway.protocols :as p]))


(defn get-client-ip [req]
  (if-let [ips (get-in req [:headers "x-forwarded-for"])]
    (-> ips (clojure.string/split #",") first)
    (:remote-addr req)))

(def app
  (api
   {:swagger
    {:ui "/"
     :spec "/swagger.json"
     :data {:info {:title "Witan Gateway service"
                   :description "CQRS gateway application for the Witan infrastructure"}
            :tags [{:name "api", :description "some apis"}]}}}

   (context "/api" []
            :tags ["api"]

            (GET "/status/:id" [id]
                 :components [receipts]
                 :path-params [id :- s/Uuid]
                 :return receipts/Status
                 :summary "Recovers a command receipt and responds with status"
                 (p/check-receipt receipts id))

            (POST "/command" req
                  :components [kafka]
                  :return command/Receipt
                  :body [command command/POST]
                  :summary "Receives a command"
                  (command/receive-command! command kafka req (get-client-ip req)))

            (GET "/query/*" req
                 :components [queries]
                 :summary "Performs the desired query"
                 (let [p (:params req)
                       route (keyword (:* p))
                       params (dissoc p :*)]
                   (p/route-query queries route params))))))
