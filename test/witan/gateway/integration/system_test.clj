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
(def received-fn (atom nil))

(use-fixtures :once (partial cycle-system-fixture system))
(use-fixtures :each (partial create-ws-connection wsconn received-fn))

(def token
  {:kixi.comms.auth/token-pair
   {:auth-token (sign {:exp (ct/to-long (t/plus (t/now) (t/hours 1)))
                       :id (uuid)
                       :user-groups {:groups [(uuid)]}})}})

(defn create-command
  [command-key version id payload]
  (transit-encode (merge token
                         {:kixi.comms.message/type "command"
                          :kixi.comms.command/key command-key
                          :kixi.comms.command/version version
                          :kixi.comms.command/id id
                          :kixi.comms.command/created-at (timestamp)
                          :kixi.comms.command/payload payload})))

(deftest submit-ping-command-test
  (let [ping? (atom nil)
        id (uuid)]
    (reset! received-fn #(reset! ping? %))
    (ws/send-msg @wsconn (transit-encode (merge token
                                                {:kixi.comms.message/type "ping"
                                                 :kixi.comms.ping/id id
                                                 :kixi.comms.ping/created-at (timestamp)})))
    (wait-for-pred (fn [] @ping?))
    (is @ping?)
    (is (not (contains? @ping? :error)) (pr-str @ping?))
    (is (= "pong" (:kixi.comms.message/type @ping?)) (pr-str @ping?))
    (is (= id (:kixi.comms.pong/id @ping?)))))


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
    (wait-for-pred (fn [] @result))
    (is @result)
    (is (= payload @result))))

(deftest submit-command->event-rountrip-test
  (let [result (atom nil)
        fe-result (atom nil)
        id     (uuid)
        comms  (:comms @system)
        payload {:foo 123}
        fixed-payload (assoc payload :bar 456)]
    (reset! received-fn #(reset! fe-result (:kixi.comms.event/payload %)))
    (log/info "Submit Command->Event Roundtrip Test")
    (c/attach-command-handler! comms
                               :submit-command-test-2
                               :test/command2
                               "1.0.0"
                               (fn [{:keys [kixi.comms.command/payload]}]
                                 {:kixi.comms.message/type "event"
                                  :kixi.comms.event/key :test/command-received-2
                                  :kixi.comms.event/version "1.0.0"
                                  :kixi.comms.event/payload (assoc payload :bar 456)}))
    (c/attach-event-handler! comms
                             :submit-command-test-3
                             :test/command-received-2
                             "1.0.0"
                             (fn [{:keys [kixi.comms.event/payload]}]
                               (reset! result payload)
                               nil))
    (ws/send-msg @wsconn (create-command :test/command2 "1.0.0" id payload))
    (wait-for-pred (fn [] @result))
    (is @result)
    (is (= fixed-payload @result))
    (is (= fixed-payload @fe-result))))
