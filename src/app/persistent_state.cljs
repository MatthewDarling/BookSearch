(ns app.persistent-state
  (:require 
   [clojure.edn :as edn] 
   [uix.core :as uix]
   [uix.dom]))

(defn with-local-storage
  "Loads initial state from local storage and persists every updated state value
  Returns a tuple of the current state value and an updater function. This function
  will persist the type of the inital-value."
  [store-key initial-value]
  (let [[value set-value!] (uix/use-state (or (-> store-key
                                                  js/localStorage.getItem
                                                  edn/read-string) 
                                              initial-value))]
    (uix/use-effect
     (fn [] 
       (let [v (-> store-key
                   js/localStorage.getItem
                   edn/read-string)] 
         (set-value! v)))
     [store-key initial-value])
    (uix/use-effect
     (fn [] 
       (if (string? value) 
         (js/localStorage.setItem store-key (str \" value \"))
         (js/localStorage.setItem store-key (str value))))
     [value store-key]) 
    [value set-value!]))

