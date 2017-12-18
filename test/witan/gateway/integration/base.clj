(ns witan.gateway.integration.base
  (:require [user :as repl]
            [environ.core :refer [env]]
            [gniazdo.core :as ws]
            [witan.gateway.handler :refer [transit-encode transit-decode]]
            [taoensso.timbre :as log]
            [buddy.core.keys            :as keys]
            [buddy.sign.jwt             :as jwt]
            [clojure.core.async :refer :all])
  (:import (org.eclipse.jetty.websocket.client WebSocketClient)
           (org.eclipse.jetty.websocket.api WebSocketPolicy)))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(def wait-tries (Integer/parseInt (env :wait-tries "120")))
(def wait-ms (Integer/parseInt (env :wait-ms "500")))

(def public-key (env :super-secret-public-pem-file
                     "./test-resources/heimdall-dev_pubkey.pem"))
(defn sign
  [payload]
  (let [prvk
        (keys/private-key
         (env :super-secret-pem-file "./test-resources/heimdall-dev_privkey.pem")
         (env :super-secret-password "test"))]
    (jwt/sign payload prvk {:alg :rs256})))

(defn cycle-system-fixture
  [a all-tests]
  (log/info "Starting system via cycle-system-fixture")
  (reset! a (repl/go))
  (Thread/sleep 2000)
  (try
    (all-tests)
    (finally
      (repl/stop)
      (reset! a nil))))

(defn create-ws-connection
  [a received-fn all-tests]
  (let [run-tests-ch (chan)
        clean (fn []
                (log/info "Closing websocket...")
                (ws/close @a)
                (reset! a nil))
        url "ws://localhost:30015/ws"
        c (ws/client)]
    (.setMaxTextMessageSize (.getPolicy c) 4194304)
    (.start c)
    (log/info "Opening websocket...")
    (reset! a (ws/connect url
                :on-connect (fn [& _]
                              (log/info "Websocket on-connect called.")
                              (put! run-tests-ch true))
                :on-receive #(when @received-fn
                               (log/info "Websocket received something!")
                               (@received-fn (transit-decode %)))
                :client c))
    (alt!!
      run-tests-ch    (do
                        (log/info "Running all tests...")
                        (all-tests)
                        (clean))
      (timeout 10000) (do
                        (log/error "Websocket connection timed out.")
                        (clean))) identity))

(defn wait-for-pred
  ([p]
   (wait-for-pred p wait-tries))
  ([p tries]
   (wait-for-pred p tries wait-ms))
  ([p tries ms]
   (log/info "Waiting for predicate -" tries "x" ms)
   (loop [try tries]
     (when (and (pos? try) (not (p)))
       (Thread/sleep ms)
       (recur (dec try))))))

(defn login
  [system-atom result-atom all-tests]
  (log/info "Logging in as test user...")
  (reset! result-atom {:kixi.comms.auth/token-pair
                       (-> (assoc (:http-kit @system-atom)
                                  :body {:username "test@mastodonc.com"
                                         :password "Secret123"})
                           (witan.gateway.handler/login)
                           :body
                           (witan.gateway.handler/transit-decode)
                           :token-pair)})
  (log/info "Login result:" @result-atom)
  (all-tests))
