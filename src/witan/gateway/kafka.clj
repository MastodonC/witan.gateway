(ns witan.gateway.kafka
  (:require [witan.gateway.protocols    :refer [SendMessage]]
            [com.stuartsierra.component :as component]
            [clojure.core.async         :as async]
            [clj-kafka.producer         :as kafka]
            [clj-kafka.zk               :as zk]
            [taoensso.timbre            :as log]
            [cheshire.core              :as json]))

(def zk-server "127.0.0.1:2181")

(defrecord KafkaProducer [topic]
  SendMessage
  (send-message! [component raw-message]
    (let [message (json/generate-string raw-message)]
      (if-let [{:keys [topic connection]} component]
        (if-let [error (kafka/send-message connection (kafka/message (name topic) (.getBytes message)))]
          (log/error "Failed to send message to Kafka:" error)
          (log/debug "Message was sent to Kafka:" topic message))
        (log/error "There is no connection to Kafka."))))

  component/Lifecycle
  (start [component]
    (log/info "Starting Kafka producer...")
    (log/info "Building broker list from ZooKeeper:" zk-server)
    (let [broker-string (->>
                         (zk/brokers {"zookeeper.connect" zk-server})
                         (map (juxt :host :port))
                         (map (partial interpose \:))
                         (map (partial apply str))
                         (interpose \,)
                         (apply str))
          _ (log/debug "Broker list" broker-string)
          connection (kafka/producer {"metadata.broker.list" broker-string
                                      "serializer.class" "kafka.serializer.DefaultEncoder"
                                      "partitioner.class" "kafka.producer.DefaultPartitioner"})]
      (->
       component
       (assoc :connection connection)
       (assoc :topic topic))))
  (stop [component]
    (log/info "Stopping Kafka producer...")
    (assoc component :connection nil)))

(defn new-kafka-producer
  [topic]
  (map->KafkaProducer {:topic topic}))
