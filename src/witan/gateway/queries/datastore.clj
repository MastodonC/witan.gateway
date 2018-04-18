(ns witan.gateway.queries.datastore
  (:require [taoensso.timbre :as log]
            [clj-http.client :as http]
            [medley :refer [map-vals]]
            ;;
            [witan.gateway.queries.utils :refer [directory-url user-header error-response]]
            [witan.gateway.queries.heimdall :as heimdall]))

(defn encode-kw
  [kw]
  (str (namespace kw)
       "_"
       (name kw)))

(defn update-items
  [body fnc]
  (update body :items (fn [items] (vec (keep fnc items)))))

(defn error
  [msg]
  (log/error msg)
  nil)

(defn expand-provenance-user
  [u d item]
  (let [prov-user-id
        (get-in item [:kixi.datastore.metadatastore/provenance
                      :kixi.user/id])
        user-info (heimdall/get-user-info u d prov-user-id)]
    (if (:error user-info)
      (error (format "Heimdall failed to return user information for %s: %s"
                     prov-user-id
                     user-info))
      (update item
              :kixi.datastore.metadatastore/provenance
              #(-> %
                   (assoc :kixi/user (first (:items user-info)))
                   (dissoc :kixi.user/id))))))

(defn expand-sharing
  [u d item]
  (update item
          :kixi.datastore.metadatastore/sharing
          #(map-vals
            (fn get-group-data
              [groups]
              (mapv (comp first :items
                          (partial heimdall/get-group-info u d))
                    groups))
            %)))

(defn expand-metadata
  [u d item]
  (->> item
       (expand-provenance-user u d)
       (expand-sharing u d)))

(defn get-file
  [u d id]
  (http/get (directory-url :datastore d "metadata" id)
            {:content-type :transit+json
             :accept :transit+json
             :throw-exceptions false
             :as :transit+json
             :headers (user-header u)}))

(defn expand-bundled-ids
  [u d {:keys [kixi.datastore.metadatastore/bundled-ids] :as body}]
  (if bundled-ids
    (assoc body :kixi.datastore.metadatastore/bundled-files
           (into {}
                 (pmap #(hash-map
                         %
                         (let [resp (get-file u d %)]
                           (if (= 200 (:status resp))
                             (expand-metadata u d (:body resp))
                             (error-response "datastore expand-bundled-ids" resp false)))) bundled-ids)))
    body))

(defn expand-metadatas
  [u d body]
  (update-items body (partial expand-metadata u d)))

;; kixi.datastore.metadatastore

(defn metadata-by-id
  [u d meta-id & _]
  (let [resp (get-file u d meta-id)]
    (if (= 200 (:status resp))
      (let [body (:body resp)]
        (->> body
             (expand-metadata u d)
             (expand-bundled-ids u d)))
      (error-response "datastore metadata-by-id" resp false))))

(defn metadata-with-activities
  "List file metadata with *this* activities set."
  [u d activities opts & _]
  (let [url (directory-url :datastore d "metadata")
        {:keys [index count]
         :or {index 0
              count 10}} opts
        resp (http/get url {:content-type :transit+json
                            :accept :transit+json
                            :throw-exceptions false
                            :as :transit+json
                            :query-params {:activity (mapv encode-kw activities)
                                           :index index
                                           :count count}
                            :headers (user-header u)})]
    (if (= 200 (:status resp))
      (let [body (:body resp)]
        (expand-metadatas u d body))
      (error-response "datastore metadata-with-activities" resp))))
