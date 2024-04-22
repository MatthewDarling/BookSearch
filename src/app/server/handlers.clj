(ns app.server.handlers
  (:require
   [aero.core :refer [read-config]]
   [clojure.java.io :as io]
   [honey.sql :as sql]
   [next.jdbc :as jdbc]
   [ring.util.codec :as codec]
   [ring.util.response :as response])
  #_(:gen-class
   :implements [com.amazonaws.services.lambda.runtime.RequestStreamHandler]))

(sql/register-op! (keyword "@@"))

(def db-config
  (-> "config.edn"
      io/resource
      (read-config {:profile :dev})
      :db-config))

(defn query-mode->tsp-query-fn
  "Converts Query Mode (phrase/logical) into the appropriate TSP query parser.
   Be careful: ts_query is not the same as tsp_query"
  [query-mode]
  (if (= "phrase" query-mode)
    :phraseto_tspquery
    :to_tspquery))

(defn query-mode->query-fn
  "Converts Query Mode (phrase/logical) into the appropriate BUILT-IN query parser"
  [query-mode]
  (if (= "phrase" query-mode)
    :phraseto_tsquery
    :to_tsquery))

(defn compile-fast-search-query
  "Given a user-inputted query string and a search mode (phrase/logical), performs
   a lookup of file contents using the ts_fast_query system, aggregating across
   file_lookup_16k to perform fast search and recall. "
  [query query-mode]
  {:pre [(#{"phrase" "logical"} query-mode)]}
  {:select   [:file_id 
              [[:tsp_present_text [:string_agg :headline " ... "]] :headline] 
              :title
              :author]
   :from     [[{:select    [:files.file_id
                            :headline
                            :title
                            :author
                            [[:raw "ROW_NUMBER() 
                             OVER (PARTITION BY files.FILE_ID 
                                   ORDER BY files.file_id, density DESC)"] :rn]]
                :from      [:files]
                :left-join [[:file_lookup_16k :fl] [:= :fl/file_id :files/file_id]
                            [[:ts_fast_headline_cover_density
                              [:cast "english" :regconfig]
                              :content_array
                              :content_tsv
                              [(query-mode->tsp-query-fn query-mode) [:cast query :text]]] :headline] [true]]
                :where     [(keyword "@@") :content_tsv [(query-mode->tsp-query-fn query-mode) query]]
                :order-by  [:files.file_id :density]}
               :data]]
   :where    [:< :rn 6]
   :group-by [:file_id :title :author]})

(defn compile-search-query
  "Given a user-inputted query string and a search mode (phrase/logical), and a
   strategy (ts_fast_headline, ts_semantic_headline, ts_headline), and performs
   a lookup of file contents using the query appropriate to the strategy."
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
     :where [:in :file_id {:select [[[:distinct :file_id]]] :from [:file_lookup_16k]
                           :where [(keyword "@@") :content_tsv [(query-mode->query-fn query-mode) query]]}]}))

(defn add-cors-headers 
  "Adds CORS (cross-origin resource sharing) headers to response"
  [resp]
  (-> resp
      (response/header "Access-Control-Allow-Origin"
                       "http://localhost:8080")
      (response/header "Access-Control-Allow-Credentials"
                       "true")))

(defn error->response
  "Given an error object, produces a 500 response with a string representation of 
   the error."
  [error]
  (-> {:error (.toString error)}
      pr-str
      response/response
      (response/status 500)
      add-cors-headers))

(defn query->response
  "Given a JDBC connection map and an HSQL map representing an SQL query, executes
   the query and creates an http response object with that data."
  [connection query-map]
  (->> query-map
       sql/format
       (jdbc/execute! connection)
       response/response
       add-cors-headers))

;; Responses ------------------------------------------------------------------
(defn -file-contents-handler
  "Responds to requests for the fill contents of a file"
  ([{{:strs [file_id]} :query-params}]
   (let [file-id (Integer/parseInt file_id)
         my-datasource (jdbc/get-datasource db-config)]
     (with-open [connection (jdbc/get-connection my-datasource)]
       (query->response connection
                        {:select [:filename :title :author :content]
                         :from   [:files]
                         :where  [:= :files.file_id file-id]}))))
  ([request respond raise]
   (respond (-file-contents-handler request))))

(defn -list-files-handler
  "Responds to a file list query"
  ([{{:strs [query query_mode strategy]} :query-params}]
   (let [my-datasource (jdbc/get-datasource db-config)]
     (with-open [connection (jdbc/get-connection my-datasource)]
       (try
         (query->response connection
                          (compile-search-query (codec/form-decode query)
                                                query_mode
                                                strategy))
         (catch Exception e
           (error->response e))))))
  ([request respond raise]
   (respond (-list-files-handler request))))

(defn -file-search-handler
  "Responds to a query for the list of matching search terms in a document."
  ([{{:strs [query query_mode file_id]} :query-params}]
   {:pre [(#{"phrase" "logical"} query_mode)]}
   (let [query (codec/form-decode query)
         file-id (Integer/parseInt file_id)
         my-datasource (jdbc/get-datasource db-config)]
     (with-open [connection (jdbc/get-connection my-datasource)]
       (try
         (->> {:select [[[:tsp_present_text :headline.words] :term]]
               :from [:files]
               :left-join [[:file_lookup_16k :fl] [:= :fl/file_id :files/file_id]
                           [[:tsp_query_matches
                             [:cast "english" :regconfig]
                             :content_array
                             :content_tsv
                             [(query-mode->tsp-query-fn query_mode) [:cast query :text]]
                             [:cast 50 :integer]] :headline] [true]]
               :where [:and
                       [:= :files/file_id file-id]
                       [(keyword "@@") :content_tsv [(query-mode->tsp-query-fn query_mode) query]]]}
              (query->response connection))
         (catch Exception e
           (error->response e))))))
  ([request respond raise]
   (respond (-file-search-handler request))))

(defn -main [& args]
  (str "Passed : " args))