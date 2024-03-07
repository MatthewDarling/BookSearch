(ns app.server
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:gen-class))

(sql/register-op! (keyword "@@"))

(def db-config {:dbtype "postgres"
                :dbname "booksearch"
                :user "booksearch"
                :password "bookitysearch"})

(defn query-mode->query-fn
  [query-mode]
  (if (= "phrase" query-mode)
    :phraseto_tsquery
    :to_tsquery))

(defn compile-fast-search-query
  [query query-mode]
  {:pre [(#{"phrase" "logical"} query-mode)]}
  {:select [:file_id :filename :title :author :content]
   :from [:files]
   ;;; TODO handle calling format differences for fast headline
   :where [:ts_fast_headline :content [:to_tsquery]]})

(defn compile-search-query
  [query query-mode strategy]
  {:pre [(#{"phrase" "logical"} query-mode)
         (#{"ts_headline" "ts_semantic_headline" "ts_fast_headline"} strategy)]}
  (if (= "ts_fast_headline" strategy)
    (compile-fast-search-query query query-mode)
    {:select [:file_id :filename :title :author :content
              [[(keyword strategy) :content [(query-mode->query-fn query-mode) [:cast query :text]]]
               :headline]]
     :from [:files]
     :where [(keyword "@@") :content [(query-mode->query-fn query-mode) query]]}))
