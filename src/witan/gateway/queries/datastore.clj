(ns witan.gateway.queries.datastore
  (:require [taoensso.timbre :as log]
            [org.httpkit.client :as http]))

(defn datastore-url
  [s route]
  (str "http://"
       (get-in s [:datastore :host]) ":"
       (get-in s [:datastore :port]) route))

(def data-fields
  [:foo :bar])

;; kixi.datastore.metadatastore

(defn files-by-author
  [d _]
  "List files with *this* premission set."
  #_(let [url (datastore-url d "/file")
          resp @(http/get url {:query-params
                               {:sharing_meta-read ["valuea" "valueb"]
                                :sharing_meta-visible ["value1" "value2"]}})]
      (log/info ">>>>>>>>>>" resp))
  {:foo 123 :bar 456 :baz 789})
