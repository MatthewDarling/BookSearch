(ns app.file
  (:require
   [app.api :as api]
   [app.html :as html]
   [app.persistent-state :as persistent.state]
   [app.semantic-ui :as sui]
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
(s/def :profile/document (s/keys :req [:files/content 
                                       :files/title
                                       :files/author]))

(defui file-contents
  "Displays a text file as HTML. UI Component to convert a plain text (large) 
   blob of text into HTML, and present it as a single document. Additionally, we 
   add markup around all of the words in the document matching search results"
  [{:files/keys [content] 
    :keys       [search-results]
    :as         file}]
  {:pre [(s/valid? :profile/document file)]}
  ($ :<>
     ($ ui/file-header file)
     ($ :article.file-text
        {:dangerouslySetInnerHTML
         {:__html (cond->> content
                    true
                    html/clean-content
                    (seq search-results)
                    (html/highlight-search-results  search-results))}})))

(defn focus-search-result 
  "Magic click hander for scrolling around the DOM to travel between search 
   results.
   Accepts:
   - set-result-no - function to set the ordinal of the currently-focussed 
                     search result
   - class-list - js collection of DOM elements bearing the search-result class
   - result-no - the currently focussed search result ordinal 
   - fn->apply - a function that accepts integer result_no and transforms it 
                 into an integer:
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
  [{:keys [loading toggle-loading
           search set-search-term
           search-results
           mode set-mode]
    :as   file}]
  {:pre [(s/valid? :profile/document file)]}
  (let [[result-no set-result-no] (uix/use-state -1)
        ;; js collection of dom elements bearing the search-result class
        ;; which is added via highlight-search-results to the main content
        class-list                (js/document.getElementsByClassName 
                                   "search-result")
        ;; prepared click handler for location buttons
        on-click                  (partial focus-search-result 
                                           set-result-no
                                           class-list
                                           result-no)]

    ;; Effect handles update to search results by resetting the results number 
    ;; to -1 and then emulating first click to forward the user to item 0
    (uix/use-effect
     (fn []
       (toggle-loading false)
       (focus-search-result set-result-no class-list -1 inc))
     [search-results toggle-loading class-list])

    ;; User Interface
    ($ :.file-search
       ($ sui/content
          ($ ui/file-header file)
          ($ sui/row
             {:class :z-index-force-overlay}
             ($ ui/text-field
                {:initial-value search
                 :on-search     (fn [term]
                                  (set-search-term term)
                                  (set-result-no -1))})) 
          ($ ui/query-mode-options
             {:mode        mode
              :on-set-mode set-mode})
          ($ :hr)
          ($ sui/h3
             (if loading
               ($ sui/content "Searching...")
               ($ ui/file-search-results
                  {:search-results search-results
                   :result-no      result-no
                   :class-list     class-list})))
          ($ :hr)
          ($ sui/buttons
             {:class [:fluid]}
             ($ sui/button
                {:on-click      #(on-click dec)
                 :class         [:basic
                                 (when (>= result-no 1)
                                   :green)]
                 :data-tooltip  "Scroll to the previous result"
                 :data-position "bottom right"
                 :disabled      (< result-no 1)}
                ($ :i.icon.angle.double.left))
             ($ sui/button
                {:on-click      #(on-click (partial * 1))
                 :class         [:basic
                                 (when-not (neg? result-no)
                                   :blue)]
                 :disabled      (= result-no -1)
                 :data-tooltip  "Scroll to the current result"
                 :data-position "bottom right"}
                ($ :i.icon.map.pin))
             ($ sui/button
                {:on-click      #(on-click inc)
                 :class         [:basic
                                 (when (< result-no 
                                          (- (.-length class-list) 1))
                                   :green)]
                 :disabled      (>= result-no (- (.-length class-list) 1))
                 :data-tooltip  "Scroll to the next result"
                 :data-position "bottom right"}
                ($ :i.icon.angle.double.right)))
          ($ :hr)
          ($ :a {:href "#"} "<< Back to Search")))))

;; File Viewer ----------------------------------------------------------------
(defui file-viewer
  "Main component to display a full text file and offer content search across 
   the file"
  [{{{:keys [id]} :path-params} :route}]
  (let [[search set-search-term!]            (persistent.state/with-local-storage 
                                               "booksearch/search-history" "")
        [mode set-mode!]                     (persistent.state/with-local-storage 
                                               "booksearch/mode" "logical")
        [search-results set-search-results!] (uix/use-state nil)
        [file set-file!]                     (uix/use-state nil)
        [loading toggle-loading]             (uix/use-state false)
        [error set-error!]                   (uix/use-state nil)]

    ;; Runs on mount only:: Retrieves File contents
    (uix/use-effect
     (fn [] (api/get-file-contents id 
                                   file
                                   loading
                                   set-file!
                                   toggle-loading))
     [loading file id])
    ;; Runs on search submit/update of search var
    (uix/use-effect
     (fn [] (api/get-search-results search
                                    mode
                                    id
                                    set-search-results!
                                    set-error!
                                    toggle-loading))
     [search mode id])

    ;; User Interface
    (if file
      ($ :<>
         ($ sui/segment
            {:class [:raised :very :padded :text]}
            (when error ($ ui/file-error error))
            ($ file-contents (assoc file :search-results search-results)))
         ($ search-results-list
            (assoc file
                   :loading loading
                   :toggle-loading toggle-loading
                   :search-results search-results
                   :mode mode
                   :set-mode set-mode!
                   :search search
                   :set-search-term set-search-term!)))
      ($ ui/loading-bar {:time-factor 5}))))
