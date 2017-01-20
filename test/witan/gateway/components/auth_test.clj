(ns witan.gateway.components.auth-test
  (:require [witan.gateway.components.auth :refer :all]
            [witan.gateway.integration.base :refer :all]
            [clj-time.core :as t]
            [clj-time.coerce :as ct]
            [witan.gateway.protocols :as p]
            [com.stuartsierra.component :as component]
            [clojure.test :refer :all]))

(def valid-auth-token
  (sign {:exp (ct/to-long (t/plus (t/now) (t/hours 1)))
         :id (uuid)
         :user-groups {:groups [(uuid)]}}))

(def expired-auth-token
  (sign {:exp (ct/to-long (t/minus (t/now) (t/hours 1)))
         :id (uuid)
         :user-groups {:groups [(uuid)]}}))

(def auth (component/start (->Authenticator public-key)))

(deftest auth-pass
  (is (p/authenticate auth (t/now) valid-auth-token)))

(deftest auth-fail-tampered
  (let [tampered-token (apply str (update (vec valid-auth-token)
                                          (long (rand (count valid-auth-token)))
                                          #(char (inc (int %)))))] ;; change one random character
    (is (not (p/authenticate auth (t/now) tampered-token)))))

(deftest auth-fail-expired
  (is (not (p/authenticate auth (t/now) expired-auth-token))))
