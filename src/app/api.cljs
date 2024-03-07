(ns app.api
  (:require 
   [cljs.core.async :refer [<!]]
   [cljs-http.client :as http] 
   [clojure.set] 
   [uix.dom])
  (:require-macros [cljs.core.async.macros :refer [go]]))


;; API Request ----------------------------------------------------------------
(defn make-remote-call [endpoint callback]
  (go (let [response (<! (http/get endpoint))]
        (callback (-> response
                      :body)))))
