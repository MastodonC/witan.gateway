(ns witan.gateway.queries.data-acquisition
  (:require [graph-router.core :as gr]))

(def group-fields
  [:kixi.group/id :kixi.group/email :kixi.group/type :kixi.group/name])

(def schema-fields
  [:id :name])

(def request-fields
  [{:kixi.data-acquisition.request-for-data/recipients
    group-fields}
   {:kixi.data-acquisition.request-for-data/destinations
    group-fields}
   :kixi.data-acquisition.request-for-data/created-at
   :kixi.data-acquisition.request-for-data/request-id
   :kixi.data-acquisition.request-for-data/requester-id
   {:kixi.data-acquisition.request-for-data/schema
    schema-fields}
   :kixi.data-acquisition.request-for-data/message])

(defn requests-by-requester
  [_ id]
  [])

(defn request-by-id
  [_ id]
  {})
