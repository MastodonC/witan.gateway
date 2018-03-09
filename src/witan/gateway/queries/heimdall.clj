(ns witan.gateway.queries.heimdall
  (:require [taoensso.timbre :as log]
            [org.httpkit.client :as http]
            [clojure.core.memoize :refer [fifo]]
            [witan.gateway.handler :refer [transit-encode transit-decode]]
            [witan.gateway.queries.utils :refer [directory-url user-header error-response]]))

(defn get-elements
  [u d method elements & _]
  (let [url (directory-url :heimdall d method)
        resp @(http/get url {:query-params {:id elements}
                             :headers (user-header u)})]
    (if (= 200 (:status resp))
      (:body (update resp
                     :body
                     #(when %
                        (transit-decode %))))
      (error-response "heimdall get-elements" resp))))

(defn get-users-info
  [u d users & _]
  (get-elements u d "users" (vec users)))

(defn get-groups-info
  [u d groups & _]
  (get-elements u d "groups" (vec groups)))

(defn get-user-info-
  [u d user-id]
  (get-elements u d "users" [user-id]))

(def user-info-cache-size 100)

(def get-user-info
  (fifo get-user-info-
        :fifo/threshold
        user-info-cache-size))

(def group-info-cache-size 100)

(defn get-group-info-
  [u d group-id]
  (get-elements u d "groups" [group-id]))

(def get-group-info
  (fifo get-group-info-
        :fifo/threshold
        group-info-cache-size))

(defn group-search
  [u d & _]
  (let [url (directory-url :heimdall d "groups" "search")
        resp @(http/get url {:headers (user-header u)})]
    (if (= 200 (:status resp))
      (:body (update resp
                     :body
                     #(when %
                        (transit-decode %))))
      (error-response "heimdall group-search" resp))))
