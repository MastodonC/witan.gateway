(ns witan.gateway.integration.expansion-test
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
(def bundle-id (atom nil))
(def auth-token (atom nil))
(def test-file-contents (str "hello, bundle " (uuid)))
(def test-file-metadata {:kixi.datastore.metadatastore/name "Test Bundled File"
                         :kixi.datastore.metadatastore/description "Test Bundled Description"
                         :kixi.datastore.metadatastore/file-type "txt"})

(def test-bundle-metadata {:kixi.datastore.metadatastore/name "Test Bundle"
                           :kixi.datastore.metadatastore/id (uuid)
                           :kixi.datastore.metadatastore/type "bundle"
                           :kixi.datastore.metadatastore/bundle-type "datapack"})

(use-fixtures :once
  (partial cycle-system-fixture system)
  (partial upload-file system file-id auth-token test-file-metadata test-file-contents)
  (partial create-bundle system bundle-id auth-token test-bundle-metadata [file-id]))

(deftest something
  (log/info "Bundle test")
  (is (= 1 1)))
