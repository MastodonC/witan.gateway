(ns witan.gateway.integration.downloads-test
  (:require [clojure.test :refer :all]
            [witan.gateway.protocols :as p]
            [witan.gateway.integration.base :refer :all]
            [witan.gateway.handler :refer [transit-encode transit-decode]]
            [kixi.comms :as c]
            [kixi.comms.time :refer [timestamp]]
            [buddy.core.keys            :as keys]
            [buddy.sign.jwt             :as jwt]
            [taoensso.timbre :as log]
            [clj-http.client :as http]
            [clj-time.core :as t]
            [me.raynes.fs :as fs]
            [clojure.java.shell :refer [sh]]))

(def system (atom nil))
(def file-id (atom nil))
(def auth-token (atom nil))
(def test-file-contents (str "hello, world " (uuid)))

(defn test-login
  [auth]
  (let [tkp (-> "http://localhost:30015/login"
                (http/post
                 {:body "{\"username\": \"test@mastodonc.com\", \"password\": \"Secret123\"}"})
                :body
                (transit-decode))]
    (reset! auth-token (get-in tkp [:token-pair :auth-token]))
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
  [comms user file-contents {:keys [kixi.comms.event/payload]}]
  (let [{:keys [kixi.datastore.filestore/upload-link
                kixi.datastore.filestore/id]} payload
        metadata {:kixi.datastore.metadatastore/name "Test File"
                  :kixi.datastore.metadatastore/description "Test Description"
                  :kixi.datastore.metadatastore/id id
                  :kixi.datastore.metadatastore/type "stored"
                  :kixi.datastore.metadatastore/file-type "txt"
                  :kixi.datastore.metadatastore/sharing
                  {:kixi.datastore.metadatastore/meta-read (:kixi.user/groups user)
                   :kixi.datastore.metadatastore/meta-update (:kixi.user/groups user)
                   :kixi.datastore.metadatastore/file-read (:kixi.user/groups user)}
                  :kixi.datastore.metadatastore/provenance
                  {:kixi.datastore.metadatastore/source "upload"
                   :kixi.user/id (:kixi.user/id user)}
                  :kixi.datastore.metadatastore/size-bytes (count file-contents)
                  :kixi.datastore.metadatastore/header false}
        tmpfile (fs/temp-file (str "gateway-download-test-" id "_"))]
    (spit tmpfile file-contents)
    (log/info "Uploading test file to" upload-link)
    (if (clojure.string/starts-with? upload-link "file:")
      (cp-to-docker tmpfile (subs upload-link 7))
      (put-to-aws tmpfile upload-link))
    (Thread/sleep 300)
    (c/send-command! comms :kixi.datastore.filestore/create-file-metadata "1.0.0" user metadata)
    nil))

(defn upload-file
  [system file-id-atom all-tests]
  (let [{:keys [comms auth]} @system
        user (test-login auth)
        _ (log/info "Result of test login:" user)
        ehs [(c/attach-event-handler!
              comms
              :download-test-upload-link-created
              :kixi.datastore.filestore/upload-link-created "1.0.0"
              (partial upload-file-to-correct-location comms user test-file-contents))
             (c/attach-event-handler!
              comms
              :download-test-file-metadata-rejected
              :kixi.datastore.file-metadata/rejected "1.0.0"
              (fn [payload]
                (log/error "Download test file was rejected:" payload) nil))
             (c/attach-event-handler!
              comms
              :download-test-file-metadata-created
              :kixi.datastore.file/created "1.0.0"
              (fn [{:keys [kixi.comms.event/payload]}]
                (let [{:keys [kixi.datastore.metadatastore/id]} payload]
                  (reset! file-id-atom id))
                nil))]]
    _ (log/info "Handlers attached." )
    (c/send-command! comms :kixi.datastore.filestore/create-upload-link "1.0.0" user nil)
    _ (log/info "Command sent: :kixi.datastore.filestore/create-upload-link" )
    (wait-for-pred #(deref file-id-atom))
    (run! (partial c/detach-handler! comms) ehs)
    (if-not (clojure.string/blank? @file-id-atom)
      (all-tests)
      (throw (Exception. "Tests could not be run")))))

(use-fixtures :once
  (partial cycle-system-fixture system)
  (partial upload-file system file-id))

(defn download-url
  ([]
   "http://localhost:30015/download")
  ([id]
   (str (download-url) "?id=" id)))

(deftest download-without-token-or-query
  (let [r (http/get (download-url)
                    {:throw-exceptions false})]
    (is (= 401 (:status r)))))

(deftest download-without-token
  (let [r (http/get (download-url @file-id)
                    {:throw-exceptions false})]
    (is (= 401 (:status r)))))

(deftest download
  (let [r (http/get (download-url @file-id)
                    {:throw-exceptions false
                     :cookies {"token" {:discard true, :path "/", :value @auth-token, :version 0}}
                     :follow-redirects false})]
    (is (= 302 (:status r)) (pr-str r))
    (when-let [redirect (get-in r [:headers "Location"])]
      (if (clojure.string/starts-with? redirect "file:")
        (is (= test-file-contents (slurp-from-docker (subs redirect 7))))
        (is (= test-file-contents (slurp redirect)))))))
