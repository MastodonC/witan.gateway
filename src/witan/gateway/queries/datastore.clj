(ns witan.gateway.queries.datastore
  (:require [taoensso.timbre :as log]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            ;;
            [witan.gateway.queries.heimdall :as heimdall]))

(defn datastore-url
  [s & params]
  (apply str "http://"
         (get-in s [:datastore :host]) ":"
         (get-in s [:datastore :port]) "/"
         (clojure.string/join "/" params)))

(def data-fields
  [:foo :bar])

(defn encode-kw
  [kw]
  (str (namespace kw)
       "_"
       (name kw)))

(defn update-items
  [body fnc]
  (update body :items (fn [items] (map fnc items))))

(defn expand-metadata
  [u d body]
  (update-items
   body
   (fn [item]
     (let [prov-user-id
           (get-in item [:kixi.datastore.metadatastore/provenance
                         :kixi.user/id])
           user-info (heimdall/get-user-info u d prov-user-id)
           collected-groups (->>
                             (:kixi.datastore.metadatastore/sharing item)
                             (vals)
                             (reduce concat)
                             (set)
                             (vec))
           group-info (->> collected-groups
                           (heimdall/get-groups-info u d)
                           (reduce (fn [a m] (assoc a (:kixi.group/id m) m)) {}))]
       (-> item
           (assoc-in [:kixi.datastore.metadatastore/provenance
                      :kixi/user] user-info)
           (update :kixi.datastore.metadatastore/provenance dissoc :kixi.user/id)
           (update :kixi.datastore.metadatastore/sharing
                   (fn [x]
                     (reduce-kv (fn [a k vs]
                                  (assoc a k (mapv #(get group-info %) vs))) {} x))))))))

;; kixi.datastore.metadatastore

(defn metadata-by-id
  [{:keys [kixi.user/id kixi.user/groups] :as u} d meta-id]
  (let [url (datastore-url d "metadata" meta-id)
        resp @(http/get url {:headers {"user-groups" (clojure.string/join "," groups)
                                       "user-id" id}})]
    (if (= 200 (:status resp))
      (let [body (:body (update resp
                                :body
                                #(when %
                                   (json/parse-string % keyword))))]
        (expand-metadata u d body))
      {:error (str "invalid status: " (:status resp))})))

(defn metadata-with-activities
  "List file metadata with *this* activities set."
  [{:keys [kixi.user/id kixi.user/groups] :as u} d activities]
  (let [url (datastore-url d "metadata")
        resp @(http/get url {:query-params (merge {:activity
                                                   (mapv encode-kw activities)}
                                                  #_(when index
                                                      {:index index})
                                                  #_(when count
                                                      {:count count}))
                             :headers {"user-groups" (clojure.string/join "," groups)
                                       "user-id" id}})]
    (if (= 200 (:status resp))
      (let [body (:body (update resp
                                :body
                                #(when %
                                   (json/parse-string % keyword))))]
        (expand-metadata u d body))
      {:error (str "invalid status: " (:status resp))})))
