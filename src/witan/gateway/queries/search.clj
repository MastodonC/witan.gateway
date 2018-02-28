(ns witan.gateway.queries.search
  (:require [witan.gateway.queries.utils :refer [directory-url user-header error-response]]
            [clj-http.client :as http]
            [witan.gateway.queries.datastore :as ds]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))

(defn minimal-search
  [u d {:keys [search-term
               from]
        :as search
        :or {from 0
             search-term ""}}]
  (let [search-url (directory-url :search d)]
    (let [response (http/post (str search-url "metadata")
                              {:body (json/generate-string {:query {:kixi.datastore.metadatastore.query/name {:match search-term}}
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
                                                            :from from})
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
  (minimal-search u d search))

(defn datapack-files
  [u d search]
  (minimal-search u d search))

(defn datapack-files-expand
  [u d search]
  (minimal-search u d search))
