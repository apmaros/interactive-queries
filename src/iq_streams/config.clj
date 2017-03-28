(ns iq-streams.config
  (:require [environ.core :refer [env]]
            [iq-streams.serde.edn :refer [edn-serde]])
  (:import org.apache.kafka.common.serialization.Serdes
           org.apache.kafka.streams.StreamsConfig))

(def host-info
  {:host "localhost"
   :port (Integer/parseInt
          (env :application-port "2020"))})

(defn host-address
  [host]
  (str (:host host) ":" (:port host)))

(def stream-properties
  {StreamsConfig/APPLICATION_ID_CONFIG     "food-server"
   StreamsConfig/BOOTSTRAP_SERVERS_CONFIG  (or (System/getenv "BOOTSTRAP_SERVERS") "localhost:9092")
   StreamsConfig/KEY_SERDE_CLASS_CONFIG    (.getName (.getClass (Serdes/String)))
   StreamsConfig/VALUE_SERDE_CLASS_CONFIG  (.getClass (edn-serde))
   StreamsConfig/ZOOKEEPER_CONNECT_CONFIG  (env :zookeeper-address, "localhost:2181")
   StreamsConfig/APPLICATION_SERVER_CONFIG (host-address host-info)
   StreamsConfig/STATE_DIR_CONFIG          (str "/tmp/kafka-streams/" (java.util.UUID/randomUUID))})
