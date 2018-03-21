(ns witan.gateway.queries.search
  (:require [witan.gateway.queries.utils :refer [directory-url user-header error-response]]
            [clj-http.client :as http]
            [witan.gateway.queries.datastore :as ds]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))

(defn execute-search
  [u d search]
  (let [search-url (directory-url :search d)]
    (let [response (http/post (str search-url "metadata")
                              {:body (json/generate-string search)
                               :accept :json
                               :throw-exceptions false
                               :as :json
                               :headers (user-header u)})]
      (if (= 200 (:status response))
        {:search search
         :items (mapv (partial ds/expand-metadata u d)
                      (get-in response [:body :items]))
         :paging (get-in response [:body :paging])}
        (error-response "search execute search" response)))))

(defn metadata-by-id
  [u d id]
  (let [search-url (directory-url :search d "metadata" id)
        response (http/get search-url
                           {:content-type :json
                            :accept :json
                            :throw-exceptions false
                            :as :json
                            :headers (user-header u)})]
    (if (= 200 (:status response))
      (ds/expand-metadata u d (:body response))
      (error-response "search metadata by id" response))))
