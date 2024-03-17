(ns app.server
  (:require [app.handlers :as handlers] 
            [reitit.ring :as ring]
            [reitit.ring.middleware.parameters :as middleware]
            [ring.adapter.jetty :as jetty])
  (:gen-class))


(def router
  (ring/router
   ["/api"
    ["/list" {:get handlers/list-files-handler}]
    ["/file" {:get handlers/file-contents-handler}]
    ["/search" {:get handlers/file-search-handler}]
    ]))

(def app
  (ring/ring-handler
   router
   (ring/create-default-handler
    {:not-found (constantly
                 {:status 404
                  :body "Not found"})})
   {:middleware [middleware/parameters-middleware]}))

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

(comment
  (-main) 
  (app {:request-method :get, :uri "/api/file?file_id=26"})

  (stop)
  (reset))
