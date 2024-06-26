(ns load-gutenberg
  "A Babashka script to upload parsed ebooks to Postgres")

(require '[babashka.pods :as pods])
(pods/load-pod 'org.babashka/postgresql "0.1.0")
(require '[babashka.deps :as deps])
(deps/add-deps '{:deps {com.github.seancorfield/honeysql {:mvn/version "2.2.861"}}})
(require '[babashka.fs :as fs])
(require '[clojure.string :as string])
(require '[honey.sql :as hsql])
(require '[pod.babashka.postgresql :as pg])

(def db-config
  {:dbtype   "postgresql"
   :host     (or (System/getenv "DB_HOST") "localhost")
   :dbname   (or (System/getenv "DB_NAME") "booksearch")
   :user     (or (System/getenv "DB_USER") "booksearch")
   :password (or (System/getenv "DB_PASS") "bookitysearch")
   :port     (or (System/getenv "DB_PORT") 5432)})

(defn files-to-process
  [target-dir]
  (fs/list-dir target-dir "*.txt"))

(defn text-pattern-for-ebook
  [title]
  (let [prelude (str "\\*\\*\\* START OF THE PROJECT GUTENBERG EBOOK " (string/upper-case title) " \\*\\*\\*")
        closing (str "\\*\\*\\* END OF THE PROJECT GUTENBERG EBOOK " (string/upper-case title) " \\*\\*\\*")]
      (re-pattern (str "(?s)" prelude "(.+)" closing))))

(defn parse-gutenberg-txt
  [txt-path]
  (let [file-contents (String. (fs/read-all-bytes txt-path) "UTF-8")
        title (second (re-find #"Title: (.+)\R" file-contents))
        author (second (re-find #"Author: (.+)\R" file-contents))
        book-text (second (re-find (text-pattern-for-ebook title) file-contents))]
    {:filename (fs/file-name txt-path)
     :title title
     :author author
     :content book-text}))

(defn upload-ebook-to-rds!
  [db-config ebook]
  (pg/execute! db-config (hsql/format {:insert-into [:files] :values [ebook]})))

(defn upload-all!
  []
  (doseq [ebook (files-to-process "public_domain_texts")]
    (upload-ebook-to-rds! db-config (parse-gutenberg-txt ebook))
    (println "Finished uploading" ebook))
  (println "Upload complete"))

(defn clear-db!
  []
  (pg/execute! db-config ["DELETE FROM files;"]))

(defn -main [& args]
  (upload-all!))
