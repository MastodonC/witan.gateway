(ns witan.gateway.handler
  (:require [compojure.core :refer :all]
            [taoensso.timbre :as log]
            [clojure.spec :as s]
            [witan.gateway.protocols :as p]
            [clojure.core.async :as async :refer [chan go go-loop put! <!]]
            [org.httpkit.server :refer [send! with-channel on-close on-receive]]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-http.client :as http]
            [kixi.comms :as comms]
            [kixi.comms.time :refer [timestamp]]
            [cognitect.transit :as tr])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]))


(defn get-client-ip [req]
  (if-let [ips (get-in req [:headers "x-forwarded-for"])]
    (-> ips (clojure.string/split #",") first)
    (:remote-addr req)))

(def transit-encoding-level :json-verbose) ;; DO NOT CHANGE
(defn transit-decode-bytes [in]
  (let [reader (tr/reader in transit-encoding-level)]
    (tr/read reader)))
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
  [ch {:keys [kixi.comms.query/body kixi.comms.query/id]} {:keys [queries]}]
  (if-not (vector? body)
    (send-message! ch {:kixi.comms.message/type "query-response"
                       :kixi.comms.query/id id
                       :kixi.comms.query/error "Query needs to be a vector"})
    (let [results (mapv (partial p/route-query queries) body)]
      (send-message! ch {:kixi.comms.message/type "query-response"
                         :kixi.comms.query/id id
                         :kixi.comms.query/results results}))))

;;

(defmethod handle-message
  "command"
  [ch {:keys [kixi.comms.command/id
              kixi.comms.command/key
              kixi.comms.command/version
              kixi.comms.command/created-at
              kixi.comms.command/payload] :as command} {:keys [comms connections]}]
  (p/add-receipt! connections (partial dispatch-event! ch id) id)
  (log/info "Forwarding command" key version id)
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
  (let [components (:components request)]
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

(defn post-to-heimdall
  [{:keys [body] :as req} path]
  (let [params (transit-decode-bytes body)
        {:keys [host port]} (get-in req [:directory :heimdall])
        _ (log/info "!!!!" params host port)
        heimdall-url (str "http://" host ":" port "/" path)]
    (update-in (http/post heimdall-url
                          {:content-type :json
                           :accept :json
                           :throw-exceptions false
                           :as :json
                           :form-params params}) :body transit-encode)))

(defn signup
  "forward signup call to heimdall"
  [req]
  (post-to-heimdall req "user"))

(defn login
  "forward login to heimdall and return tokens"
  [req]
  (post-to-heimdall req "create-auth-token"))

(defroutes app
  (GET "/ws" req (ws-handler req))
  (GET "/health" [] (str "hello"))
  (POST "/signup" req (signup req))
  (POST "/login" req (login req)))
