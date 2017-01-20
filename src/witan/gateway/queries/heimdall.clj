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
  [{:keys [kixi.user/id kixi.user/groups]} system-map method elements]
  (let [url (heimdall-url system-map method)
        resp @(http/get url {:query-params {:id elements}
                             :headers {"user-groups" (clojure.string/join "," groups)
                                       "user-id" id}})]
    (if (= 200 (:status resp))
      (:body (update resp
                     :body
                     #(when %
                        (json/parse-string % keyword))))
      {:error (str "invalid status: " (:status resp))})))

(defn get-users-info
  [u d users]
  (get-elements u d "users" (vec users)))

(defn get-groups-info
  [u d groups]
  (get-elements u d "groups" (vec groups)))
