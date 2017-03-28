(defproject iq-streams "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.apache.kafka/kafka-streams "0.10.1.0"]
                 [org.apache.kafka/kafka-clients "0.10.1.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [environ "1.1.0"]
                 [ring/ring-core "1.5.0"]
                 [ring/ring-jetty-adapter "1.5.0"]
                 [ring/ring-json "0.4.0"]
                 [metosin/compojure-api "1.1.9"]
                 [clj-http "2.3.0"]]

  :main ^:skip-aot iq-streams.core
  :target-path "target/%s"
  :aot [iq-streams.serde.edn]
  :profiles {:uberjar {:aot :all}})
