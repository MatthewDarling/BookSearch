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

(defn search-handler [set-files! set-state!]
  (fn [response]
    (let [parsed-response (cljs.reader/read-string response)]
      (if (:error parsed-response)
        (do (set-state! (fn [s] (assoc s
                                       :error parsed-response
                                       :loading false
                                       :time-end (js/Date.now))))
            (set-files! (fn [_]  [])))
        (do (set-files! (fn [_]  parsed-response))
            (set-state! (fn [s] (assoc s
                                       :error nil
                                       :loading false
                                       :time-end (js/Date.now)))))))))

;; List Item ------------------------------------------------------------------
(defui list-item
  [{:files/keys [file_id title author sequence_no]
    :keys [headline backup_headline]
    :as props}]
  ;; need to update the spec a bit
  #_{:pre [(s/valid? :list/document props)]}

  ($ :.file.open-book
     ($ :header
        {:key (keyword file_id sequence_no)}
        ($ :h3  title)
        ($ :h4 author))
     ($ :a {:href (str "#file/" file_id)} ">>")
     ($ :article.file-text
        {:dangerouslySetInnerHTML
         {:__html (-> headline
                      (clojure.string/replace "<b>"
                                              (str "<a href='#file/" file_id "'><b>"))
                      (clojure.string/replace "</b>"
                                              "</b></a>"))}})
     ($ :div backup_headline)))

;; File List ----------------------------------------------------------------
(defui file-list []
  (let [[search set-search-term!] (persistent.state/with-local-storage "booksearch/search-history" "")
        [files set-files!] (persistent.state/with-local-storage "booksearch/documents" [])
        [strategy set-strategy!] (persistent.state/with-local-storage "booksearch/strategy" "ts_fast_headline")
        [state set-state!] (uix/use-state {:loading false
                                           :error   nil}) 
        _ (uix/use-effect
           (fn [ ]
             (set-files! (fn [_]  []))
             (set-state! (fn [s] (assoc s
                                        :loading true
                                        :time-start (js/Date.now))))
             (api/make-remote-call (str "http://localhost:3000/api/list?query="
                                        search
                                        "&query_mode=phrase&strategy="
                                        strategy)
                                   (search-handler set-files! set-state!)))
           [search strategy set-files!])]
    ($ :.app
       ($ ui/header) 
       ($ :.input-wrapper
          ($ ui/text-field {:initial-value search
                            :on-search set-search-term!})
          ($ ui/strategy-radio-options {:on-set-strategy set-strategy!
                                        :strategy strategy})
          ($ ui/query-stats state))
       (when (:loading state) ($ ui/loading-bar))
       (for [file files] ($ list-item file))
       (when (:error state)
         ($ :.error
            ($ :h3 "Uh-oh! There was an error!")
            (str (:error state)))))))
