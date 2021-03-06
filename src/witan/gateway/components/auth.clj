(ns witan.gateway.components.auth
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [clj-time.core              :as t]
            [clj-time.coerce            :as ct]
            [clojure.spec.alpha               :as s]
            [kixi.comms                 :as c]
            [buddy.core.keys            :as keys]
            [buddy.sign.jwt             :as jwt]
            [witan.gateway.protocols    :as p :refer [Authenticate]]))

(defrecord Authenticator [pubkey]
  Authenticate
  (authenticate [this time auth-token]
    (when auth-token
      (try
        (let [pk (:loaded-pubkey this)
              auth-payload (jwt/unsign auth-token pk {:alg :rs256})
              expiry (-> auth-payload :exp ct/from-long)]
          (if (t/before? time expiry)
            {:kixi.user/id (:id auth-payload)
             :kixi.user/groups (-> (conj (:user-groups auth-payload)
                                         (:self-group auth-payload))
                                   (set)
                                   (vec))}
            (throw (Exception. "Auth token has expired"))))
        (catch Exception e (log/warn e "Failed to unsign an auth token:")))))

  component/Lifecycle
  (start [component]
    (log/info "Starting Authenticator")
    (let [pk (keys/public-key pubkey)]
      (assoc component :loaded-pubkey pk)))

  (stop [component]
    (log/info "Stopping Authenticator")
    (dissoc component :loaded-pubkey)))

(defn new-authenticator
  [args]
  (map->Authenticator args))
