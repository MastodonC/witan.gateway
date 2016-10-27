(ns witan.gateway.integration.system-test
  (:require [clojure.test :refer :all]
            [witan.gateway.integration.base :refer :all]
            [gniazdo.core :as ws]
            [cheshire.core :as json]
            [kixi.comms :as c]
            [kixi.comms.time :refer [timestamp]]
            [taoensso.timbre :as log]))


(def system (atom nil))
(def wsconn (atom nil))
(def received-fn (atom nil))

(defn create-ws-connection
  [a all-tests]
  (reset! a (ws/connect "ws://localhost:30015/ws"
                        :on-receive #(if @received-fn
                                       (@received-fn (json/parse-string % keyword)))))
  (all-tests)
  (ws/close @a)
  (reset! a nil))

(use-fixtures :once (partial cycle-system-fixture system))
(use-fixtures :each (partial create-ws-connection wsconn))

(deftest submit-ping-command-test
  (let [ping? (atom nil)
        id (uuid)]
    (reset! received-fn #(reset! ping? %))
    (ws/send-msg @wsconn (json/generate-string {:kixi.comms.message/type "ping"
                                                :kixi.comms.ping/id id
                                                :kixi.comms.ping/created-at (timestamp)}))
    (wait-for-pred (fn [] @ping?))
    (is @ping?)
    (is (not (contains? @ping? :error)) (pr-str @ping?))
    (is (= "pong" (:kixi.comms.message/type @ping?)))
    (is (= id (:kixi.comms.pong/id @ping?)))))


(deftest submit-command-test
  (let [result (atom nil)
        id     (uuid)
        comms  (:comms @system)
        payload {:foo 123}]
    (log/info "Submit Command Test")
    (c/attach-command-handler! comms :submit-command-test-1 :test/command "1.0.0" (fn [{:keys [kixi.comms.command/payload]}]
                                                                                    (reset! result payload)
                                                                                    {:kixi.comms.message/type "event"
                                                                                     :kixi.comms.event/key :test/command-received-1
                                                                                     :kixi.comms.event/version "1.0.0"
                                                                                     :kixi.comms.event/payload (assoc payload :bar 456)}))
    (ws/send-msg @wsconn (json/generate-string {:kixi.comms.message/type "command"
                                                :kixi.comms.command/key :test/command
                                                :kixi.comms.command/version "1.0.0"
                                                :kixi.comms.command/id id
                                                :kixi.comms.command/created-at (timestamp)
                                                :kixi.comms.command/payload payload}))
    (wait-for-pred (fn [] @result))
    (is @result)
    (is (= payload @result))))

(deftest submit-command->event-rountrip-test
  (let [result (atom nil)
        id     (uuid)
        comms  (:comms @system)
        payload {:foo 123}]
    (log/info "Submit Command->Event Roundtrip Test")
    (c/attach-command-handler! comms
                               :submit-command-test-2
                               :test/command
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
    (ws/send-msg @wsconn (json/generate-string {:kixi.comms.message/type "command"
                                                :kixi.comms.command/key :test/command
                                                :kixi.comms.command/version "1.0.0"
                                                :kixi.comms.command/id id
                                                :kixi.comms.command/created-at (timestamp)
                                                :kixi.comms.command/payload payload}))
    (wait-for-pred (fn [] @result))
    (is @result)
    (is (= (assoc payload :bar 456) @result))))
