(ns witan.gateway.handler-test
  (:require [witan.gateway.handler :refer :all]
            [clojure.test :refer :all]))

(deftest conform-add-created-at-test
  (let [command {:kixi.comms.message/type "command"
                 :kixi.comms.command/key :foo/bar
                 :kixi.comms.command/version "1.0.0"
                 :kixi.comms.command/id (str (java.util.UUID/randomUUID))
                 :kixi.comms.command/payload {}}
        [_ r] (conform command)]
    (is (not (= :clojure.spec/invalid r)))
    (is (contains? r :kixi.comms.command/created-at) (pr-str r))))
