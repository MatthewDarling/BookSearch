(ns app.core
  (:require
   [app.file :as file]
   [app.file-list :as file.list] 
   [uix.core :as uix :refer [$]]
   [uix.dom]))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn render []
  (uix.dom/render-root #_($ file/file-viewer) ($ file.list/file-list) root))

(defn ^:export init []
  (render))
