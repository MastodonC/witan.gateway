(ns witan.gateway.integration.query-test
  (:require [clojure.test :refer :all]
            [witan.gateway.integration.base :refer :all]
            [witan.gateway.handler :refer [transit-encode transit-decode]]
            [witan.gateway.components.query-router :as queryr]
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

(def token
  {:kixi.comms.auth/token-pair
   {:auth-token (sign {:id (uuid)
                       :user-groups [(uuid)]
                       :self-group (uuid)})}})

(defn send-query
  [qid query-name param-v]
  (ws/send-msg @wsconn (transit-encode (merge token
                                              {:kixi.comms.message/type "query"
                                               :kixi.comms.query/body {query-name [[param-v] :fields]}
                                               :kixi.comms.query/id qid}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest all-queries-test
  (doseq [query (keys queryr/functions)]
    (testing (str "Query " query)
      (let [qid (uuid)
            resp (atom nil)]
        (reset! received-fn #(reset! resp %))
        (send-query qid query [])
        (wait-for-pred #(deref resp))
        (is (= (:kixi.comms.message/type @resp) "query-response") (str @resp))
        (is (= (:kixi.comms.query/id @resp) qid))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest metadata-activites-returns-nothing-when-there-is-nothing
  (let [qid (uuid)
        resp (atom nil)]
    (reset! received-fn #(reset! resp %))
    (send-query qid :datastore/metadata-with-activities [:kixi.datastore.metadatastore/file-read])
    (wait-for-pred #(deref resp))
    (is (= {:kixi.comms.message/type "query-response",
            :kixi.comms.query/id qid
            :kixi.comms.query/results
            [{:datastore/metadata-with-activities {:items [], :paging {:total 0, :count 0, :index 0}}}]}
           @resp))))
