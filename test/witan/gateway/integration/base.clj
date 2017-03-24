(ns witan.gateway.integration.base
  (:require [user :as repl]
            [environ.core :refer [env]]
            [gniazdo.core :as ws]
            [witan.gateway.handler :refer [transit-encode transit-decode]]
            [taoensso.timbre :as log]
            [buddy.core.keys            :as keys]
            [buddy.sign.jwt             :as jwt]
            [clojure.core.async :refer :all]
            [amazonica.aws.dynamodbv2 :as ddb]
            [kixi.comms.components.kinesis :as kinesis]))

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

(defn table-exists?
  [endpoint table]
  (try
    (ddb/describe-table {:endpoint endpoint} table)
    (catch Exception e false)))

(defn clear-tables
  [endpoint table-names]
  (doseq [sub-table-names (partition-all 10 table-names)]
    (doseq [table-name sub-table-names]
      (ddb/delete-table {:endpoint endpoint} :table-name table-name))
    (loop [tables sub-table-names]
      (when (not-empty tables)
        (recur (doall (filter (partial table-exists? endpoint) tables)))))))

(defn- tear-down-kinesis!
  [system]
  (when-let [kinesis (:comms system)]
    (log/info "Deleting dynamo tables ...")
    (clear-tables (:dynamodb-endpoint kinesis)
                  [(kinesis/event-worker-app-name (:app kinesis) (:profile kinesis))
                   (kinesis/command-worker-app-name (:app kinesis) (:profile kinesis))])

    #_(log/info "Deleting streams...")
    #_(kinesis/delete-streams! (:endpoint kinesis) (vals (:streams kinesis)))))

(defn cycle-system-fixture
  [a all-tests]
  (reset! a (repl/go))
  (Thread/sleep 2000)
  (try
    (all-tests)
    (finally
      (repl/stop)
      #_(tear-down-kinesis! @a)
      (reset! a nil))))

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
                          :on-receive #(when @received-fn
                                         (log/info "Websocket received something!")
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
