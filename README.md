# Kafka Stream Interactive Queries
This is example implementation of blog article [Unifying Stream Processing and Interactive Queries in Apache Kafka](https://www.confluent.io/blog/unifying-stream-processing-and-interactive-queries-in-apache-kafka/).

## Usage

1. Bootstrap

	- Start Kafka cluster
		
	`docker-compose up -d`
	
	- Create topics
	
	```
	cd confluent-3.x.x 
	  ./bin/kafka-topics --zookeeper localhost:2181 --partitions 3 --replication 1 --create --topic order
	
	  ./bin/kafka-topics --zookeeper localhost:2181 --partitions 3 --replication 1 --create --topic customer
	
	  ./bin/kafka-topics --zookeeper localhost:2181 --partitions 3 --replication 1 --create --topic food-server-order-count-changelog
	
	```

2. Seed data

	```
	echo "1--john\n2--adam\n3--daniel\n4--james\n5--jack\n6--mathew\n7--will" | bin/kafka-console-producer --broker-list localhost:9092 --topic customer --property parse.key=true --property key.separator="--"
		  
	echo "1--spagety\n2--pizza\n--pasta" | bin/kafka-console-producer --broker-list localhost:9092 --topic order  --property parse.key=true --property key.separator="--"
	```
  
3. Run application

	Make sure that `STATE_DIR` and `APPLICATION_PORT` is unique. 
  
	```
	STATE_DIR=food-server-0 APPLICATION_PORT=5400 lein run
	STATE_DIR=food-server-1 APPLICATION_PORT=6400 lein run
	STATE_DIR=food-server-2 APPLICATION_PORT=7400 lein run
	```
	    
## Topology
Source of the topology are two streams

- **customer**
  - compacted topics represented as KTable 
  - format: `key:user-id, value:username`
- **order**
  - stream topic symbolising the order represented as KStream
  - format: `key:user-id, value:food-name`

and following processing nodes

- **left join**
  - join the order stream with customers table.

- **group by key**
  - group orders by message key

- **count**
  - count grouped orders  

## Rest

Rest is powered by embedded jetty server, in this case [ring jetty](https://github.com/ring-clojure/ring). It is used to expose the metadata and store information provided by the stream application. It will be described in following sections.

## Discovery

We have created topics with 3 partitions running on 3 application instances. Application instances divide data in the stores so that each application instance is assigned with a single partition.

KafkaStreams `0.10.1.0` allows to expose application its host metadata. Setting `application.server` streams config parameter, the application advertise its endpoint information metadata e.g. `localhost:5400` to other instances of the application.

These metadata are used by application to discover on what address is value of specific key available. This example exposes these metadata with API. Following metadata are available:

- all metadata - all metadata with host information of the application instances which are storing a data. If we start new application instance on e.g. port `9400`, this will not have any partition assigned (topics have only 3 partition, so no left to give to another node) and therefore will not be in the metadata list. 

```shell
 curl localhost:7400/metadata
 #=> {"result":[{"host":"localhost","port":7400,"state-store-names":["customer-v1-changelog","order-count"]},{"host":"localhost","port":6400,"state-store-names":["customer-v1-changelog","order-count"]},{"host":"localhost","port":8400,"state-store-names":["customer-v1-changelog","order-count"]}]} 
```

- all metadata for store - host information of instances storing data for the particular store. We should see again all 3 application instances. 
```shell
  curl localhost:7400/metadata/stores/order-count
   #=> {"result":[{"host":"localhost","port":7400,"state-store-names":["customer-v1-changelog","order-count"]},{"host":"localhost","port":6400,"state-store-names":["customer-v1-changelog","order-count"]},{"host":"localhost","port":8400,"state-store-names":["customer-v1-changelog","order-count"]}]}
```
- all metadata for key - host information of instances containing data for the particular store and key. It must be always a single host regardless the value for the key exists or not.
 ```shell
  curl localhost:7400/metadata/stores/order-count/key/1
   #=> {"result":{"host":"localhost","port":6400,"state-store-names":["customer-v1-changelog","order-count"]}}
 ```

## Store
The application is using two stores:

- `customer-v1-changelog` - persists customers KTable
- `order-count` - persists the number of orders as side effect of last `.count` processing node

Store is accessible from running stream:

```
;; stream - KafkaStreams instance
;; returns: CompositeReadOnlyKeyValueStore,
;;   implementation of ReadOnlyKeyValueStore
(.store stream store-name (QueryableStoreTypes/keyValueStore))
```

 Its worth to note, that KafkaStreams has two types of stores; `keyValueStore` and `windowStore`. This example was scoped down to keyValue store only.
 
 [ReadOnlyKeyValueStore](http://docs.confluent.io/3.1.0/streams/javadocs/index.html) is a wrapper around store, by default the RocksDB store. Applying REST following operations are supported:
 
- all

```
curl http://localhost:7400/stores/customer-v1-changelog/all
 #=> {"result":[{"key":"2","value":"adam"},{"key":"3","value":"daniel"}]}
```

- get

```
curl http://localhost:6400/stores/customer-v1-changelog/get/2
 #=> {"response":"adam"}
```
notice, that user with key _2_ is located on the `localhost:7400` 

- range

```
curl http://localhost:7400/stores/customer-v1-changelog/range?from=1&to=5
 #=> {"result":[{"key":"1","value":"john"},{"key":"5","value":"jack"}]}
```

- approximate-num-etnries

```
curl http://localhost:7400/stores/customer-v1-changelog/approximate-num-entries
 #=> {"result":6}
```

Because the store is in this example distributed on multiple application instances, we combine `get` functionality with the discovery service so that user can search value for any key on any of running instances.

Because we have access to the metadata for the key and store, it is easy to find out what host contains the information.

If its current host, the application just queries the store. Otherwise application uses http client to fetch it from the host where it is located and returns the value.

```clojure
(GET "/stores/:store/get/:k" [store k]
    (let [host-info (m/metadata-for-key @streams-instance store k)]
      (response
       (if (this-host? host-info)
         {:response (-> (s/get-store @streams-instance store)
             (s/fetch k))}
         (fetch-host host-info (format "/stores/%s/get/%s"
                                       store k))))))
```

Kafka streams exposes a wrapper around the RocksDB instead of the RocksDB instance, what limits the functionality that RocksDB has to offer.
