(ns app.file-list
  (:require
   [app.api :as api]
   [app.persistent-state :as persistent.state]
   [app.ui :as ui]
   [cljs.reader]
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
    :keys [headline backup_headline]
    :as props}]
  ;; need to update the spec a bit
  #_{:pre [(s/valid? :list/document props)]}

  ($ :.file
     ($ :div.file-header
        {:key file_id}
        ($ :h3 file_id ". " title)
        ($ :h4 author))
     ($ :span.file-text {:dangerouslySetInnerHTML  {:__html headline}})
     ($ :div backup_headline)))

;; File List ----------------------------------------------------------------
(defui file-list []
  (let [[search set-search-term!] (persistent.state/with-local-storage "booksearch/search-history" "")
        [files set-files!] (persistent.state/with-local-storage "booksearch/documents" [])
        [error set-error!] (uix/use-state nil)
        on-search (fn [search-term]
                    (set-search-term! search-term)
                    (api/make-remote-call (str "http://localhost:3000/api/list?query="
                                               search-term
                                               "&query_mode=phrase&strategy=ts_fast_headline")
                                          (fn [response]
                                            (js/console.log "response" (str response))
                                            (let [parsed-response (cljs.reader/read-string response)]
                                              (if (:error parsed-response)
                                                (do (set-error! (fn [_] (:error parsed-response)))
                                                    (set-files! (fn [_]  [])))
                                                (do (set-files! (fn [_]  parsed-response))
                                                    (set-error! (fn [_] nil))))))))]
    ($ :.app
       ($ ui/header)
       ($ ui/text-field {:initial-value search
                         :on-search on-search})
       (for [file files]
         ($ list-item file))
       
       (when error
         ($ :.error 
            ($ :h3 "Uh-oh! There was an error!")
            error)))))
