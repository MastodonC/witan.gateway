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
            [me.raynes.fs :as fs]
            [kixi.comms :as c]))

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

(defn upload-file-to-correct-location
  [comms user file-meta file-contents {:keys [kixi.comms.event/payload] :as event}]
  (let [{:keys [kixi.datastore.filestore/upload-link
                kixi.datastore.filestore/id]} payload]
    (let [metadata (merge {:kixi.datastore.metadatastore/id id
                           :kixi.datastore.metadatastore/type "stored"
                           :kixi.datastore.metadatastore/sharing
                           {:kixi.datastore.metadatastore/meta-read (:kixi.user/groups user)
                            :kixi.datastore.metadatastore/meta-update (:kixi.user/groups user)
                            :kixi.datastore.metadatastore/file-read (:kixi.user/groups user)}
                           :kixi.datastore.metadatastore/provenance
                           {:kixi.datastore.metadatastore/source "upload"
                            :kixi.user/id (:kixi.user/id user)}
                           :kixi.datastore.metadatastore/size-bytes (count file-contents)
                           :kixi.datastore.metadatastore/header false}
                          file-meta)
          tmpfile (fs/temp-file (str "gateway-test-" id "_"))]
      (spit tmpfile file-contents)
      (log/info "Uploading test file to" upload-link)
      (if (clojure.string/starts-with? upload-link "file:")
        (cp-to-docker tmpfile (subs upload-link 7))
        (put-to-aws tmpfile upload-link))
      (Thread/sleep 300)
      (c/send-command! comms :kixi.datastore.filestore/create-file-metadata "1.0.0"
                       user metadata
                       {:kixi.comms.command/id (:kixi.comms.command/id event)})
      nil)))

(defn upload-file
  [system file-id-atom auth-token-atom test-file-metadata test-file-contents all-tests]
  (let [{:keys [comms auth]} @system
        user (test-login auth auth-token-atom)
        adjusted-comms (assoc-in comms [:consumer-config :auto.offset.reset] :latest)
        _ (log/info "Result of test login:" user)
        cid (uuid)
        ehs [(c/attach-event-handler!
              adjusted-comms
              :download-test-upload-link-created
              :kixi.datastore.filestore/upload-link-created "1.0.0"
              (fn [{:keys [kixi.comms.command/id] :as event}]
                (log/info "Event received: :kixi.datastore.filestore/upload-link-created. " event id cid)
                (when (= id cid)
                  (upload-file-to-correct-location
                   comms user
                   test-file-metadata
                   test-file-contents event))))
             (c/attach-event-handler!
              adjusted-comms
              :download-test-file-metadata-rejected
              :kixi.datastore.file-metadata/rejected "1.0.0"
              (fn [{:keys [kixi.comms.command/id] :as event}]
                (when (= id cid)
                  (log/error "Download test file was rejected:" event))))
             (c/attach-event-handler!
              adjusted-comms
              :download-test-file-metadata-created
              :kixi.datastore.file-metadata/updated "1.0.0"
              (fn [{:keys [kixi.comms.event/payload kixi.comms.command/id] :as event}]
                (when (= id cid)
                  (let [id (get-in payload [:kixi.datastore.metadatastore/file-metadata
                                            :kixi.datastore.metadatastore/id])]
                    (reset! file-id-atom id)))
                nil))]]
    (log/info "Handlers attached.")
    (c/send-command! comms :kixi.datastore.filestore/create-upload-link "1.0.0" user nil
                     {:kixi.comms.command/id cid
                      :origin "witan.gateway-test"})
    (log/info "Command sent: :kixi.datastore.filestore/create-upload-link with command ID" cid)
    (wait-for-pred #(deref file-id-atom))
    (run! (partial c/detach-handler! comms) ehs)
    (log/info "File ID:" @file-id-atom)
    (if-not (clojure.string/blank? @file-id-atom)
      (all-tests)
      (throw (Exception. "Tests could not be run")))))

(defn create-bundle
  [system bundle-id-atom auth-token-atom test-bundle-metadata test-bundle-ids all-tests]
  (let [{:keys [comms auth]} @system
        user (test-login auth auth-token-atom)
        adjusted-comms (assoc-in comms [:consumer-config :auto.offset.reset] :latest)
        _ (log/info "Result of test login:" user)
        cid (uuid)
        ehs [(c/attach-event-handler!
              adjusted-comms
              :create-bundle-file-metadata-created
              :kixi.datastore.file-metadata/updated "1.0.0"
              (fn [{:keys [kixi.comms.event/payload kixi.comms.command/id] :as event}]
                (when (= id cid)
                  (let [id (get-in payload [:kixi.datastore.metadatastore/file-metadata
                                            :kixi.datastore.metadatastore/id])]
                    (reset! bundle-id-atom id)))
                nil))
             (c/attach-event-handler!
              adjusted-comms
              :create-bundle-file-metadata-rejected
              :kixi.datastore.file-metadata/rejected "1.0.0"
              (fn [{:keys [kixi.comms.command/id] :as event}]
                (when (= id cid)
                  (log/error "Bundle was rejected:" event))))]]
    (log/info "Handlers attached.")
    (c/send-command! comms :kixi.datastore.filestore/create-datapack "1.0.0" user
                     (assoc test-bundle-metadata
                            :kixi.datastore.metadatastore/bundled-ids (map deref test-bundle-ids)
                            :kixi.datastore.metadatastore/provenance {:kixi.datastore.metadatastore/source "upload"
                                                                      :kixi.user/id (:kixi.user/id user)}
                            :kixi.datastore.metadatastore/sharing {:kixi.datastore.metadatastore/meta-read (:kixi.user/groups user)
                                                                   :kixi.datastore.metadatastore/meta-update (:kixi.user/groups user)
                                                                   :kixi.datastore.metadatastore/file-read (:kixi.user/groups user)})
                     {:kixi.comms.command/id cid
                      :origin "witan.gateway-test"})
    (log/info "Command sent: :kixi.datastore.filestore/create-datapack with command ID" cid)
    (wait-for-pred #(deref bundle-id-atom))
    (run! (partial c/detach-handler! comms) ehs)
    (log/info "Bundle ID:" @bundle-id-atom)
    (if-not (clojure.string/blank? @bundle-id-atom)
      (all-tests)
      (throw (Exception. "Tests could not be run")))))
