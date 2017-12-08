(ns witan.gateway.queries.datastore
  (:require [taoensso.timbre :as log]
            [clj-http.client :as http]
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

(defn expand-metadata
  [u d item]
  (let [prov-user-id
        (get-in item [:kixi.datastore.metadatastore/provenance
                      :kixi.user/id])
        user-info (heimdall/get-users-info u d [prov-user-id])]
    (if (:error user-info)
      (error (format "Heimdall failed to return user information for %s: %s"
                     prov-user-id
                     user-info))
      (let [collected-groups (->>
                              (:kixi.datastore.metadatastore/sharing item)
                              (vals)
                              (reduce concat)
                              (set))
            group-resp (heimdall/get-groups-info u d collected-groups)]
        (if (:error group-resp)
          (error (format "Heimdall failed to return group information for %s: %s"
                         collected-groups
                         group-resp))
          (let [group-info (reduce (fn [a m] (assoc a (:kixi.group/id m) m))
                                   {}
                                   (:items group-resp))]
            (-> item
                (assoc-in [:kixi.datastore.metadatastore/provenance
                           :kixi/user] (first (:items user-info)))
                (update :kixi.datastore.metadatastore/provenance dissoc :kixi.user/id)
                (update :kixi.datastore.metadatastore/sharing
                        (fn expand-sharing [sharing]
                          (zipmap (keys sharing)
                                  (map #(->> (mapv group-info %)
                                             (keep identity)
                                             (vec)) (vals sharing))))))))))))

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
                             (error-response "datastore expand-bundled-ids" resp)))) bundled-ids)))
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
      (error-response "datastore metadata-by-id" resp))))

(defn metadata-with-activities
  "List file metadata with *this* activities set."
  [u d activities & _]
  (let [url (directory-url :datastore d "metadata")
        opts (when (map? (last activities)) (last activities))
        activities (if opts (butlast activities) activities)
        {:keys [index count]
         :or {index 0
              count 50}} opts
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
