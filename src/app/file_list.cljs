(ns app.file-list
  (:require
   [app.api :as api]
   [app.persistent-state :as persistent.state]
   [app.ui :as ui]
   [cljs.core.async :refer [go]]
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
  [{:files/keys [file_id title author sequence_no]
    :keys [headline backup_headline]
    :as props}]
  ;; need to update the spec a bit
  #_{:pre [(s/valid? :list/document props)]}

  ($ :.file
     ($ :div.file-header
        {:key (keyword file_id sequence_no)}
        ($ :h3 file_id ". " title)
        ($ :h4 author))
     ($ :span.file-text {:dangerouslySetInnerHTML  {:__html headline}})
     ($ :div backup_headline)))

;; File List ----------------------------------------------------------------
(defui file-list []
  (let [[search set-search-term!] (persistent.state/with-local-storage "booksearch/search-history" "")
        [files set-files!] (persistent.state/with-local-storage "booksearch/documents" [])
        [strategy set-strategy!] (persistent.state/with-local-storage "booksearch/strategy" "ts_fast_headline")
        [state set-state!] (uix/use-state {:loading false
                                           :error   nil})

        perform-search (fn [search-term strategy-key]
                         (set-files! (fn [_]  []))
                         (set-state! (fn [s] (assoc s :loading true :time-start (js/Date.now))))
                         (api/make-remote-call (str "http://localhost:3000/api/list?query="
                                                    search-term
                                                    "&query_mode=phrase&strategy="
                                                    strategy-key)
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
                                                                                    :time-end (js/Date.now))))))))))
        on-search (fn [search-term]
                    (set-search-term! search-term)
                    (perform-search search-term strategy))
        on-set-strategy (fn [strategy]
                          (set-strategy! strategy)
                          (perform-search search strategy))
        _ (go (when-let [counter-elem (js/document.getElementById "loading-counter")]
                (js/setInterval #(when (:loading state)
                                   (let [current-time (- (js/Date.) (:time-start state))]
                                     (set! (.-innerHTML counter-elem) (str current-time "ms"))))
                                1)))]
    ($ :.app
       ($ ui/header)
       ($ :.input-wrapper
          ($ ui/text-field {:initial-value search
                            :on-search on-search})
          ($ ui/strategy-radio-options {:on-set-strategy on-set-strategy
                                        :strategy strategy})

          ($ :.stats "Time to query: "
             (if (> (- (:time-end state) (:time-start state)) 0)
               ($ :.elapsed (- (:time-end state) (:time-start state)) "ms")
               "-")))

       (when (:loading state)
         ($ :.loading
            "Loading "
            ($ :.loading-book
               ($ :span.page.turn)
               ($ :span.page.turn)
               ($ :span.page.turn)
               ($ :span.page.turn)
               ($ :span.cover)
               ($ :span.page)
               ($ :span.cover.turn))
            ($ :#loading-counter)))


       (for [file files]
         ($ list-item file))

       (when (:error state)
         ($ :.error
            ($ :h3 "Uh-oh! There was an error!")
            (str (:error state)))))))
