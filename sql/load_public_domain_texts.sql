INSERT INTO files (filename, title, author, content)
SELECT filename, title, author, content
FROM (SELECT filename, title, MAX(author) as author, STRING_AGG(split, ' ') as content     
      FROM (SELECT filename, title[1], author[1], split, ROW_NUMBER () OVER () as wordno
            FROM (SELECT pg_read_file((:filename)::TEXT) AS text, (:filename)::TEXT AS filename) AS file,
      regexp_matches(file.text, 'Title: ([^\u000D]+)', 'g') AS title,
      regexp_matches(file.text, 'Author: ([^\u000D]+)', 'g') AS author,
      regexp_matches(file.text, '\*\*\* START OF THE PROJECT GUTENBERG EBOOK [^\u000D]+\u000D([\w|\W]+)\*\*\* END OF THE PROJECT GUTENBERG EBOOK', 'g') AS content,
      regexp_split_to_table(content[1], '\s')  as split) as texts 
      GROUP BY filename, title, ROUND(wordno / 8000000000)
      ORDER BY filename, title, ROUND(wordno / 8000000000)) as r;
