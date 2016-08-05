(ns witan.gateway.handler
  (:require [compojure.core :refer :all]
            [taoensso.timbre :as log]
            [schema.core :as s]
            [cheshire.core :as json]
            [witan.gateway.command :as command]
            [witan.gateway.protocols :as p]
            [witan.gateway.schema :as rs]
            [clojure.core.async :as async :refer [chan go go-loop put! <!]]
            [org.httpkit.server :refer [send! with-channel on-close on-receive]]
            [clj-time.core :as t]
            [clj-time.format :as tf]))


(defn get-client-ip [req]
  (if-let [ips (get-in req [:headers "x-forwarded-for"])]
    (-> ips (clojure.string/split #",") first)
    (:remote-addr req)))

(defn iso-dt-now
  []
  (tf/unparse (tf/formatters :basic-date-time) (t/now)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Defs

(defonce receipts (atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper functions

(defn send-edn!
  [ch m]
  (let [edn (pr-str m)]
    (log/debug "Sending EDN:" edn)
    (send! ch edn)))

(defn send-message!
  [ch m]
  (if-let [err (rs/check-message "1.0" m)]
    (log/error "Failed to send a message - validation failed:" (str err))
    (send-edn! ch m)))

(defn dispatch-event!
  [ch receipt event]
  (try
    (log/debug "Dispatching" event "to" ch)
    (send-message! ch event)
    (catch Exception e
      (println "Failed to dispatch event:" e))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Message Handling

(defmulti handle-message
  (fn [ch msg components] (:message/type msg)))

(defmethod handle-message
  :query
  [ch {:keys [query/edn query/id]} {:keys [queries]}]
  (if-not (vector? edn)
    (send-message! ch {:message/type :query-response :query/id id :query/error "Query needs to be a vector"})
    (let [results (mapv (partial p/route-query queries) edn)]
      (send-message! ch {:message/type :query-response :query/id id :query/results results}))))

;;

(defmethod handle-message
  :command
  [ch {:keys [command/id command/key command/version] :as command} {:keys [kafka connections]}]
  (let [receipt (java.util.UUID/randomUUID)
        now (iso-dt-now)]
    (p/add-receipt! connections (partial dispatch-event! ch receipt) receipt)
    (if-let [error (command/receive-command!
                    receipt
                    (assoc command
                           :command/created-at now
                           :command/receipt receipt
                           :message/type :command-processed)
                    kafka)]
      (do
        (log/error "Failed to send msg to Kafka:" error)) ;; TODO do we tell the client?
      (send-message! ch {:message/type :command-receipt
                         :command/key key
                         :command/version version
                         :command/id id
                         :command/receipt receipt
                         :command/received-at now}))))

(defmethod handle-message
  :default
  [_ msg _]
  (log/error "Received an unknown message:" (:type msg) msg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Connection Handling

(defn disconnect! [cm channel status]
  (p/remove-connection! cm channel))

(defn connect! [cm channel]
  (p/add-connection! cm channel))

(defn ws-handler [request]
  (let [components (:witan.gateway.components.server/components request)]
    (with-channel request channel
      (connect! (:connections components) channel)
      (on-close channel (partial disconnect! (:connections components) channel))
      (on-receive channel #(try
                             (let [msg (read-string %)
                                   error (rs/check-message "1.0" msg)]
                               (if-not error
                                 (handle-message channel msg components)
                                 (send-edn! channel {:error error :original msg})))
                             (catch Exception e
                               (println "Exception thrown:" e)
                               (send-edn! channel {:error (str e) :original %})))))))

(defn login
  [req]
  {:status 200
   :body (json/generate-string {:id #uuid "00000000-0000-0000-0000-000000000000"
                                :token "0jO2cOEJOh8mJQ3p9eh9EEBn9oBp2Wecb0upoIeoGkMv0nIjvg3ovJUvgrkGJNge"})})

(defroutes app
  (GET "/ws" req (ws-handler req))
  (POST "/login" req (login req)))
