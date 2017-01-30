(ns witan.gateway.components.downloads
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [clojure.core.async :as async :refer [chan go go-loop put! <! <!! close!]]
            [witan.gateway.protocols    :as p :refer [ManageDownloads]]
            [kixi.comms :as c]))

(defn handle-download-event-fn [ch]
  (fn [event]
    (when (or (= :kixi.datastore.filestore/download-link-created (:kixi.comms.event/key event))
              (= :kixi.datastore.filestore/download-link-rejected (:kixi.comms.event/key event))))
    (let [{:keys [kixi.comms.event/payload]} event
          {:keys [kixi.datastore.metadatastore/link
                  kixi.datastore.metadatastore/id]} payload]
      (when link
        (put! ch link))
      (close! ch)
      nil)))

(defrecord DownloadManager [timeout]
  ManageDownloads
  (create-download-redirect [{:keys [comms events]} user file-id]
    (log/info "Attempting to redirect download for user"
              (:kixi.user/id user) "to file" file-id)
    (let [download-chan (chan 1)
          dlfn (handle-download-event-fn download-chan)]
      (p/register-event-receiver! events dlfn)
      (c/send-command! comms :kixi.datastore.filestore/create-download-link "1.0.0"
                       user
                       {:kixi.datastore.metadatastore/id file-id})
      (let [[v p] (async/alts!! [download-chan (async/timeout (or timeout 10000))])]
        (p/unregister-event-receiver! events dlfn)
        (when (= p download-chan)
          v))))

  component/Lifecycle
  (start [{:keys [comms] :as component}]
    (log/info "Starting Download Manager")
    component)

  (stop [{:keys [comms] :as component}]
    (log/info "Stopping Download Manager")
    component))

(defn new-download-manager
  [config]
  (->DownloadManager (:timeout config)))
