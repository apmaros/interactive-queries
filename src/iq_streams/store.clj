(ns iq-streams.store
  (:require [iq-streams.utils :refer [obj->map]])
  (:import [org.apache.kafka.streams.state QueryableStoreTypes]))

;; returns instance of ReadOnlyKeyValueStore
;; does not expose RocksDB instance

(defn get-store
  [stream store-name]
  (.store stream store-name (QueryableStoreTypes/keyValueStore)))

(defn key-values->map
  [elements]
  (map #(obj->map % :key .key :value .value)
       elements))

(defn fetch
  [store k]
  (.get store k))

(defn fetch-all
  [store]
  (-> (.all store)
      iterator-seq
      key-values->map))

(defn fetch-range
  [store from to]
  (-> (.range store from to)
      iterator-seq
      key-values->map))

(defn approximate-num-entries
  [store]
  (.approximateNumEntries store))
