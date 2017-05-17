(ns witan.gateway.queries.datastore
  (:require [taoensso.timbre :as log]
            [clj-http.client :as http]
            ;;
            [witan.gateway.queries.utils :refer [directory-url user-header]]
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

(defn expand-metadatas
  [u d body]
  (update-items body (partial expand-metadata u d)))

;; kixi.datastore.metadatastore

(defn metadata-by-id
  [u d meta-id & _]
  (let [url (directory-url :datastore d "metadata" meta-id)
        resp @(http/get url {:content-type :transit+json
                             :accept :transit+json
                             :throw-exceptions false
                             :as :transit+json
                             :headers (user-header u)})]
    (if (= 200 (:status resp))
      (let [body (:body resp)]
        (expand-metadata u d body))
      {:error (str "invalid status: " (:status resp))})))

(defn metadata-with-activities
  "List file metadata with *this* activities set."
  [u d activities & _]
  (let [url (directory-url :datastore d "metadata")
        resp @(http/get url {:content-type :transit+json
                             :accept :transit+json
                             :throw-exceptions false
                             :as :transit+json
                             :query-params (merge {:activity
                                                   (mapv encode-kw activities)}
                                                  #_(when index
                                                      {:index index})
                                                  #_(when count
                                                      {:count count}))
                             :headers (user-header u)})]
    (log/info ">>>>>>>>>>>>>>>>" resp)
    (if (= 200 (:status resp))
      (let [body (:body resp)]
        (expand-metadatas u d body))
      {:error (str "invalid status: " (:status resp))})))
