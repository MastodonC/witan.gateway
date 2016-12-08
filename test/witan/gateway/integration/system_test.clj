(ns witan.gateway.integration.system-test
  (:require [clojure.test :refer :all]
            [witan.gateway.integration.base :refer :all]
            [witan.gateway.handler :refer [transit-encode transit-decode]]
            [gniazdo.core :as ws]
            [kixi.comms :as c]
            [kixi.comms.time :refer [timestamp]]
            [buddy.core.keys            :as keys]
            [buddy.sign.jwt             :as jwt]
            [taoensso.timbre :as log]))


(def system (atom nil))
(def wsconn (atom nil))
(def received-fn (atom nil))

(use-fixtures :once (partial cycle-system-fixture system))
(use-fixtures :each (partial create-ws-connection wsconn received-fn))

(defn sign
  [payload]
  (let [prvk (keys/private-key "./test-resources/auth_privkey.pem" "secret123")]
    (jwt/sign payload prvk {:alg :rs256})))

(def token
  {:kixi.comms.auth/token-pair
   {:auth-token (sign {:id (uuid)
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
    (is (= "pong" (:kixi.comms.message/type @ping?)))
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
