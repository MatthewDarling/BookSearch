(ns app.core
  (:require
   [app.file :as file]
   [app.file-list :as file.list]
   [reitit.frontend :as rf]
   [reitit.frontend.easy :as rfe]
   [reitit.frontend.controllers :as rfc]
   [uix.core :as ui :refer [defui $]]
   [uix.dom]))

(def routes
  (rf/router
   [["/"
     {:name        ::frontpage
      :view        file.list/file-list}]
    ["file/:id"
     {:name        ::file
      :view        file/file-viewer
      :parameters  {:path {:id js/parseInt}}
      :controllers [{:parameters {:path [:id]}}]}]]))

(defui current-page []
  (let [[match set-match!] (ui/use-state nil)
        update-match (fn [new-match]
                      (set-match!
                       (fn [old-match]
                         (some->> new-match
                                  (rfc/apply-controllers (:controllers old-match))
                                  (assoc new-match :controllers)))))]
    (ui/use-effect
     (fn []
       (when-not match
         (rfe/start!
          routes
          update-match
          {:use-fragment true}))))
    (when match
      (let [view (:view (:data match))]
        ($ view {:route match
                 :home (rfe/href ::frontpage {})})))))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn init []
  (uix.dom/render-root ($ current-page) root))
