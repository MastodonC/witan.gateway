(ns witan.gateway.components.receipts
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [ring.util.http-response    :refer [ok not-found]]
            [witan.gateway.protocols    :as p :refer [ManageReceipts Database]]
            [cheshire.core              :as json]
            [schema.core                :as s]))

(def table-name :receipts)

(s/defschema Status
  {:status s/Keyword
   :receipt s/Uuid
   (s/optional-key :event) {:key s/Keyword
                            :id s/Uuid
                            (s/optional-key :resource-id) s/Uuid}
   (s/optional-key :error) s/Str})

(defrecord ReceiptManager [db]
  ManageReceipts
  (store-event! [this event]
    (log/debug "Storing event:" event)
    (try
      (let [receipt (or (get-in event [:command :id]) (get event :id))]
        (p/insert! db table-name {:receipt (java.util.UUID/fromString receipt)
                                  :json (json/generate-string event)} {:using [:ttl 300]}))
      (catch Exception e (log/error "Failed to store the event:" e))))

  (check-receipt [this receipt]
    (log/debug "Checking receipt:" receipt)
    (if-let [event (first (not-empty (p/select db table-name {:receipt receipt})))]
      (let [{:keys [event id params error] :as parsed} (json/parse-string (:json event) true)
            resp {:status (if error :error :ok)
                  :receipt receipt}
            resp (if-not error
                   (assoc resp :event {:key event :id id})
                   (assoc resp :error error))
            resp (if (and (not error) (:id params)) (update resp :event #(assoc % :resource-id (:id params))) resp)]
        (ok resp))
      (not-found {:status :not-found
                  :receipt receipt})))

  component/Lifecycle
  (start [component]
    (log/info "Starting Receipt Manager")
    (p/drop-table! db table-name)
    (p/create-table! db table-name {:json :text
                                    :receipt :uuid
                                    :primary-key :receipt})
    component)

  (stop [component]
    (log/info "Stopping Receipt Manager")
    component))

(defn new-receipt-manager []
  (map->ReceiptManager {}))
