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
      :controllers []}]
    ["file/:id"
     {:name ::file
      :view file/file-viewer
      :parameters {:path {:id js/parseInt}}
      :controllers [{:parameters {:path [:id]}}]}]]
   {:data {:controllers []}}))

(defui current-page []
  (let [[match set-match!] (ui/use-state nil)]
    (ui/use-effect
     (fn []
       (when-not match 
         (rfe/start!
          routes
          (fn [new-match]
            (set-match!
             (fn [old-match]
               (when new-match
                 (assoc new-match
                        :controllers
                        (rfc/apply-controllers (:controllers old-match)
                                               new-match))))))
          {:use-fragment true}))))
    (when match
      (let [view (:view (:data match))]
        ($ view {:route match
                 :home (rfe/href ::frontpage {})})))))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn init []
  (uix.dom/render-root ($ current-page) root))
