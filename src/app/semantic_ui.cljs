(ns app.semantic-ui
  (:require
   [cljsjs.semantic-ui-react]
   [goog.object]))

(def semantic-ui js/semanticUIReact)

(defn component
  "Get a component from sematic-ui-react:

    (component \"Button\")
    (component \"Menu\" \"Item\")"
  [k & ks]
  (if (seq ks)
    (apply goog.object/getValueByKeys semantic-ui k ks)
    (goog.object/get semantic-ui k)))

(def button (component "Button"))
(def buttons (component "ButtonGroup"))
(def card (component "Card"))
(def cards (component "Cards"))
(def container (component "Container"))

(def grid (component "Grid"))
(def header (component "Header"))
(def input (component "Input"))
(def message (component "Message"))
(def label (component "Label"))
(def placeholder (component "Placeholder"))
(def radio (component "Radio"))
(def ribbon (component "Ribbon"))
(def rail (component "Rail"))
(def search (component "Search"))
(def segment (component "Segment"))
(def h3 :h3.ui.header)

(def h4 :h4.ui.header)
(def blue-bar :.bar.blue)
(def content :.content)
(def file-loader :.ui.active.progress.indicating.blue.file-loader)
(def form :.ui.form)
(def row :.row)

;; Icons ----------------------------------------
(def book-icon :i.book.icon)
(def circle-icon :i.circle.outline.icon)
(def play-icon :i.icon.play.circle.outline)
(def tach-icon :i.tachometer.alternate.icon)

