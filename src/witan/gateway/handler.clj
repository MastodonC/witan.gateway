(ns witan.gateway.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [base64-clj.core :as base64]
            [witan.gateway.protocols :as p]))

(defonce commands-seen (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def IdType
  s/Str)

(s/defschema CommandPost
  {:command s/Str
   (s/optional-key :params) s/Any
   (s/optional-key :version) s/Str})

(s/defschema CommandReceipt
  {:id s/Str})

(s/defschema CommandStatus
  {:command s/Keyword
   :id      IdType
   :version s/Str
   :status  s/Keyword})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-client-ip [req]
  (if-let [ips (get-in req [:headers "x-forwarded-for"])]
    (-> ips (clojure.string/split #",") first)
    (:remote-addr req)))

(defn receive-command!
  [{:keys [command version] :as payload} kafka req]
  (let [now (java.util.Date.)
        id (base64/encode (clojure.string/join "^" [now command version (java.util.UUID/randomUUID)]))
        updated-payload
        (-> payload
            (assoc :command (keyword command))
            (assoc :version (or version "tbd"))
            (assoc :id id)
            (assoc :origin (get-client-ip req))
            (assoc :handled-by (:server-name req))
            (assoc :received-at now))]
    (swap! commands-seen assoc id updated-payload)
    (p/send-message! kafka updated-payload)
    (created (select-keys updated-payload (keys CommandReceipt)))))

(defn fetch-command
  [xid]
  (if-let [command (get @commands-seen xid)]
    (let [{:keys [command version id]} command]
      (ok {:command command
           :id id
           :version version
           :status :unknown}))
    (not-found)))

(defn perform-query
  [query])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

            (GET "/command/:id" [id]
                 :path-params [id :- IdType]
                 :return CommandStatus
                 :summary "Recovers a command receipt and responds with status"
                 (fetch-command id))

            (POST "/command" req
                  :components [kafka]
                  :return CommandReceipt
                  :body [command CommandPost]
                  :summary "Receives a command"
                  (receive-command! command kafka req))

            (GET "/query" []
                 :summary "Performs the desired query"
                 (ok 123)))))
