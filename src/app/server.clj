(ns app.server
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [reitit.http :as http]
            [reitit.ring :as ring]
            [reitit.ring.middleware.parameters :as middleware]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as response])
  (:gen-class))

(sql/register-op! (keyword "@@"))

(def db-config {:dbtype "postgres"
                :dbname "booksearch"
                :user "booksearch"
                :password "bookitysearch"})

(defn query-mode->tsp-query-fn
  [query-mode]
  (if (= "phrase" query-mode)
    :phraseto_tspquery
    :to_tspquery))

(defn query-mode->query-fn
  [query-mode]
  (if (= "phrase" query-mode)
    :phraseto_tsquery
    :to_tsquery))

(defn compile-fast-search-query
  [query query-mode]
  {:pre [(#{"phrase" "logical"} query-mode)]}
  {:select [:files.file_id
            :filename
            :title
            :author
            [[:tsp_present_text [:array_to_string [:slice_array [:array_agg :headline/headline] 1 5] " ... "]] :headline]]
   :from [:files]
   :left-join [[:file_lookup_16k :fl] [:= :fl/file_id :files/file_id]
               [[:ts_fast_headline_cover_density
                 [:cast "english" :regconfig]
                 :content_array
                 :content_tsv
                 [:phraseto_tspquery [:cast query :text]]] :headline] [true]]
   :where [(keyword "@@") :content_tsv [(query-mode->tsp-query-fn query-mode) query]]
   :group-by [:files.file_id]
   :order-by [:files.file_id]})

(defn compile-search-query
  [query query-mode strategy]
  {:pre [(#{"phrase" "logical"} query-mode)
         (#{"ts_headline" "ts_semantic_headline" "ts_fast_headline"} strategy)]}
  (if (= "ts_fast_headline" strategy)
    (compile-fast-search-query query query-mode)
    {:select [:file_id :filename :title :author
              [[(keyword strategy) 
                :content 
                [(query-mode->query-fn query-mode) [:cast query :text]]
                "MaxFragments=5"]
               :headline]]
     :from [:files]
     :where [(keyword "@@") :content [(query-mode->query-fn query-mode) query]]}))

(defn retrieve-file-handler
  "Async and sync compatible handler. Most example online show only a synchronous
  handler, but when I only define the one arg version, I get 'wrong number of
  args' when calling with curl."
  ([{{:strs [file_id]}
     :query-params :as request}]
   (let [file-id (Integer/parseInt file_id)
         my-datasource (jdbc/get-datasource db-config)]
     (with-open [connection (jdbc/get-connection my-datasource)]
       (response/response
        (pr-str (jdbc/execute! connection
                               (sql/format {:select [:filename :title :author :content]
                                            :from   [:files]
                                            :where  [:= :files.file_id file-id]})))))))
  ([request respond raise]
   (respond (retrieve-file-handler request))))

(defn list-files-handler
  ([{{:strs [query query_mode strategy]}
     :query-params :as request}]
   (let [my-datasource (jdbc/get-datasource db-config)]
     (with-open [connection (jdbc/get-connection my-datasource)]
       (try 
         (let [result (jdbc/execute! connection
                                     (sql/format (compile-search-query query
                                                                       query_mode
                                                                       strategy)))]
           (-> result
               pr-str
               response/response
               (response/header "Access-Control-Allow-Origin"
                                "http://localhost:8080")
               (response/header "Access-Control-Allow-Credentials"
                                "true")))
         (catch Exception e 
           (-> {:error (.toString e)}
               pr-str
               response/response
               (response/status 401)
               (response/header "Access-Control-Allow-Origin"
                                "http://localhost:8080")
               (response/header "Access-Control-Allow-Credentials"
                                "true"))
           )))))
  ([request respond raise]
   (respond (list-files-handler request))))

(def router
  (ring/router
   ["/api"
    ["/file" {:get retrieve-file-handler}]
    ["/list" {:get list-files-handler}]]))

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
