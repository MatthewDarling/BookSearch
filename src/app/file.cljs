(ns app.file
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


;; File Profile ------------------------------------------------------------------
(defui file-profile
  [{:keys [file_id content title author] :as props}]
  ;; = Temp --
  ;;{:pre [(s/valid? :list/document props)]} 
  ($ :.file
     ($ :div.file-header
        {:key file_id}
        ($ :h3 file_id ". " title)
        ($ :h4 author))
     ($ :span.file-text content)))

;; File Viewer ----------------------------------------------------------------
(defui file-viewer []
  (let [[search set-search-term!] (persistent.state/with-local-storage "booksearch/search-history" "")
        [file set-file!] (persistent.state/with-local-storage "booksearch/document-record" {})
        on-search (fn [search-term]
                    (set-search-term! search-term)
                    (api/make-remote-call (str "https://jsonplaceholder.typicode.com/posts/" search)
                                          (fn [response]
                                            (set-file! #(identity response)))))]
    ($ ui/header)
    ($ ui/text-field {:initial-value search
                          :on-search on-search})
    (when file
      ($ file-profile
                   ; - Temp --
         (assoc (select-keys (clojure.set/rename-keys file {:body :content}) [:content :title])
                :author "C. Thomas Howell"
                :file_id 2)))))


