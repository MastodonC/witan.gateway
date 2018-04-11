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
(def token (atom nil))
(def wsconn (atom nil))
(def received-fn (atom nil))

(use-fixtures :once
  (partial cycle-system-fixture system)
  (partial login system token))
(use-fixtures :each (partial create-ws-connection wsconn received-fn))

(defn send-query
  [qid query-name & params]
  (ws/send-msg @wsconn (transit-encode (merge @token
                                              {:kixi.comms.message/type "query"
                                               :kixi.comms.query/body {query-name [(vec params) :fields]}
                                               :kixi.comms.query/id qid}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest all-queries-test
  (doseq [query (keys queryr/functions)]
    (testing (str "Query " query)
      (let [qid (uuid)
            resp (atom nil)]
        (reset! received-fn #(reset! resp %))
        (send-query qid query [] {})
        (wait-for-pred #(deref resp))
        (is (= (:kixi.comms.message/type @resp) "query-response") (str @resp))
        (is (= (:kixi.comms.query/id @resp) qid))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest broken-query
  (let [qid (uuid)
        resp (atom nil)]
    (reset! received-fn #(reset! resp %))
    (send-query qid :datastore/metadata-with-activities [:kixi.datastore.metadatastore/file-read])
    (wait-for-pred #(deref resp))
    (is (= (:kixi.comms.message/type @resp) "query-response"))
    (is (= (:kixi.comms.query/id @resp) qid))
    (is (= (get-in @resp [:kixi.comms.query/results 0 :datastore/metadata-with-activities :error])
           "incorrect amount of arguments were supplied"))))

(deftest metadata-activites-count
  (let [rcount (rand-nth (range 5 15))
        qid (uuid)
        resp (atom nil)]
    (reset! received-fn #(reset! resp %))
    (send-query qid :datastore/metadata-with-activities [:kixi.datastore.metadatastore/file-read] {:count rcount})
    (wait-for-pred #(deref resp))
    (is (= (:kixi.comms.message/type @resp) "query-response"))
    (is (= (:kixi.comms.query/id @resp) qid))
    (is (<= (get-in @resp [:kixi.comms.query/results 0 :datastore/metadata-with-activities :paging :count]) rcount))
    (is (= (get-in @resp [:kixi.comms.query/results 0 :datastore/metadata-with-activities :paging :index]) 0))))

(deftest metadata-activites-count-50
  (let [rcount 50
        qid (uuid)
        resp (atom nil)]
    (reset! received-fn #(reset! resp %))
    (send-query qid :datastore/metadata-with-activities [:kixi.datastore.metadatastore/file-read] {:count rcount})
    (wait-for-pred #(deref resp))
    (is (= (:kixi.comms.message/type @resp) "query-response"))
    (is (= (:kixi.comms.query/id @resp) qid))
    (is (<= (get-in @resp [:kixi.comms.query/results 0 :datastore/metadata-with-activities :paging :count]) rcount))
    (is (= (get-in @resp [:kixi.comms.query/results 0 :datastore/metadata-with-activities :paging :index]) 0))))

(deftest metadata-activites-count-?
  (let [rcount (rand-nth (range 5 15))
        rindex (rand-nth (range 5 15))
        qid (uuid)
        resp (atom nil)]
    (reset! received-fn #(reset! resp %))
    (send-query qid :datastore/metadata-with-activities [:kixi.datastore.metadatastore/file-read] {:count rcount :index rindex})
    (wait-for-pred #(deref resp))
    (is (= (:kixi.comms.message/type @resp) "query-response"))
    (is (= (:kixi.comms.query/id @resp) qid))
    (is (<= (get-in @resp [:kixi.comms.query/results 0 :datastore/metadata-with-activities :paging :count]) rcount))
    (is (= (get-in @resp [:kixi.comms.query/results 0 :datastore/metadata-with-activities :paging :index]) rindex))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest get-groups
  (let [rcount (rand-nth (range 5 15))
        qid (uuid)
        qid2 (uuid)
        resp (atom nil)
        get-items #(-> %1 :kixi.comms.query/results first %2 :items)]
    (testing "Get all groups"
      (reset! received-fn #(reset! resp %))
      (send-query qid :groups/search [])
      (wait-for-pred #(deref resp))
      (is (= (:kixi.comms.message/type @resp) "query-response"))
      (is (= (:kixi.comms.query/id @resp) qid))
      (is (not-empty (get-items @resp :groups/search))))
    (testing "Get specific groups"
      (let [group-ids (->> (get-items @resp :groups/search)
                           (map :kixi.group/id)
                           (shuffle)
                           (take 2)
                           (set))]
        (reset! resp nil)
        (send-query qid2 :groups/by-ids group-ids)
        (wait-for-pred #(deref resp))
        (is (= (:kixi.comms.message/type @resp) "query-response"))
        (is (= (:kixi.comms.query/id @resp) qid2))
        (is (= group-ids (set (map :kixi.group/id (get-items @resp :groups/by-ids)))))))))
