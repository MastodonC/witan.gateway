(ns witan.gateway.queries.heimdall
  (:require [taoensso.timbre :as log]
            [org.httpkit.client :as http]
            [cheshire.core :as json]
            [witan.gateway.queries.utils :refer [directory-url user-header]]))

(defn get-elements
  [u d method elements]
  (let [url (directory-url :heimdall d method)
        resp @(http/get url {:query-params {:id elements}
                             :headers (user-header u)})]
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

(defn group-search
  [u d]
  (let [url (directory-url :heimdall d "groups" "search")
        resp @(http/get url {:headers (user-header u)})]
    (if (= 200 (:status resp))
      (:body (update resp
                     :body
                     #(when %
                        (json/parse-string % keyword))))
      {:error (str "invalid status: " (:status resp))})))
