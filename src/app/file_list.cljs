(ns app.file-list
  (:require
   [app.api :as api]
   [app.html :as html]
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

;; List Item ------------------------------------------------------------------
(defui list-item
  "UI representation of a line-item in the file search results. Presents "
  [{:files/keys [file_id]
    :keys       [headline]
    :as         props}]
  ;; need to update the spec a bit
  {:pre [(s/valid? :list/document props)]}
  ($ :.file
     {:key (str "file" (or file_id 0))}
     ($  ui/file-header props) 
     ($ :article.file-text
        {:dangerouslySetInnerHTML {:__html (html/link->file headline file_id)}})))

;; File List ----------------------------------------------------------------
(defui file-list
  "Main File Search and List component. 
   Accepts router props but do not expect anything from them.
   Draws a file list while holding the user search term in persistent state,
   under the key booksearch/search-history, and our search strategy as 
   booksearch/strategy. "
  [_]
  (let [[search set-search-term!] (persistent.state/with-local-storage 
                                    "booksearch/search-history" "")
        [strategy set-strategy!]  (persistent.state/with-local-storage 
                                    "booksearch/strategy" "ts_fast_headline")
        [mode set-mode!]          (persistent.state/with-local-storage
                                    "booksearch/mode" "logical")
        [state set-state!]        (uix/use-state {:loading false
                                                  :error   nil})]
    (uix/use-effect
     (fn []
       (set-state! (fn [s] (assoc s
                                  :files []
                                  :loading true
                                  :time-start (js/Date.now))))
       (api/get-file-list search mode strategy set-state!))
     [search strategy mode])
    ($ sui/segment
       {:class [:app :raised :very :padded :text]}
       ($ ui/header)
       ($ sui/grid
          {:class [:input-wrapper]}
          ($ sui/row
             {:class :z-index-force-overlay}
             ($ ui/text-field
                {:initial-value search
                 :on-search     set-search-term!
                 :has-error     (:error state)
                 :loading       (:loading state)})
             ($ ui/query-mode-options
                {:on-set-mode set-mode!
                 :mode        mode
                 :loading     (:loading state)}))
          ($ sui/row
             ($ ui/strategy-radio-options
                {:on-set-strategy set-strategy!
                 :strategy        strategy})
             ($ ui/query-stats state {})))
       (if (:loading state)
         ($ :<>
            ($ ui/loading-bar {:time-factor 20})
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
