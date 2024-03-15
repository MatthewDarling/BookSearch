INSERT INTO files (filename, title, author, content)
SELECT filename, title[1], author[1], content[1]
            FROM (SELECT pg_read_file((:filename)::TEXT) AS text, (:filename)::TEXT AS filename) AS file,
      regexp_matches(file.text, 'Title: ([^\u000D]+)', 'g') AS title,
      regexp_matches(file.text, 'Author: ([^\u000D]+)', 'g') AS author,
      regexp_matches(file.text, '\*\*\* START OF THE PROJECT GUTENBERG EBOOK [^\u000D]+\u000D([\w|\W]+)\*\*\* END OF THE PROJECT GUTENBERG EBOOK', 'g') AS content;