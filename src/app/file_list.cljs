(ns app.file-list
  (:require
   [app.api :as api]
   [app.persistent-state :as persistent.state] 
   [app.semantic-ui :as sui]
   [app.ui :as ui] 
   [cljsjs.semantic-ui-react]
   [cljs.reader]
   [cljs.spec.alpha :as s]
   [clojure.set]
   [clojure.string]
   
   [uix.core :as uix :refer [defui $]]
   [uix.dom]))

;; Specs ----------------------------------------------------------------------
(s/def :files/file_id int?)
(s/def :files/title string?)
(s/def :files/author string?)
(s/def :list/document (s/keys :req [:files/file_id
                                    :files/title
                                    :files/author]))

(defn search-handler
  "Generates a response handler function for file list search.
   Accepts:
   - set-files! - a function to update a new file list into
   - set-state! - a function to update state to track errors loading as boolean
                  state, and the time-start/end of the request."
  [set-state!]
  (fn [response]
    (let [resp (cljs.reader/read-string response)] 
      (set-state!
       (fn [s] (assoc s
                      :error nil
                      :loading false
                      :files resp
                      :time-end (js/Date.now)))))))

(defn search-error
  "Generates a response handler function for file list search.
   Accepts:
   - set-files! - a function to update a new file list into
   - set-state! - a function to update state to track errors loading as boolean
                  state, and the time-start/end of the request."
  [set-state!]
  (fn [response]
    (set-state!
     (fn [s] (assoc s
                    :error response
                    :loading false
                    :files []
                    :time-end (js/Date.now))))))

(defn link->file
  "Given a headline as text with <b> tag annotations, converts the <b>
   tags into anchors with links to the fil profile page and route."
  [headline file_id]
  (some-> headline
          (clojure.string/replace "<b>"
                                  (str "<a class='file-link' href='#file/" file_id "'>"))
          (clojure.string/replace "</b>"
                                  "</a>")))

;; List Item ------------------------------------------------------------------
(defui list-item
  "UI representation of a line-item in the file search results. Presents "
  [{:files/keys [file_id]
    :keys [headline]
    :as props}]
  ;; need to update the spec a bit
  {:pre [(s/valid? :list/document props)]}
  ($ :.file
     {:key (str "file" (or file_id 0))}
     ($  ui/file-header props) 
     ($ :article.file-text
        {:dangerouslySetInnerHTML
         {:__html (link->file headline file_id)}})))

(defn search-from-state
  [search mode strategy set-state!]
  (api/make-remote-call (str "http://localhost:3000/api/list?query="
                             (js/encodeURIComponent search)
                             "&query_mode=" mode
                             "&strategy=" strategy)
                        (search-handler set-state!)
                        (search-error set-state!)))

;; File List ----------------------------------------------------------------
(defui file-list
  "Main File Search and List component. 
   Accepts router props but do not expect anything from them.
   Draws a file list while holding the user search term in persistent state,
   under the key booksearch/search-history, and our search strategy as 
   booksearch/strategy. "
  [_]
  (let [[search set-search-term!]
        (persistent.state/with-local-storage "booksearch/search-history" "")
        [strategy set-strategy!]
        (persistent.state/with-local-storage "booksearch/strategy" "ts_fast_headline")
        [mode set-mode!]
        (persistent.state/with-local-storage "booksearch/mode" "logical")
        [state set-state!]
        (uix/use-state {:loading false :error nil})]
    (uix/use-effect
     (fn []
       (set-state! (fn [s] (assoc s
                                  :files []
                                  :loading true
                                  :time-start (js/Date.now))))
       (search-from-state search mode strategy set-state!))
     [search strategy mode])
    ($ sui/segment
       {:class [:app :raised :very :padded :text :form]}
       ($ ui/header)
       ($ sui/grid
          {:class [:input-wrapper]}
          ($ sui/row
             {:class :z-index-force-overlay}
             ($ ui/text-field
                {:initial-value search
                 :on-search set-search-term!
                 :loading (:loading state)})
             ($ ui/query-mode-options
                {:on-set-mode set-mode!
                 :mode mode
                 :loading (:loading state)}))
          ($ sui/row
             ($ ui/strategy-radio-options
                {:on-set-strategy set-strategy!
                 :strategy strategy})
             ($ ui/query-stats state {})))
       (if (:loading state)
         ($ :<>
            ($ ui/loading-bar)
            (for [i (range 3)]
              ($ ui/file-placeholder {:key (keyword :placeholder i)}))
            ($ :hr))
         (if (seq (:files state))
           (for [file (:files state)] ($ list-item file))
           ($ :.ui.info.message
              ($ :.header "No results returned")
              "No results match your "
              ($ :b mode)
              " search for "
              ($ :b search)
              " using "
              ($ :b strategy))))
       (when (:error state)
         ($ :.error
            ($ :h3 "Uh-oh! There was an error!")
            (str (:error state)))))))
