(ns witan.gateway.command
  (:require [schema.core :as s]
            [ring.util.http-response :refer [accepted
                                             internal-server-error]]
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
        (assoc payload
               :command (keyword command)
               :version (or version "tbd")
               :id id
               :origin origin
               :handled-by (:server-name req)
               :received-at now)]
    (if-let [err (p/send-message! kafka :command updated-payload)]
      (internal-server-error err)
      (accepted {:receipt id}))))
