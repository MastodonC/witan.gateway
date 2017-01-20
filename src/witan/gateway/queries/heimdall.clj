(ns witan.gateway.queries.heimdall
  (:require [taoensso.timbre :as log]
            [org.httpkit.client :as http]
            [cheshire.core :as json]))

(defn heimdall-url
  [s & params]
  (apply str "http://"
         (get-in s [:heimdall :host]) ":"
         (get-in s [:heimdall :port]) "/"
         (clojure.string/join "/" params)))

(defn get-elements
  [{:keys [kixi.user/id kixi.user/groups]} system-map method users]
  (let [url (heimdall-url system-map method)
        resp @(http/get url {:query-params {:id users}
                             :headers {"user-groups" (clojure.string/join "," groups)
                                       "user-id" id}})]
    (if (= 200 (:status resp))
      (:body (update resp
                     :body
                     #(when %
                        (json/parse-string % keyword))))
      {:error (str "invalid status: " (:status resp))})))

(defn get-user-info
  [{:keys [kixi.user/id kixi.user/groups]} system-map method user-id]
  (let [url (heimdall-url system-map "user" user-id)
        resp @(http/get url {:headers {"user-groups" (clojure.string/join "," groups)
                                       "user-id" id}})]
    (if (= 200 (:status resp))
      (:body (update resp
                     :body
                     #(when %
                        (json/parse-string % keyword))))
      {:error (str "invalid status: " (:status resp))})))

(defn get-users-info
  [u d users]
  (get-elements u d "users" users))

(defn get-groups-info
  [u d groups]
  (get-elements u d "groups" groups))
