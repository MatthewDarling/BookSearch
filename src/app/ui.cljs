(ns app.ui
  (:require
   [clojure.string]
   [uix.core :as uix :refer [defui $]]
   [uix.dom]))

;; Header ---------------------------------------------------------------------
(defui header []
  ($ :header.app-header.chapter-title 
     ($ :h1 "Book Search")
     ($ :h2 "Demonstration of Full-Text Search on Large Texts")))

;; Search Bar -----------------------------------------------------------------
(defui text-field [{:keys [on-search initial-value]}]
  (let [[value set-value!] (uix/use-state initial-value)]
    ($ :div.text-input-wrapper 
       ($ :input.text-input
          {:value value
           :placeholder "Enter a search term"
           :on-change (fn [^js e]
                        (set-value! (.. e -target -value)))
           :on-key-down (fn [^js e]
                          (when (= "Enter" (.-key e))
                            (on-search value)))}))))

(defui strategy-radio
  [{:keys [strategy value on-set-strategy]}]
  ($ :<>
     ($ :input
        {:type :radio
         :name :rad-strategy
         :on-change (fn [^js e]
                      (on-set-strategy (.. e -target -value)))
         :value value
         :checked (= strategy value)})
     ($ :label {:for value} value)))


(defui strategy-radio-options [{:keys [strategy on-set-strategy]}]
  (let [strategy (cond-> strategy (keyword? strategy) name)]
    ($ :div.strategy
       ($ strategy-radio
          {:value "ts_fast_headline"
           :on-set-strategy on-set-strategy
           :strategy strategy})
       ($ strategy-radio
          {:value "ts_semantic_headline"
           :on-set-strategy on-set-strategy
           :strategy strategy})
       ($ strategy-radio
          {:value "ts_headline"
           :on-set-strategy on-set-strategy
           :strategy strategy}))))

(defui mode-radio
  [{:keys [strategy value on-set-mode]}]
  ($ :<>
     ($ :input
        {:type :radio
         :name :rad-mode
         :on-change (fn [^js e]
                      (on-set-mode (.. e -target -value)))
         :value value
         :checked (= strategy value)})
     ($ :label {:for value} value)))

(defui query-mode-options [{:keys [mode on-set-mode]}]
  ($ :div.mode
     ($ mode-radio
        {:value "phrase"
         :on-set-mode on-set-mode
         :strategy mode})
     ($ mode-radio
        {:value "logical"
         :on-set-mode on-set-mode
         :strategy mode})))

(defn loading-bar [_]
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


(defui query-stats [{:keys [time-start time-end]}]
  ($ :.stats "Time to query: "
     (if (> (- time-end time-start) 0)
       ($ :.elapsed (- time-end time-start) "ms")
       "-")))

(defui file-header
  "Ui Component to convert a plain text (large) blob of text into HTML"
  [{:files/keys [file_id title author]}]
  ($ :header
     {:key (keyword file_id) :class "file-title"}
     ($ :h3 title)
     ($ :h4 author)))