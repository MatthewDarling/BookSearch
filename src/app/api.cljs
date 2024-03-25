(ns app.api
  (:require 
   [ajax.core :refer [GET]] 
   [clojure.set] 
   [uix.dom]))

;; API Request ----------------------------------------------------------------
(defn make-remote-call [endpoint callback error-handler]
  (GET endpoint 
    {:handler callback
     :error-handler error-handler}))
