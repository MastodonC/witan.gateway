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
  (update body :items (fn [items] (vec (keep fnc items)))))

(defn error
  [msg]
  (log/error msg)
  nil)

(defn expand-metadata
  [u d body]
  (update-items
   body
   (fn expanding-item [item]
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
               group-resp (:items (heimdall/get-groups-info u d collected-groups))]
           (if (:error group-resp)
             (error (format "Heimdall failed to return group information for %s: %s"
                            collected-groups
                            group-resp))
             (let [group-info (reduce (fn [a m] (assoc a (:kixi.group/id m) m)) {} group-resp)]
               (-> item
                   (assoc-in [:kixi.datastore.metadatastore/provenance
                              :kixi/user] (first (:items user-info)))
                   (update :kixi.datastore.metadatastore/provenance dissoc :kixi.user/id)
                   (update :kixi.datastore.metadatastore/sharing
                           (fn expand-sharing [sharing]
                             (zipmap (keys sharing)
                                     (map (partial mapv group-info) (vals sharing))))))))))))))

b;; kixi.datastore.metadatastore

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
