(ns witan.gateway.protocols)

(defprotocol SendMessage
  (send-message! [this message]))
