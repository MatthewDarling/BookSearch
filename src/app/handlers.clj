(ns app.handlers
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc] 
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
     :where [:in :file_id {:select [[[:distinct :file_id]] ] :from [:file_lookup_16k] 
              :where [(keyword "@@") :content_tsv [(query-mode->query-fn query-mode) query]]}]}))

(defn add-cors-headers [resp]
  (-> resp
      (response/header "Access-Control-Allow-Origin"
                       "http://localhost:8080")
      (response/header "Access-Control-Allow-Credentials"
                       "true")))

;; Responses ------------------------------------------------------------------
(defn file-contents-handler
  "Async and sync compatible handler. Most example online show only a synchronous
  handler, but when I only define the one arg version, I get 'wrong number of
  args' when calling with curl."
  ([{{:strs [file_id]}
     :query-params :as request}]
   (let [file-id (Integer/parseInt file_id)
         my-datasource (jdbc/get-datasource db-config)]
     (with-open [connection (jdbc/get-connection my-datasource)]
       (->> {:select [:filename :title :author :content]
             :from   [:files]
             :where  [:= :files.file_id file-id]}
            sql/format 
            (jdbc/execute! connection)
            pr-str
            response/response
            add-cors-headers))))
  ([request respond raise]
   (respond (file-contents-handler request))))

(defn list-files-handler
  ([{{:strs [query query_mode strategy]} :query-params}]
   (let [my-datasource (jdbc/get-datasource db-config)]
     (with-open [connection (jdbc/get-connection my-datasource)]
       (try
         (let [result (->> (compile-search-query query
                                                 query_mode
                                                 strategy)
                           sql/format
                           (jdbc/execute! connection))]
           (-> result
               pr-str
               response/response
               add-cors-headers))
         (catch Exception e
           (-> {:error (.toString e)}
               pr-str
               response/response
               (response/status 401)
               add-cors-headers))))))
  ([request respond raise]
   (respond (list-files-handler request))))

(defn file-search-handler
  ([{{:strs [query query_mode file_id]}
     :query-params :as request}]
   {:pre [(#{"phrase" "logical"} query_mode)]}

   (let [file-id (Integer/parseInt file_id)
         my-datasource (jdbc/get-datasource db-config)]
     (with-open [connection (jdbc/get-connection my-datasource)]
       (->> {:select [[[:tsp_present_text :headline.words] :term]]
             :from [:files]
             :left-join [[:file_lookup_16k :fl] [:= :fl/file_id :files/file_id]
                         [[:tsp_query_matches
                           [:cast "english" :regconfig]
                           :content_array
                           :content_tsv
                           [:phraseto_tspquery [:cast query :text]]
                           [:cast 50 :integer]] :headline] [true]]
             :where [:and
                     [:= :files/file_id file-id]
                     [(keyword "@@") :content_tsv [(query-mode->tsp-query-fn query_mode) query]]]}
            sql/format
            (jdbc/execute! connection)
            pr-str
            response/response
            add-cors-headers))))
  ([request respond raise]
   (respond (file-search-handler request))))

