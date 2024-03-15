(ns app.core
  (:require
   [app.file :as file]
   [app.file-list :as file.list]
   [reitit.frontend :as rf]
   [reitit.frontend.easy :as rfe]
   [reitit.frontend.controllers :as rfc]
   [uix.core :as ui :refer [defui $]]
   [uix.dom]))

(defn log-fn [& params]
  (fn [_]
    (apply js/console.log params)))

(def routes
  (rf/router
   [["/"
     {:name ::frontpage
      :view file.list/file-list
      :controllers [{:start (log-fn "start" "frontpage controller")
                     :stop (log-fn "stop" "frontpage controller")}]}]
    ["file/:id"
     {:name ::file
      :view file/file-viewer
      :parameters {:path {:id js/parseInt}}
      :controllers [{:parameters {:path [:id]}
                     :start (fn [{:keys [path]}]
                              (log-fn "start" "item controller" (:id path)))
                     :stop (fn [{:keys [path]}]
                             (log-fn "stop" "item controller" (:id path)))}]}]]
   {:data {:controllers [{:start (log-fn "start" "root-controller")
                          :stop (log-fn "stop" "root controller")}]}}))

(defui current-page []
  (let [[match set-match!] (ui/use-state nil)
        _ (ui/use-effect
           (fn [] (rfe/start!
                   routes
                   (fn [new-match] 
                     (set-match! (fn [old-match]
                                   (when new-match
                                     (assoc new-match 
                                            :controllers
                                            (rfc/apply-controllers (:controllers old-match) 
                                                                   new-match))))))
                   {:use-fragment true})))] 
    (when match
      (let [view (:view (:data match))]
        ($ view {:route match
                 :home (rfe/href ::frontpage {})})))))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn init []
  (uix.dom/render-root ($ current-page) root))
