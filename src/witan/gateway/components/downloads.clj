(ns witan.gateway.components.downloads
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [clojure.core.async :as async :refer [chan go go-loop put! <! <!! close!]]
            [witan.gateway.protocols    :as p :refer [ManageDownloads]]
            [kixi.comms :as c]))

(defn on-download-link
  [this args]
  (let [{:keys [kixi.comms.event/payload]} args
        {:keys [kixi.datastore.metadatastore/link
                kixi.datastore.metadatastore/id]} payload
        pd (:pending-downloads this)
        user-id (get-in payload [:kixi/user :kixi.user/id])
        ch (get-in @pd [user-id id])]
    (when ch
      (when link
        (put! ch link))
      (close! ch)
      (swap! pd update user-id #(dissoc % id)))
    nil))

(defrecord DownloadManager [timeout]
  ManageDownloads
  (create-download-redirect [{:keys [pending-downloads comms]} user file-id]
    (log/info "Attempting to redirect download for user"
              (:kixi.user/id user) "to file" file-id)
    (let [download-chan (chan 1)]
      (swap! pending-downloads update (:kixi.user/id user) #(assoc % file-id download-chan))
      (c/send-command! comms :kixi.datastore.filestore/create-download-link "1.0.0"
                       user
                       {:kixi.datastore.metadatastore/id file-id})
      (let [[v p] (async/alts!! [download-chan (async/timeout (or timeout 10000))])]
        (when (= p download-chan)
          v))))

  component/Lifecycle
  (start [{:keys [comms] :as component}]
    (log/info "Starting Download Manager")
    (let [cp (assoc component :pending-downloads (atom {}))
          event-handlers [(c/attach-event-handler! comms :download-manager-link-success
                                                   :kixi.datastore.filestore/download-link-created "1.0.0"
                                                   (partial on-download-link cp))
                          (c/attach-event-handler! comms :download-manager-link-failure
                                                   :kixi.datastore.filestore/download-link-rejected "1.0.0"
                                                   (partial on-download-link cp))]]
      (assoc cp :event-handlers event-handlers)))

  (stop [{:keys [comms] :as component}]
    (log/info "Stopping Download Manager")
    (run! (partial c/detach-handler! comms) (:event-handlers component))
    (-> component
        (dissoc :pending-downloads) ;; Do we need to close any open chans?
        (dissoc :event-handlers))))

(defn new-download-manager
  [config]
  (->DownloadManager (:timeout config)))
