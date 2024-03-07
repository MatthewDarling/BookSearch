(ns app.file-list
  (:require
   [app.persistent-state :as persistent.state]
   [cljs.core.async :refer [<!]]
   [cljs-http.client :as http]
   [cljs.spec.alpha :as s]
   [clojure.set]
   [uix.core :as uix :refer [defui $]]
   [uix.dom])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Specs ----------------------------------------------------------------------
(s/def :file/content string?)
(s/def :file/title string?)
(s/def :file/author string?)
(s/def :list/document (s/keys :req-un [:file/content :file/title :file/author]))

;; Header ---------------------------------------------------------------------
(defui header []
  ($ :header.app-header
     ($ :h1 "Book Search")
     ($ :h2 "Demonstration of Full-Text Search on Large Texts")))

;; Search Bar -----------------------------------------------------------------
(defui text-field [{:keys [on-search initial-value]}]
  (let [[value set-value!] (uix/use-state (js/parseInt initial-value))]
    ($ :div.text-input-wrapper
       ($ :input.text-input
       ;; - temp ---
          {:value (if (> value 0) value initial-value)
           :type :number
           :placeholder "Enter a search term"
           :on-change (fn [^js e]
                        (set-value! (.. e -target -value)))
           :on-key-down (fn [^js e]
                          (when (= "Enter" (.-key e))
                            (on-search (if (> value 0) value initial-value))
                            (set-value! "")))}))))

;; List Item ------------------------------------------------------------------
(defui list-item
  [{:keys [content title author] :as props}]
  ;; = Temp --
  {:pre [(s/valid? :list/document props)]}
  ($ :.file
     ($ :div.file-header
        {:key title}
        ($ :h3 title)
        ($ :h4 author))
     ($ :span.file-text content)))

;; API Request ----------------------------------------------------------------
(defn make-remote-call [endpoint callback]
  (go (let [response (<! (http/get endpoint 
                                   {:on-success js/console.log}))]
        (callback (-> response
                      :body)))))

;; Application ----------------------------------------------------------------
(defui file-list []
  (let [[search set-search-term!] (persistent.state/use-persistent-state "booksearch/search-history" "")
        [files set-files!] (persistent.state/use-persistent-state "booksearch/documents" [])
        on-search (fn [search-term]
                    (set-search-term! search-term)
                    (make-remote-call (str "https://jsonplaceholder.typicode.com/posts/" search-term)
                                      (fn [response]
                                        (set-files! #(vector response)))))]
    ($ :.app
       ($ header)
       ($ text-field {:initial-value search
                      :on-search on-search})
       (for [file files]
         ($ list-item
            ; - Temp --
            (assoc (clojure.set/rename-keys file {:body :content})
                   :author "C. Thomas Howell"))))))


