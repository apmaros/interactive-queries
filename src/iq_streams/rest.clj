(ns iq-streams.rest
  (:require [cheshire.core :refer [parse-string]]
            [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [compojure
             [core :refer :all]
             [route :as route]]
            [iq-streams
             [config :as config]
             [metadata :as m]
             [store :as s]]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware
             [json :refer [wrap-json-response]]
             [params :refer [wrap-params]]]
            [ring.util.response :refer [response]]))

(def server-instance (atom nil))
(def streams-instance (atom nil))

(defn- this-host?
  [host-info]
  (and
   (= (:port host-info)
      (:port config/host-info))
   (= (:host host-info)
      (:host config/host-info))))

(defn- fetch-host
  [host-info path]
  (try
    (-> (client/get (str "http://" (config/host-address host-info) path))
        :body
        parse-string)
    (catch java.net.ConnectException e
      {:error "unable to connect"
       :message (.getMessage e)})))

(defroutes handler
  (GET "/" []
    (response {}))
  (GET "/metadata" []
    (response {:result (m/streams-metadata @streams-instance)}))
  (GET "/metadata/stores/:store" [store]
    (response {:result (m/metadata-for-store @streams-instance store)}))
  (GET "/metadata/stores/:store/key/:k" [store k]
    (response {:result (m/metadata-for-key @streams-instance store k)}))

  (GET "/stores/:store/get/:k" [store k]
    (let [host-info (m/metadata-for-key @streams-instance store k)]
      (response
       (if (this-host? host-info)
         {:response (-> (s/get-store @streams-instance store)
             (s/fetch k))}
         (fetch-host host-info (format "/stores/%s/get/%s"
                                       store k))))))

  (GET "/stores/:store/all" [store]
    (response {:result (s/fetch-all (s/get-store @streams-instance store))}))

  (GET "/stores/:store/range" [store from to]
    (response {:result (-> (s/get-store @streams-instance store)
                         (s/fetch-range from to))}))

  (GET "/stores/:store/approximate-num-entries" [store]
    (response {:result (-> (s/get-store @streams-instance store)
                           s/approximate-num-entries)}))

  (route/not-found (response
                    {:error "route not found"})))

(def app
  (-> handler
      wrap-params
      wrap-json-response))

(defn- start-server!
  [port]
  (log/info "Starting rest server on port " port)
  (future (run-jetty app {:port port})))

(defn start! [streams port]
  (let [server (start-server! port)]
    (reset! server-instance (constantly server))
    (reset! streams-instance streams)
    server))
