(ns app.html
  (:require
   [cljs.reader]
   [cljs.spec.alpha :as s]
   [clojure.set]
   [clojure.string]))

;; Specs ----------------------------------------------------------------------
(s/def :files/content string?)

(defn clean-content 
  "Given plain text, adds chapter headings to text, and exchanges line return 
   character into html line breaks." 
  [content]
  {:pre [(s/valid? :files/content content)]}
  (-> content 
      (clojure.string/replace #"\n[Cc][Hh][Aa][Pp][Tt][Ee][Rr]\s[^\n]+"
                              #(str "<div class='chapter-title'>" %1 "</div>"))
      (clojure.string/replace "\n\r" 
                              "\n&nbsp;<br>&nbsp;<br>")))

(defn highlight-search-results
  "Adds html spans with the search-result class to the content.
   Accepts search-results as a collection of maps with the :term key, where each
   term represents an exact match within a document."
  [search-results content]
  (when content
    (reduce (fn [text term] 
              (clojure.string/replace text
                                      (str " " term)
                                      #(str " <span class='search-result'>"
                                            (clojure.string/trim %1)
                                            "</span> ")))
            content
            (->> search-results
                 distinct
                 (sort-by (comp count :term))
                 reverse
                 (keep :term)))))

(defn link->file
  "Given a headline as text with <b> tag annotations, converts the <b>
   tags into anchors with links to the fil profile page and route."
  [headline file_id]
  (some-> headline
          (clojure.string/replace "<b>"
                                  (str "<a class='file-link' href='#file/" file_id "'>"))
          (clojure.string/replace "</b>"
                                  "</a>")))
