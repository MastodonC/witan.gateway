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
            [me.raynes.fs :as fs]))

(def system (atom nil))
(def file-id (atom nil))
(def auth-token (atom nil))
(def test-file-contents (str "hello, world " (uuid)))
(def test-file-metadata {:kixi.datastore.metadatastore/name "Test File"
                         :kixi.datastore.metadatastore/description "Test Description"
                         :kixi.datastore.metadatastore/file-type "txt"})

(use-fixtures :once
  (partial cycle-system-fixture system)
  (partial upload-file system file-id auth-token test-file-metadata test-file-contents))

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
  (println "Downloading" @file-id (download-url @file-id))
  (loop [tries 10]
    (let [r (http/get (download-url @file-id)
                      {:throw-exceptions false
                       :cookies {"token" {:discard true, :path "/", :value @auth-token, :version 0}}
                       :follow-redirects false
                       :redirect-strategy :none})]
      (if (or (= 302 (:status r))
              (<= tries 0))
        (do
          (is (= 302 (:status r)) (pr-str r))
          (when-let [redirect (get-in r [:headers "Location"])]
            (if (clojure.string/starts-with? redirect "file:")
              (is (= test-file-contents (slurp-from-docker (subs redirect 7))))
              (is (= test-file-contents (slurp redirect))))))
        (do
          (Thread/sleep 500)
          (log/info "Download test failed. Trying" (dec tries) "more time(s)...")
          (recur (dec tries)))))))
