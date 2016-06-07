(ns witan.gateway.protocols)

(defprotocol SendMessage
  (send-message! [this topic message]))

;;;;;;;;;

(defprotocol ManageReceipts
  (store-event! [this event])
  (check-receipt [this receipt]))

;;;;;;;;;

(defprotocol RouteQuery
  (route-query [this route params]))

;;;;;;;;;

(defprotocol Database
  (drop-table! [this table])
  (create-table! [this table columns])
  (insert! [this table row args])
  (select [this table where]))
