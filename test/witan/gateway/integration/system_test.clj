(ns witan.gateway.integration.system-test
  (:require [clojure.test :refer :all]
            [witan.gateway.integration.base :refer :all]
            [witan.gateway.handler :refer [transit-encode transit-decode]]
            [gniazdo.core :as ws]
            [kixi.comms :as c]
            [kixi.comms.time :refer [timestamp]]
            [clj-time.core :as t]
            [clj-time.coerce :as ct]
            [taoensso.timbre :as log]))


(def system (atom nil))
(def wsconn (atom nil))
(def token (atom nil))
(def received-fn (atom nil))

(defn set-receiver-fn!
  [fn name]
  (log/info "Setting receiver fn token:" name)
  (reset! received-fn fn))

(use-fixtures :once
  (partial cycle-system-fixture system)
  (partial login system token))
(use-fixtures :each (partial create-ws-connection wsconn received-fn))

(defn create-command
  [command-key version id payload]
  (transit-encode (merge @token
                         {:kixi.comms.message/type "command"
                          :kixi.comms.command/key command-key
                          :kixi.comms.command/version version
                          :kixi.comms.command/id id
                          :kixi.comms.command/created-at (timestamp)
                          :kixi.comms.command/payload payload})))

(deftest submit-ping-command-test
  (let [ping? (atom nil)
        id (uuid)]
    (set-receiver-fn! #(reset! ping? %) "submit-ping-command-test")
    (ws/send-msg @wsconn (transit-encode (merge @token
                                                {:kixi.comms.message/type "ping"
                                                 :kixi.comms.ping/id id
                                                 :kixi.comms.ping/created-at (timestamp)})))
    (wait-for-pred #(deref ping?))
    (is @ping?)
    (is (not (contains? @ping? :error)) (pr-str @ping?))
    (is (= "pong" (:kixi.comms.message/type @ping?)) (pr-str @ping?))
    (is (= id (:kixi.comms.pong/id @ping?)))))

(deftest refresh-token-test
  (let [refreshed? (atom nil)
        id (uuid)]
    (set-receiver-fn! #(reset! refreshed? %) "refresh-token-test")
    (ws/send-msg @wsconn (transit-encode (merge @token
                                                {:kixi.comms.message/type "refresh"})))
    (wait-for-pred #(deref refreshed?))
    (is @refreshed?)
    (is (not (contains? @refreshed? :error)) (pr-str @refreshed?))
    (is (= "refresh-response" (:kixi.comms.message/type @refreshed?)) (pr-str @refreshed?))
    (is (:kixi.comms.auth/token-pair @refreshed?))))


(deftest submit-command-test
  (let [result (atom nil)
        id     (uuid)
        comms  (:comms @system)
        payload {:foo 123}]
    (log/info "Submit Command Test")
    (c/attach-command-handler! comms
                               :submit-command-test-1
                               :test/command1
                               "1.0.0"
                               (fn [{:keys [kixi.comms.command/payload]}]
                                 (log/info "Submit Command Test - Handled command!")
                                 (reset! result payload)
                                 {:kixi.comms.message/type "event"
                                  :kixi.comms.event/key :test/command-received-1
                                  :kixi.comms.event/version "1.0.0"
                                  :kixi.comms.event/payload (assoc payload :bar 456)}))
    (ws/send-msg @wsconn (create-command :test/command1 "1.0.0" id payload))
    (wait-for-pred #(deref result))
    (is @result)
    (is (= payload @result))))

(deftest submit-command->event-rountrip-test
  (let [result (atom [])
        fe-result (atom [])
        id     (uuid)
        comms  (:comms @system)
        payload {:foo 123}
        fixed-payload (assoc payload :bar 456)]
    (set-receiver-fn! #(swap! fe-result conj (:kixi.comms.event/payload %)) "submit-command->event-rountrip-test")
    (log/info "Submit Command->Event Roundtrip Test")
    (c/attach-command-handler! comms
                               :submit-command-test-2
                               :test/command2
                               "1.0.0"
                               (fn [{:keys [kixi.comms.command/payload]}]
                                 [{:kixi.comms.message/type "event"
                                   :kixi.comms.event/key :test/command-received-2a
                                   :kixi.comms.event/version "1.0.0"
                                   :kixi.comms.event/payload (assoc payload :bar 456)
                                   :kixi.comms.event/partition-key id}
                                  {:kixi.comms.message/type "event"
                                   :kixi.comms.event/key :test/command-received-2b
                                   :kixi.comms.event/version "1.0.0"
                                   :kixi.comms.event/payload (assoc payload :bar 456)
                                   :kixi.comms.event/partition-key id}]))
    (c/attach-event-handler! comms
                             :submit-command-test-3
                             :test/command-received-2a
                             "1.0.0"
                             (fn [{:keys [kixi.comms.event/payload]}]
                               (swap! result conj payload)
                               nil))
    (c/attach-event-handler! comms
                             :submit-command-test-3
                             :test/command-received-2b
                             "1.0.0"
                             (fn [{:keys [kixi.comms.event/payload]}]
                               (swap! result conj payload)
                               nil))
    (ws/send-msg @wsconn (create-command :test/command2 "1.0.0" id payload))
    (wait-for-pred #(and (deref result)
                         (= (count (deref result)) 2)))
    (is @result)
    (is (every? #{fixed-payload} @result))
    (is (every? #{fixed-payload} @fe-result))))
