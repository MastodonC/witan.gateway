(ns witan.gateway.integration.base
  (:require [user :as repl]
            [environ.core :refer [env]]
            [gniazdo.core :as ws]
            [witan.gateway.handler :refer [transit-encode transit-decode]]
            [taoensso.timbre :as log]
            [clojure.core.async :refer :all]))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(def wait-tries (Integer/parseInt (env :wait-tries "65")))
(def wait-ms (Integer/parseInt (env :wait-ms "500")))

(defn cycle-system-fixture
  [a all-tests]
  (reset! a (repl/go))
  (Thread/sleep 2000)
  (all-tests)
  (repl/stop)
  (reset! a nil))

(defn create-ws-connection
  [a received-fn all-tests]
  (let [run-tests-ch (chan)
        clean (fn []
                (log/info "Closing websocket...")
                (ws/close @a)
                (reset! a nil))]
    (log/info "Opening websocket...")
    (reset! a (ws/connect "ws://localhost:30015/ws"
                          :on-connect (fn [& _]
                                        (log/info "Websocket on-connect called.")
                                        (put! run-tests-ch true))
                          :on-receive #(if @received-fn
                                         (@received-fn (transit-decode %)))))
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
