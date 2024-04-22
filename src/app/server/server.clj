(ns app.server.server
  (:require [app.server.handlers :as handlers] 
            [reitit.ring :as ring]
            [reitit.ring.middleware.parameters :as middleware]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.json :as ring.json])
  (:gen-class))


(def router
  (ring/router
   ["/api"
    ["/list" {:get handlers/-list-files-handler}]
    ["/file" {:get handlers/-file-contents-handler}]
    ["/search" {:get handlers/-file-search-handler}]]))

(def app
  (ring/ring-handler
   router
   (ring/create-default-handler
    {:not-found (constantly
                 {:status 404
                  :body "Not found"})})
   {:middleware [middleware/parameters-middleware
                 ring.json/wrap-json-response]}))

(def port 3000)

(defonce server-var nil)

(defn start [_]
  (let [server (jetty/run-jetty #'app {:port port, :join? false, :async? true})]
    (println "server running in port" port)
    server))

(defn stop []
  (.stop server-var)
  (alter-var-root #'server-var (constantly nil)))

(defn -main [& args]
  (alter-var-root #'server-var start))

(defn reset []
  (stop)
  (-main))
