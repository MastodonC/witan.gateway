(ns witan.gateway.queries.data-acquisition
  (:require [graph-router.core :as gr]))

(def request-fields
  [{:kixi.data-acquisition.request-for-data/recipients
    [:kixi.user/id :kixi.user/email]}
   :kixi.data-acquisition.request-for-data/created-at
   :kixi.data-acquisition.request-for-data/batch-id
   :kixi.data-acquisition.request-for-data/schema-id])

(defn requests-by-requester
  [_ id]
  [])
