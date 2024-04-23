(ns app.api
  (:require 
   [ajax.core :refer [GET]] 
   [cljs.reader]
   [clojure.set]
   [clojure.walk]
   [uix.dom]))

;; API Request ----------------------------------------------------------------
(defn make-remote-call [endpoint callback error-handler]
  (GET endpoint 
    {:handler callback
     :error-handler error-handler}))

(defn get-file-contents
  [id file loading set-file! toggle-loading] 
  (when-not (or loading file)
    (toggle-loading true)
           ;; Load File Content
    (make-remote-call
     (str "http://localhost:3000/api/file?file_id=" id)
     (fn [response]
       (set-file! #(first (js->clj response :keywordize-keys true))))
     js/console.error)))

(defn get-search-results
  [search mode id set-search-results! set-error! toggle-loading] 
  (toggle-loading true)
         ;; Get Search Results
  (make-remote-call
   (str "http://localhost:3000/api/search?file_id=" id
        "&query=" (js/encodeURIComponent search)
        "&query_mode=" mode)
   (fn [response]
     (let [resp (-> response js->clj clojure.walk/keywordize-keys)]
       (set-search-results! (->> resp
                                 distinct
                                 (sort-by (comp count :term))
                                 reverse))
       (set-error! nil)))
   (fn [resp]
     (set-search-results! [])
     (set-error! (str resp)))))

(defn search-handler
  "Generates a response handler function for file list search.
   Accepts:
   - set-files! - a function to update a new file list into
   - set-state! - a function to update state to track errors loading as boolean
                  state, and the time-start/end of the request."
  [set-state!]
  (fn [response]
    (let [resp (-> response js->clj clojure.walk/keywordize-keys)]
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

(defn get-file-list
  [search mode strategy set-state!]
  (make-remote-call (str "http://localhost:3000/api/list?query="
                         (js/encodeURIComponent search)
                         "&query_mode=" mode
                         "&strategy=" strategy)
                    (search-handler set-state!)
                    (search-error set-state!)))