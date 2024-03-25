(ns app.persistent-state
  (:require 
   [clojure.edn :as edn] 
   [uix.core :as uix]
   [uix.dom]))

(defn -read-with-catch 
  [content] 
  (try 
    (edn/read-string content)
    (catch js/Error e
      "")))

(defn with-local-storage
  "Loads initial state from local storage and persists every updated state value
  Returns a tuple of the current state value and an updater function. This function
  will persist the type of the inital-value."
  [store-key initial-value] 
  (let [local-store-data (-> store-key
                             js/localStorage.getItem
                             -read-with-catch)
        [value set-value!] (uix/use-state (or local-store-data
                                              initial-value))]
    (uix/use-effect
     (fn []
       (try
         (let [v (-> store-key
                     js/localStorage.getItem
                     -read-with-catch)]
           (set-value! v))))
     [store-key initial-value])
    (uix/use-effect
     (fn []
       (if (string? value)
         (js/localStorage.setItem store-key (str \" value \"))
         (js/localStorage.setItem store-key (str value))))
     [value store-key])
    [value set-value!]))

