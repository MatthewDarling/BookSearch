(ns app.file
  (:require
   [app.api :as api]
   [app.persistent-state :as persistent.state]
   [app.ui :as ui] 
   [cljs.reader]
   [cljs.spec.alpha :as s]
   [clojure.set]
   [clojure.string]
   [uix.core :as uix :refer [defui $]]
   [uix.dom]))

;; Specs ----------------------------------------------------------------------
(s/def :file/content string?)
(s/def :file/title string?)
(s/def :file/author string?)
(s/def :list/document (s/keys :req-un [:file/content :file/title :file/author]))

(defn clean-content [content]
  (-> content 
      (clojure.string/replace "\n" 
                              "\n&nbsp;<br>")
      (clojure.string/replace #"[Cc][Hh][Aa][Pp][Tt][Ee][Rr]\s[^\n]+" 
                              #(str "<div class='chapter-title'>" %1 "</div>"))))

(defn highlight-search-results [search-results content]
  (when content
    (reduce (fn [text term]
              (clojure.string/replace text
                                      (str " " term " ")
                                      #(str " <span class='search-result'>" (clojure.string/trim %1) "</span> ")))
            content
            (map :term (set search-results)))))

(defui file-contents [{:files/keys [content file_id title author]
                       :keys [search-results]}]
  ($ :.file.open-book
     ($ :header
        {:key (keyword file_id) :class "file-title"}
        ($ :h3 title)
        ($ :h4 author))
     ($ :article.file-text
        {:dangerouslySetInnerHTML
         {:__html (cond->> content
                    true clean-content
                    (seq search-results) (highlight-search-results search-results))}})))

(defui search-results-list 
  [{:files/keys [title author file_id]
    :keys [search set-search-term search-results]}] 
  (let [[result-no set-result-no] (uix/use-state -1)
        class-list (js/document.getElementsByClassName "search-result")
        click-handler (fn [fn->apply _]
                        (when-not (neg? result-no)
                          (.remove (.-classList (.item class-list result-no)) "focussed-result"))
                        (set-result-no fn->apply)
                        (.scrollIntoView (.item class-list (fn->apply result-no)))
                        (.add (.-classList (.item class-list (fn->apply result-no))) "focussed-result"))
        _ (uix/use-effect
           (fn [] (set-result-no -1))
           [search-results])]
    ($ :div.file-search.open-book
       ($ :header
          {:key (keyword file_id) :class "file-title"}
          ($ :h3 title)
          ($ :h4 author))
       ($ ui/text-field {:initial-value search
                         :on-search (fn [term]
                                      (set-search-term term)
                                      (set-result-no 0))})
       ($ :header (inc result-no) " of " (.-length class-list) " results")
       ($ :.search-controls
        ($ :button {:on-click (partial click-handler dec)
                                  :disabled (< result-no 1)} "PREVIOUS")
        ($ :button {:on-click (partial click-handler (partial * 1))
                                  :disabled (= result-no -1)} "RELOCATE")
        ($ :button {:on-click (partial click-handler inc)
                                  :disabled (>= result-no (- (.-length class-list) 1))} "NEXT")))))

;; File Viewer ----------------------------------------------------------------
(defui file-viewer [{:keys [route]}]
  (let [[search set-search-term!] (persistent.state/with-local-storage "booksearch/search-history" "")
        [search-results set-search-results!] (uix/use-state nil)
        [file set-file!] (uix/use-state nil)
        ;; Runs on mount only:: 
        _ (uix/use-effect
           (fn [] 
             (api/make-remote-call
              (str "http://localhost:3000/api/file?file_id=" (-> route :path-params :id))
              (fn [response]
                (set-file! #(first (cljs.reader/read-string response)))))))
        ;; Runs on search submit/update of search var
        _ (uix/use-effect
           (fn []
             (api/make-remote-call
              (str "http://localhost:3000/api/search?file_id=" (-> route :path-params :id)
                   "&query=" search "&query_mode=phrase&strategy=ts_fast_headline")
              (fn [response]
                (set-search-results! #(cljs.reader/read-string response)))))
           [search route])]
    (when file
      ($ :div.file-profile 
         ($ search-results-list (assoc file
                                       :search-results search-results
                                       :search search
                                       :set-search-term set-search-term!))
         ($ file-contents (assoc file
                                 :search-results search-results))
         (str (keys file))))))


