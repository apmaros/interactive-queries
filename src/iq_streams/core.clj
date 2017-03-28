(ns iq-streams.core
  (:gen-class)
  (:require [clojure.tools.logging :as log]
            [iq-streams
             [config :as config]
             [rest :as rest]
             [store :as s]])
  (:import [org.apache.kafka.streams KafkaStreams StreamsConfig]
           [org.apache.kafka.streams.kstream KStreamBuilder ValueJoiner]
           org.apache.kafka.streams.state.QueryableStoreTypes))

(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread ex]
     (log/error ex "Uncaught exception on" (.getName thread)))))

(defn create-topology
  [customers-table order-stream]
  (log/info "creating orders topology")
  (->
   order-stream
   (.leftJoin
    customers-table
    (reify ValueJoiner
      (apply [this order customer]
        (log/info (format "Customer %s is having %s"
                          customer order))
        {:customer (or customer "UNKNOWN")
         :order (or order "UNKNOWN")})))
   .groupByKey
   (.count "order-count")))

(defn create-stream [builder config]
  (->
   builder
   (.table "customer" "customer-v1-changelog")
   (create-topology (.stream builder (into-array String ["order"]))))
  (KafkaStreams. builder config))

(defn -main
  [& args]
  (log/info "Order serving task")
  (let [streams (create-stream (KStreamBuilder.)
                               (StreamsConfig. config/stream-properties))]
    (.start streams)
    (rest/start! streams (:port config/host-info))))

(comment
  (def streams
    (create-stream (KStreamBuilder.)
                   (StreamsConfig. config/stream-properties)))

  (.start streams)

  (def store
    (s/get-store streams "order-count")))
