(ns witan.gateway.queries.utils)

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
