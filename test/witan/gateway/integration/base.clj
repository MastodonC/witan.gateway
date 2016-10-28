(ns witan.gateway.integration.base
  (:require [user :as repl]
            [environ.core :refer [env]]
            [gniazdo.core :as ws]
            [witan.gateway.handler :refer [transit-encode transit-decode]]
            [taoensso.timbre :as log]))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn cycle-system-fixture
  [a all-tests]
  (reset! a (repl/go))
  (Thread/sleep 2000)
  (all-tests)
  (repl/stop)
  (reset! a nil))

(defn create-ws-connection
  [a received-fn all-tests]
  (reset! a (ws/connect "ws://localhost:30015/ws"
                        :on-receive #(if @received-fn
                                       (@received-fn (transit-decode %)))))
  (all-tests)
  (ws/close @a)
  (reset! a nil))

(defn wait-for-pred
  ([p]
   (let [wait-tries (or (some-> (env :wait-tries) (Integer/valueOf)) 65)]
     (wait-for-pred p wait-tries)))
  ([p tries]
   (let [wait-ms (or (some-> (env :wait-ms) (Integer/valueOf)) 500)]
     (wait-for-pred p tries wait-ms)))
  ([p tries ms]
   (log/info "Waiting for predicate -" tries "x" ms)
   (loop [try tries]
     (when (and (pos? try) (not (p)))
       (Thread/sleep ms)
       (recur (dec try))))))
