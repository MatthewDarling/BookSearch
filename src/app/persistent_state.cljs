(ns app.persistent-state
  (:require 
   [clojure.edn :as edn] 
   [uix.core :as uix]
   [uix.dom]))

(defn use-persistent-state
  "Loads initial state from local storage and persists every updated state value
  Returns a tuple of the current state value and an updater function. This function
  will persist the type of the inital-value."
  [store-key initial-value]
  (let [[value set-value!] (uix/use-state initial-value)]
    (uix/use-effect
     (fn []
       (let [v (edn/read-string (js/localStorage.getItem store-key))]
         (set-value! #(identity v))))
     [store-key])
    (uix/use-effect
     (fn []
       (js/localStorage.setItem store-key (str value)))
     [value store-key])
    [value set-value!]))
