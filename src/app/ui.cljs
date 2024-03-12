(ns app.ui
  (:require
   [clojure.string]
   [uix.core :as uix :refer [defui $]]
   [uix.dom]))

;; Header ---------------------------------------------------------------------
(defui header []
  ($ :header.app-header
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

