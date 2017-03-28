(ns iq-streams.serde.edn
  (:import [org.apache.kafka.common.serialization Deserializer Serde StringDeserializer StringSerializer])
  (:require [clojure.tools.logging :as log]))

(defn edn-serializer []
  (proxy [StringSerializer] []
    (serialize [topic data]
      (proxy-super serialize topic (pr-str data)))))

(defn edn-deserializer []
  (let [string-deserializer (StringDeserializer.)]
    (reify Deserializer
      (deserialize [_ topic data]
        (if data
          (read-string (.deserialize string-deserializer topic data))
          (log/error (str "No data to deserialize for topic " topic)))))))

(deftype EDNSerde []
  Serde
  (configure [_ configs is-key])
  (close [_])
  (serializer [_]
    (edn-serializer))
  (deserializer [_]
    (edn-deserializer)))

(defn edn-serde []
  (EDNSerde.))
