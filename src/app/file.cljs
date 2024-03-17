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
(s/def :files/content string?)
(s/def :files/title string?)
(s/def :files/author string?)
(s/def :profile/document (s/keys :req [:files/content :files/title :files/author]))

(defn clean-content 
  "Given plain text, adds chapter headings to text, and exchanges line return 
   character into html line breaks." 
  [content]
  {:pre [(s/valid? :files/content content)]}
  (-> content 
      (clojure.string/replace #"\n[Cc][Hh][Aa][Pp][Tt][Ee][Rr]\s[^\n]+"
                              #(str "<div class='chapter-title'>" %1 "</div>"))
      (clojure.string/replace "\n\r" 
                              "\n&nbsp;<br>&nbsp;<br>")))

(defn highlight-search-results 
  "Adds html spans with the search-result class to the content.
   Accepts search-results as a collection of maps with the :term key, where each
   term represents an exact match within a document."
  [search-results content]
  (when content
    (reduce (fn [text term]
              (clojure.string/replace text
                                      (str " " term)
                                      #(str " <span class='search-result'>"
                                            (clojure.string/trim %1)
                                            "</span> ")))
            content
            (->> search-results
             distinct
             (sort-by (comp count :term))
             reverse
                 (map :term)))))

(defui file-contents
  "Displays a text file as HTML. UI Component to convert a plain text (large) blob 
   of text into HTML, and present it as a single document. Additionally, we add markup
   around all of the words in the document matching search results"
  [{:files/keys [content] 
    :keys [search-results]
    :as file}]
  {:pre [(s/valid? :profile/document file)]}
  ($ :.file.open-book
     ($ ui/file-header file)
     ($ :article.file-text
        {:dangerouslySetInnerHTML
         {:__html (cond->> content
                    true clean-content
                    (seq search-results) (highlight-search-results search-results))}})))

(defn focus-search-result 
  "Magic click hander for scrolling around the DOM to travel between search results.
   Accepts:
   - set-result-no - function to set the ordinal of the currently-focussed search result
   - class-list - js collection of DOM elements bearing the search-result class
   - result-no - the currently focussed search result ordinal 
   - fn->apply - a function that accepts integer result_no and transforms it into an integer:
                 Consider inc, dec, (partial * 1), (partial * 0) as possibilities"
  [set-result-no class-list result-no fn->apply] 
  (when (pos? (.-length class-list))
    ;; remove focussed-result class if applied
    (when (.item class-list result-no)
      (.remove (.-classList (.item class-list result-no))
               "focussed-result"))
    ;; update result-no via the provided fn->apply
    (set-result-no fn->apply)
    ;; scroll to next item and adorn it with the focussed-result class
    (when (.item class-list (fn->apply result-no))
      ;; scroll
      (.scrollIntoView (.item class-list (fn->apply result-no)))
      ;; add focussed-result class
      (.add (.-classList (.item class-list (fn->apply result-no)))
            "focussed-result"))))

(defui search-results-list
  "UI controls for searching and scrolling through the document between
   foudn search results"
  [{:keys [loading search set-search-term search-results toggle-loading]
    :as file}]
  {:pre [(s/valid? :profile/document file)]} 
  (let [[result-no set-result-no] (uix/use-state -1)
        ;; js collection of dom elements bearing the search-result class
        ;; which is added via highlight-search-results to the main content
        class-list (js/document.getElementsByClassName "search-result")
        ;; prepared click handler for location buttons
        on-click (partial focus-search-result set-result-no class-list result-no)]
    ;; Effect handles update to search results by resetting the results number 
    ;; to -1 and then emulating first click to forward the user to item 0
    (uix/use-effect
     (fn [] 
       (toggle-loading false)
       (focus-search-result set-result-no class-list -1 inc))
     [search-results toggle-loading class-list])
    ;; User Interface
    ($ :div.file-search.open-book
       ($ ui/file-header file)
       ($ ui/text-field {:initial-value search
                         :on-search (fn [term]
                                      (set-search-term term)
                                      (set-result-no -1))})
       ($ :header
          (if loading
            ($ :h3 "Searching...")
            ($ :<> 
             ($ :h4 (inc result-no) " of " (.-length class-list) " results matching:")
             ($ :h3 (apply str (interpose " " (mapv :term search-results))))))) 
       ($ :.search-controls
          ($ :button
             {:on-click #(on-click dec)
              :disabled (< result-no 1)}
             "PREVIOUS")
          ($ :button
             {:on-click #(on-click (partial * 1))
              :disabled (= result-no -1)}
             "RELOCATE")
          ($ :button
             {:on-click #(on-click inc)
              :disabled (>= result-no (- (.-length class-list) 1))}
             "NEXT"))
       ($ :hr)
       ($ :a {:href "#"} "<< Back to Search"))))

;; File Viewer ----------------------------------------------------------------
(defui file-viewer
  "Main component to display a full text file and offer content search across the file"
  [{{{:keys [id]} :path-params} :route}]
  (let [[search set-search-term!] (persistent.state/with-local-storage "booksearch/search-history" "")
        [search-results set-search-results!] (uix/use-state nil)
        [file set-file!] (uix/use-state nil)
        [loading toggle-loading] (uix/use-state false)]
    ;; Runs on mount only:: Retrieves File contents
    (uix/use-effect
     (fn []
       (when-not (or loading file)
         (toggle-loading true)
         ;; Load File Content
         (api/make-remote-call
          (str "http://localhost:3000/api/file?file_id=" id)
          (fn [response] 
            (set-file! #(first (cljs.reader/read-string response)))))))
     [loading file id])
    ;; Runs on search submit/update of search var
    (uix/use-effect
     (fn []
       (toggle-loading true)
       ;; Get Search Results
       (api/make-remote-call
        (str "http://localhost:3000/api/search?file_id=" id
             "&query=" (js/encodeURIComponent search) "&query_mode=phrase")
        (fn [response]
          (set-search-results! #(->> response
                                     cljs.reader/read-string
                                     distinct
                                     (sort-by (comp count :term))
                                     reverse)))))
     [search id])
    ;; User Interface
    (if file
      ($ :div.file-profile
         ($ search-results-list (assoc file
                                       :loading loading
                                       :toggle-loading toggle-loading
                                       :search-results search-results
                                       :search search
                                       :set-search-term set-search-term!))
         ($ file-contents (assoc file
                                 :search-results search-results)))
      ($ :div {:style {:width "100vw"}} ($ ui/loading-bar)))))
