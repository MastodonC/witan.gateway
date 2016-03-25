(ns witan.gateway.command
  (:require [schema.core :as s]
            [ring.util.http-response :refer [accepted]]
            [witan.gateway.protocols :as p]))

(s/defschema POST
  {:command s/Str
   (s/optional-key :params) s/Any
   (s/optional-key :version) s/Str})

(s/defschema Receipt
  {:receipt s/Uuid})

(defn receive-command!
  [{:keys [command version] :as payload} kafka req origin]
  (let [now (java.util.Date.)
        id (java.util.UUID/randomUUID)
        updated-payload
        (-> payload
            (assoc :command (keyword command))
            (assoc :version (or version "tbd"))
            (assoc :id id)
            (assoc :origin origin)
            (assoc :handled-by (:server-name req))
            (assoc :received-at now))]
    (p/send-message! kafka :command updated-payload)
    (accepted {:receipt id})))
