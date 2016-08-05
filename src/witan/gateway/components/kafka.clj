(ns witan.gateway.components.kafka
  (:require [witan.gateway.protocols    :as p]
            [com.stuartsierra.component :as component]
            [taoensso.timbre            :as log]
            [cheshire.core              :as json]
            [clj-kafka.producer         :as kafka]
            [clj-kafka.zk               :as zk]
            [clj-kafka.consumer.zk      :as kafka-zk]
            [clj-kafka.core             :as kafka-core]
            [clojure.core.async         :as async :refer [go-loop chan <! close! put!]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Producer

(defrecord KafkaProducer [host port]
  p/SendMessage
  (send-message! [component topic raw-message]
    (let [message (json/generate-string raw-message)]
      (if-let [{:keys [connection]} component]
        (if-let [error (kafka/send-message connection (kafka/message (name topic) (.getBytes message)))]
          (do
            (log/error "Failed to send message to Kafka:" error)
            error)
          (log/debug "Message was sent to Kafka:" topic message))
        (do
          (log/error "There is no connection to Kafka.")
          "There is no connection to Kafka."))))

  component/Lifecycle
  (start [component]
    (log/info "Starting Kafka producer...")
    (log/info "Building broker list from ZooKeeper:" host port)
    (let [broker-string (->>
                         (zk/brokers {"zookeeper.connect" (str host ":" port)})
                         (map (juxt :host :port))
                         (map (partial interpose \:))
                         (map (partial apply str))
                         (interpose \,)
                         (apply str))
          _ (log/debug "Broker list" broker-string)
          connection (kafka/producer {"metadata.broker.list" broker-string
                                      "serializer.class" "kafka.serializer.DefaultEncoder"
                                      "partitioner.class" "kafka.producer.DefaultPartitioner"})]
      (assoc component :connection connection)))

  (stop [component]
    (log/info "Stopping Kafka producer...")
    (assoc component :connection nil)))

(defn new-kafka-producer [args]
  (map->KafkaProducer args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Consumer

(defn start-listening! [consumer topic receiver receiver-ctx]
  (async/go
    (let [stream (kafka-zk/create-message-stream consumer (name topic))]
      (run! #(receiver (-> %
                           kafka-core/to-clojure
                           :value
                           (String. "UTF-8")
                           (json/parse-string true)) receiver-ctx) stream))))

(defrecord KafkaConsumer [host port topic receiver receiver-ctx group-id]
  component/Lifecycle
  (start [component]
    (log/info "Starting Kafka consumer..." host port topic)
    (let [config {"zookeeper.connect" (str host ":" port)
                  "group.id" group-id
                  "auto.offset.reset" "smallest"
                  "auto.commit.enable" "true"}
          _ (log/debug "Consumer config:" config)
          consumer (kafka-zk/consumer config)]
      (start-listening! consumer topic receiver receiver-ctx)
      (assoc component :consumer consumer)))

  (stop [component]
    (log/info "Stopping Kafka consumer...")
    (when-let [consumer (:consumer component)]
      (kafka-zk/shutdown consumer))
    (assoc component :consumer nil)))

(defn new-kafka-consumer [args]
  (map->KafkaConsumer args))
