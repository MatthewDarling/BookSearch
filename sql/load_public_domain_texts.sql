CREATE OR REPLACE FUNCTION ls_files_extended(path text, filter text default null, sort text default 'filename')
    RETURNS TABLE(filename text) AS
$BODY$
BEGIN
  SET client_min_messages TO WARNING;
  CREATE TEMP TABLE _files(filename text) ON COMMIT DROP;

  EXECUTE format($$COPY _files FROM PROGRAM 'find %s '$$, path);

  RETURN QUERY EXECUTE format($$SELECT * FROM _files WHERE %s ORDER BY %s $$, concat_ws(' AND ', 'true', filter), sort);
END;
$BODY$ LANGUAGE plpgsql SECURITY DEFINER;


-- INSERT ---------------------------------------------------------------------

INSERT INTO files (filename, title, author, content)
SELECT filename, title, author, content
FROM (SELECT filename, title, MAX(author) as author, STRING_AGG(split, ' ') as content     
      FROM (SELECT filename, title[1], author[1], split, ROW_NUMBER () OVER () as wordno
            FROM (SELECT pg_read_file(filename) AS text, filename  
                  FROM ls_files_extended((:path)::TEXT, NULL, 'filename') 
                  WHERE substring(filename, '.txt') <> '') AS file,
      regexp_matches(file.text, 'Title: ([^\u000D]+)', 'g') AS title,
      regexp_matches(file.text, 'Author: ([^\u000D]+)', 'g') AS author,
      regexp_matches(file.text, '\*\*\* START OF THE PROJECT GUTENBERG EBOOK [^\u000D]+\u000D([\w|\W]+)\*\*\* END OF THE PROJECT GUTENBERG EBOOK', 'g') AS content,
      regexp_split_to_table(content[1], '\s')  as split) as texts 
      GROUP BY filename, title, ROUND(wordno / 8000000000)
      ORDER BY filename, title, ROUND(wordno / 8000000000)) as r;


DROP FUNCTION ls_files_extended;