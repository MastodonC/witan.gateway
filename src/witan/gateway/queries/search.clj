(ns witan.gateway.queries.search
  (:require [witan.gateway.queries.utils :refer [directory-url user-header error-response]]
            [clj-http.client :as http]
            [witan.gateway.queries.datastore :as ds]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))

(defn minimal-metadata-search
  [u d {:keys [search-term
               from
               metadata-type]
        :as search
        :or {from 0
             search-term ""}}]
  (let [search-url (directory-url :search d)]
    (let [response (http/post (str search-url "metadata")
                              {:body (json/generate-string
                                      {:query
                                       (merge {:kixi.datastore.metadatastore.query/name {:match search-term}}
                                              (when metadata-type
                                                {:kixi.datastore.metadatastore.query/type {:equals metadata-type}}))
                                       :fields [:kixi.datastore.metadatastore/name
                                                :kixi.datastore.metadatastore/id
                                                [:kixi.datastore.metadatastore/provenance
                                                 :kixi.datastore.metadatastore/created]
                                                [:kixi.datastore.metadatastore/provenance
                                                 :kixi.user/id]
                                                :kixi.datastore.metadatastore/type
                                                :kixi.datastore.metadatastore/file-type
                                                :kixi.datastore.metadatastore/license
                                                :kixi.datastore.metadatastore/size-bytes
                                                :kixi.datastore.metadatastore/sharing]
                                       :from from
                                       :size 50})
                               :content-type :json
                               :accept :json
                               :throw-exceptions false
                               :as :json
                               :headers (user-header u)})]
      (if (= 200 (:status response))
        {:search-term search-term
         :items (mapv (partial ds/expand-metadata u d)
                      (get-in response [:body :items]))
         :paging (get-in response [:body :paging])}
        (error-response "search minimal search" response)))))

(defn dashboard
  [u d search]
  ;;need to support the types here
  )

(defn datapack-files
  [u d search]
  (minimal-metadata-search u d
                           (assoc search
                                  :metadata-type "stored")))
