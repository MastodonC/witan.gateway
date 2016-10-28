(ns witan.gateway.handler
  (:require [compojure.core :refer :all]
            [taoensso.timbre :as log]
            [clojure.spec :as s]
            [witan.gateway.protocols :as p]
            [clojure.core.async :as async :refer [chan go go-loop put! <!]]
            [org.httpkit.server :refer [send! with-channel on-close on-receive]]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [kixi.comms :as comms]
            [kixi.comms.time :refer [timestamp]]
            [cognitect.transit :as tr])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))


(defn get-client-ip [req]
  (if-let [ips (get-in req [:headers "x-forwarded-for"])]
    (-> ips (clojure.string/split #",") first)
    (:remote-addr req)))

(def transit-encoding-level :json-verbose) ;; DO NOT CHANGE
(defn transit-decode [s]
  (let [sbytes (.getBytes s)
        in (ByteArrayInputStream. sbytes)
        reader (tr/reader in transit-encoding-level)]
    (tr/read reader)))
(defn transit-encode [s]
  (let [out (ByteArrayOutputStream. 4096)
        writer (tr/writer out transit-encoding-level)]
    (tr/write writer s)
    (.toString out)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper functions

(defn send-outbound!
  [ch m]
  (let [o (transit-encode m)]
    (log/debug "Sending:" o)
    (send! ch o)))

(defn send-message!
  [ch m]
  (if-let [err (s/explain-data :kixi.comms.message/message m)]
    (log/error "Failed to send a message - validation failed:" (str err))
    (send-outbound! ch m)))

(defn dispatch-event!
  [ch receipt event]
  (try
    (log/debug "Dispatching event to" ch)
    (send-message! ch event)
    (catch Exception e
      (log/error "Failed to dispatch event:" e))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Message Handling

(defmulti handle-message
  (fn [ch msg components] (:kixi.comms.message/type msg)))

(defmethod handle-message
  "query"
  [ch {:keys [query/edn query/id]} {:keys [queries]}]
  (if-not (vector? edn)
    (send-message! ch {:kixi.comms.message/type :query-response :query/id id :query/error "Query needs to be a vector"})
    (let [results (mapv (partial p/route-query queries) edn)]
      (send-message! ch {:kixi.comms.message/type :query-response :query/id id :query/results results}))))

;;

(defmethod handle-message
  "command"
  [ch {:keys [kixi.comms.command/id
              kixi.comms.command/key
              kixi.comms.command/version
              kixi.comms.command/created-at
              kixi.comms.command/payload] :as command} {:keys [comms connections]}]
  (p/add-receipt! connections (partial dispatch-event! ch id) id)
  (comms/send-command! comms key version payload {:id id
                                                  :created-at created-at}))

(defmethod handle-message
  "ping"
  [ch {:keys [kixi.comms.ping/id]} {:keys [comms connections]}]
  (send-outbound! ch {:kixi.comms.message/type "pong"
                      :kixi.comms.pong/id id
                      :kixi.comms.pong/created-at (timestamp)}))

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

(defn conform
  "There are some things we can't expect/don't want gateway clients to
   provide so we add them here"
  [msg]
  (let [pre-process
        (case (:kixi.comms.message/type msg)
          "command" (assoc msg :kixi.comms.command/created-at (timestamp))
          msg)
        result
        (case (:kixi.comms.message/type msg)
          "ping" pre-process
          (s/conform :kixi.comms.message/message pre-process))]
    [pre-process result]))

(defn ws-handler [request]
  (let [components (:witan.gateway.components.server/components request)]
    (with-channel request channel
      (connect! (:connections components) channel)
      (on-close channel (partial disconnect! (:connections components) channel))
      (on-receive channel #(try
                             (let [raw-msg (transit-decode %)
                                   [cleaned result] (conform raw-msg)]
                               (if-not (= result :clojure.spec/invalid)
                                 (handle-message channel result components)
                                 (send-outbound! channel {:error (s/explain-data :kixi.comms.message/message cleaned)
                                                          :original raw-msg})))
                             (catch Exception e
                               (println "Exception thrown:" e)
                               (send-outbound! channel {:error (str e) :original %})))))))

(defn login
  [req]
  {:status 200
   :body (transit-encode {:id #uuid "00000000-0000-0000-0000-000000000000"
                          :token "0jO2cOEJOh8mJQ3p9eh9EEBn9oBp2Wecb0upoIeoGkMv0nIjvg3ovJUvgrkGJNge"})})

(defroutes app
  (GET "/ws" req (ws-handler req))
  (GET "/health" [] (str "hello"))
  (POST "/login" req (login req)))
