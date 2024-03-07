(ns app.ui
  (:require 
   [uix.core :as uix :refer [defui $]]
   [uix.dom]))

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

