(ns witan.gateway.queries.datastore
  (:require [taoensso.timbre :as log]
            [org.httpkit.client :as http]
            [cheshire.core :as json]))

(defn datastore-url
  [s route]
  (str "http://"
       (get-in s [:datastore :host]) ":"
       (get-in s [:datastore :port]) route))

(def data-fields
  [:foo :bar])

(defn encode-kw
  [kw]
  (str (namespace kw)
       "_"
       (name kw)))

;; kixi.datastore.metadatastore

(defn metadata-with-activities
  "List file metadata with *this* activities set."
  [{:keys [kixi.user/id kixi.user/groups]} d activities]
  (let [url (datastore-url d "/metadata")
        resp @(http/get url {:query-params (merge (zipmap (repeat :activity)
                                                          (map encode-kw activities))
                                                  #_(when index
                                                      {:index index})
                                                  #_(when count
                                                      {:count count}))
                             :headers {"user-groups" (clojure.string/join "," groups)
                                       "user-id" id}})]
    (when (= 200 (:status resp))
      (let [contents (json/parse-string (:body resp) keyword)]
        contents))))
