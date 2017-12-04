(ns witan.gateway.queries.utils
  (:require [taoensso.timbre :as log]))

(defn directory-url
  [k s & params]
  (apply str "http://"
         (get-in s [k :host]) ":"
         (get-in s [k :port]) "/"
         (clojure.string/join "/" params)))

(defn user-header
  [{:keys [kixi.user/id kixi.user/groups] :as u}]
  {"user-groups" (clojure.string/join "," groups)
   "user-id" id})

(defn error-response
  [msg {:keys [status body] :as resp}]
  (log/error "An error response was generated:" msg resp)
  {:error (str "Invalid status: " status)
   :error-info {:msg msg}})
