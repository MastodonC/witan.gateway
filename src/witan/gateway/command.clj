(ns witan.gateway.command
  (:require [schema.core :as s]
            [witan.gateway.protocols :as p]))

(defn receive-command!
  [receipt {:keys [command/key command/version] :as payload} kafka]
  (p/send-message! kafka :command payload))
