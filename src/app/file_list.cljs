(ns app.file-list
  (:require
   [app.api :as api]
   [app.persistent-state :as persistent.state]
   [app.ui :as ui] 
   [cljs.spec.alpha :as s]
   [clojure.set]
   [uix.core :as uix :refer [defui $]]
   [uix.dom]))

;; Specs ----------------------------------------------------------------------
(s/def :file/content string?)
(s/def :file/title string?)
(s/def :file/author string?)
(s/def :list/document (s/keys :req-un [:file/content :file/title :file/author]))

;; List Item ------------------------------------------------------------------
(defui list-item
  [{:files/keys [file_id content title author]
    :keys [headline]
    :as props}]
  ;; need to update the spec a bit
  #_{:pre [(s/valid? :list/document props)]}
  ($ :.file
     ($ :div.file-header
        {:key file_id}
        ($ :h3 file_id ". " title)
        ($ :h4 author))
     ($ :span.file-text headline)))

;; File List ----------------------------------------------------------------
(defui file-list []
  (let [[search set-search-term!] (persistent.state/with-local-storage "booksearch/search-history" "")
        [files set-files!] (persistent.state/with-local-storage "booksearch/documents" [])
        on-search (fn [search-term]
                    (set-search-term! search-term)
                    (api/make-remote-call (str "http://localhost:3000/api/list?query=" search-term "&query_mode=phrase&strategy=ts_headline")
                                          (fn [response]
                                            (set-files! #(identity response)))))]
    ($ :.app
       ($ ui/header)
       ($ ui/text-field {:initial-value search
                         :on-search on-search})
       (for [[id file] (map-indexed vector files)]
         ($ list-item file)))))
