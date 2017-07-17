(ns witan.gateway.integration.base
  (:require [user :as repl]
            [environ.core :refer [env]]
            [gniazdo.core :as ws]
            [witan.gateway.handler :refer [transit-encode transit-decode]]
            [taoensso.timbre :as log]
            [buddy.core.keys            :as keys]
            [buddy.sign.jwt             :as jwt]
            [clojure.core.async :refer :all]
            [clj-http.client :as http]
            [witan.gateway.protocols :as p]
            [clj-time.core :as t]
            [clojure.java.shell :refer [sh]]
            [me.raynes.fs :as fs]))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(def wait-tries (Integer/parseInt (env :wait-tries "120")))
(def wait-ms (Integer/parseInt (env :wait-ms "500")))

(def public-key (env :super-secret-public-pem-file
                     "./test-resources/heimdall-dev_pubkey.pem"))
(defn sign
  [payload]
  (let [prvk
        (keys/private-key
         (env :super-secret-pem-file "./test-resources/heimdall-dev_privkey.pem")
         (env :super-secret-password "test"))]
    (jwt/sign payload prvk {:alg :rs256})))

(defn cycle-system-fixture
  [a all-tests]
  (reset! a (repl/go))
  (Thread/sleep 2000)
  (try
    (all-tests)
    (finally
      (repl/stop)
      (reset! a nil))))

(defn create-ws-connection
  [a received-fn all-tests]
  (let [run-tests-ch (chan)
        clean (fn []
                (log/info "Closing websocket...")
                (ws/close @a)
                (reset! a nil))]
    (log/info "Opening websocket...")
    (reset! a (ws/connect "ws://localhost:30015/ws"
                          :on-connect (fn [& _]
                                        (log/info "Websocket on-connect called.")
                                        (put! run-tests-ch true))
                          :on-receive #(when @received-fn
                                         (log/info "Websocket received something!")
                                         (@received-fn (transit-decode %)))))
    (alt!!
      run-tests-ch    (do
                        (log/info "Running all tests...")
                        (all-tests)
                        (clean))
      (timeout 10000) (do
                        (log/error "Websocket connection timed out.")
                        (clean))) identity))

(defn wait-for-pred
  ([p]
   (wait-for-pred p wait-tries))
  ([p tries]
   (wait-for-pred p tries wait-ms))
  ([p tries ms]
   (log/info "Waiting for predicate -" tries "x" ms)
   (loop [try tries]
     (when (and (pos? try) (not (p)))
       (Thread/sleep ms)
       (recur (dec try))))))

(defn test-login
  [auth auth-token-atom]
  (let [tkp (-> "http://localhost:30015/login"
                (http/post
                 {:body "{\"username\": \"test@mastodonc.com\", \"password\": \"Secret123\"}"})
                :body
                (transit-decode))]
    (reset! auth-token-atom (get-in tkp [:token-pair :auth-token]))
    (p/authenticate auth (t/now) (get-in tkp [:token-pair :auth-token]))))

(defn find-datastore-docker-id
  []
  (let [ds-line (first
                 (filter (partial re-find #"datastore")
                         (rest (clojure.string/split (:out (sh "docker" "ps"))
                                                     (re-pattern (str \newline))))))]
    (re-find #"[a-zA-Z0-9]+" ds-line)))

(defn cp-to-docker
  [tmpfile upload-link]
  (let [sh-line ["docker" "cp" (str tmpfile) (str (find-datastore-docker-id) ":" upload-link)]]
    (println (apply sh sh-line))))

(defn slurp-from-docker
  [file]
  (let [tmpfile (fs/temp-file "gateway-download-slurp-test-")
        sh-line ["docker" "cp" (str (find-datastore-docker-id) ":" file) (str tmpfile)]]
    (println (apply sh sh-line) sh-line)
    (slurp tmpfile)))

(defn put-to-aws
  [tmpfile upload-link]
  (http/put upload-link {:body tmpfile}))
