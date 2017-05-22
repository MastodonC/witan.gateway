(ns witan.gateway.handler
  (:require [compojure.core :refer :all]
            [taoensso.timbre :as log]
            [clojure.spec :as s]
            [witan.gateway.protocols :as p]
            [clojure.core.async :as async :refer [chan go go-loop put! <! <!!]]
            [org.httpkit.server :refer [send! with-channel on-close on-receive]]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clj-http.client :as http]
            [kixi.comms :as comms]
            [kixi.comms.time :refer [timestamp]]
            [cognitect.transit :as tr]
            [clojure.java.io :as io]
            [clojure.data.codec.base64 :as b64])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream]
           [org.httpkit.BytesInputStream]))

(defn get-client-ip [req]
  (if-let [ips (get-in req [:headers "x-forwarded-for"])]
    (-> ips (clojure.string/split #",") first)
    (:remote-addr req)))

(def transit-encoding-level :json-verbose) ;; DO NOT CHANGE
(defn transit-decode-bytes [in]
  (let [reader (tr/reader in transit-encoding-level)]
    (tr/read reader)))
(defn transit-decode [^String s]
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

(defn decode-params
  [params]
  (if (and params (or (= (type params) ByteArrayInputStream)
                      (= (type params) org.httpkit.BytesInputStream)))
    (transit-decode-bytes params)
    params))

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
      (log/error e "Failed to dispatch event:" event))))

(defn post-to-heimdall
  ([components path]
   (post-to-heimdall components path nil))
  ([components path p]
   (try
     (let [params (decode-params p)
           {:keys [host port]} (get-in components [:directory :heimdall])
           heimdall-url (str "http://" host ":" port "/" path)
           r (http/post heimdall-url
                        {:content-type :transit+json
                         :accept :transit+json
                         :throw-exceptions false
                         :as :transit+json
                         :form-params params})]
       (if (= 201 (:status r))
         (update r :body transit-encode)
         {:status (:status r)
          :body (transit-encode {:witan.gateway/error (:body r)})}))
     (catch com.fasterxml.jackson.core.JsonParseException e
       (log/error e)
       {:status 500
        :body (transit-encode {:witan.gateway/error (.getMessage e)})}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Message Handling

(defmulti handle-message
  (fn [ch msg user components] (:kixi.comms.message/type msg)))

(defmethod handle-message
  "query"
  [ch {:keys [kixi.comms.query/body kixi.comms.query/id]} user {:keys [queries]}]
  (if-not (map? body)
    (send-message! ch {:kixi.comms.message/type "query-response"
                       :kixi.comms.query/id id
                       :kixi.comms.query/error "Query needs to be a vector"})
    (let [results (mapv (partial p/route-query queries user) body)]
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
              kixi.comms.command/payload] :as command} user {:keys [comms connections]}]
  (p/add-receipt! connections ch id dispatch-event!)
  (log/info "Forwarding command" key version id)
  (comms/send-command! comms key version user payload {:kixi.comms.command/id id
                                                       :created-at created-at}))

(defmethod handle-message
  "ping"
  [ch {:keys [kixi.comms.ping/id]} user {:keys [comms connections]}]
  (log/trace "Received ping!")
  (send-outbound! ch (merge {:kixi.comms.message/type "pong"
                             :kixi.comms.pong/created-at (timestamp)}
                            (when id {:kixi.comms.pong/id id}))))

(defmethod handle-message
  "refresh"
  [ch {:keys [kixi.comms.auth/token-pair]} user components]
  (let [r (post-to-heimdall components "refresh-auth-token" (select-keys token-pair [:refresh-token]))]
    (if (= 201 (:status r))
      (send-outbound! ch {:kixi.comms.message/type "refresh-response"
                          :kixi.comms.auth/token-pair (:token-pair (transit-decode (:body r)))})
      (send-outbound! ch {:kixi.comms.message/type "refresh-response"
                          :kixi.comms.auth/error (:body r)}))))

(defmethod handle-message
  :default
  [_ msg _ _]
  (log/error "Received an unknown message:"
             (:kixi.comms.message/type msg) msg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Connection Handling

(defn error-message
  [original error-key error-str]
  (let [error-payload {:witan.gateway/error error-key}
        error-payload (if-not (empty? error-str)
                        (assoc error-payload :witan.gateway/error-str (str error-str))
                        error-payload)]
    {:kixi.comms.message/type "error"
     :kixi.comms.message/original original
     :kixi.comms.message/payload error-payload}))

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
          "command" (s/conform :kixi.comms.message/message pre-process)
          "query"   (s/conform :kixi.comms.message/message pre-process)
          pre-process)]
    [pre-process result]))

(defn valid-auth?
  [auth {:keys [kixi.comms.auth/token-pair] :as msg}]
  (if (= "refresh" (:kixi.comms.message/type msg))
    {} ;; if refreshing, return an empty user
    (let [{:keys [auth-token]} token-pair]
      (p/authenticate auth (t/now) auth-token))))

(defn ws-handler [request]
  (let [components (:components request)]
    (with-channel request channel
      (connect! (:connections components) channel)
      (on-close channel (partial disconnect! (:connections components) channel))
      (on-receive channel #(try
                             (let [raw-msg (transit-decode %)
                                   user-payload (valid-auth? (:auth components) raw-msg)]
                               (if user-payload
                                 (let [[cleaned result] (conform raw-msg)]
                                   (if-not (= result :clojure.spec/invalid)
                                     (handle-message channel result user-payload components)
                                     (send-outbound!
                                      channel
                                      (error-message raw-msg :invalid-msg (s/explain-data :kixi.comms.message/message raw-msg)))))
                                 (send-outbound!
                                  channel
                                  (error-message raw-msg :unauthenticated nil))))
                             (catch Exception e
                               (log/error e)
                               (send-outbound!
                                channel
                                (error-message % :server-error (str e)))))))))

(defn signup
  "forward signup call to heimdall"
  [req]
  (post-to-heimdall req "user" (:body req)))

(defn login
  "forward login to heimdall and return tokens"
  [req]
  (post-to-heimdall req "create-auth-token" (:body req)))

(defn download
  "create a download link and 301 to it"
  [req]
  (let [auth       (get-in req [:components :auth])
        downloads  (get-in req [:components :downloads])
        id         (get-in req [:params "id"])
        auth-token (get-in req [:cookies "token" :value])
        user       (p/authenticate auth (t/now) auth-token)]
    (if-not user
      {:status 401
       :body "Unauthenticated"}
      (if-let [location (and user
                             (p/get-download-redirect downloads user id))]
        {:status 302
         :headers {"Location" location}}
        {:status 401
         :body "Unauthorized"}))))

(defn request-password-reset
  [req]
  (let [comms    (get-in req [:components :comms])
        username (:username (decode-params (:body req)))]
    (if (clojure.string/blank? username)
      {:status 400 :body (transit-encode "No username?")}
      (do
        (comms/send-command! comms
                             :kixi.heimdall/create-password-reset-request
                             "1.0.0"
                             nil
                             {:username username})
        {:status 201 :body (transit-encode "OK")}))))

(defn complete-password-reset
  [req]
  (post-to-heimdall req "reset-password" (:body req)))

(defroutes app
  (GET "/ws" req (ws-handler req))
  (GET "/healthcheck" [] (str "hello"))
  (GET "/download" req (download req))
  (POST "/signup" req (signup req))
  (POST "/login" req (login req))
  (POST "/reset" req (request-password-reset req))
  (POST "/complete-reset" req (complete-password-reset req)))
