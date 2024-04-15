;;; A Babashka script to create Flyway migrations for the custom PG extensions
;;; To do: provide just the folder and avoid hardcoding the full SQL file name
;;;

(require '[babashka.fs :as fs])
(require '[babashka.process :as proc])
(require '[clojure.string :as string])

(defn str->int
  [input-str]
  (try
    (Integer/parseInt input-str)
    (catch Exception e
      nil)))

(defn file-md5
  [filename]
  (-> (proc/shell {:out :string} "md5sum" filename)
      :out
      (string/split #" ")
      first))

(defn migration-number
  "Flyway migration names are traditionally formatted as:
     V001__create_foo_table.sql
  So this function retrieves the `001' part."
  [migration-filename]
  (-> migration-filename
      (string/split #"__")
      first
      (subs 1)))

(defn latest-migration-number
  "Find the numerically largest existing migration number string. Given properly
  padded version strings, sorting works correctly out of the box."
  [migrations-folder]
  (->> migrations-folder
       fs/list-dir
       (map (comp migration-number fs/file-name))
       sort
       last))

(defn new-migration-number
  [latest-version-string]
  (->> latest-version-string
       str->int
       inc
       (format "%03d")))

(defn migration-required-for-extension?
  "Return true if no migration in `migrations-folder' matches the file content of
  `compiled-extension-path'. If a prior migration implements an older version of
  the same extension, determined by MD5 hash being unequal, then return true as
  they simply define functions idempotently."
  [{:keys [migrations-folder compiled-extension-path]}]
  (let [known-migration-hashes (->> migrations-folder fs/list-dir (map file-md5) (into #{}))]
    (nil? (known-migration-hashes (file-md5 compiled-extension-path)))))

(defn ensure-extension-converted-to-migration
  "Given the migrations folder and a simple .sql file containing an extension,
  create a Flyway formatted and numbered .sql file to apply that extension, if
  an equivalent migration doesn't already exist."
  [{:keys [migrations-folder compiled-extension-path] :as opts}]
  (when (migration-required-for-extension? opts)
    (let [migration-number (new-migration-number (latest-migration-number migrations-folder))
          new-migration-filename (str "V"
                                      migration-number
                                      "__install_latest_"
                                      (fs/parent compiled-extension-path)
                                      ".sql")]
      (fs/copy compiled-extension-path
               (str "sql/booksearch/" new-migration-filename)))))

(doseq [extension-path ["pg_ts_semantic_headline/tsp_semantic_headline--1.0.sql"
                        "pg_large_text_search/pg_large_text_search--1.0.sql"]]
  (ensure-extension-converted-to-migration {:migrations-folder "sql/booksearch"
                                            :compiled-extension-path extension-path}))
