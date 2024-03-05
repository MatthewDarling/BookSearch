# Loading files from disk on PostgreSQL
In order to read files off of disk and into our database, we are going to need to be able to read the contents of a directory.

Sampling from [[SO: How to list files inside of a folder](https://stackoverflow.com/questions/25413303/how-to-list-files-in-a-folder-from-inside-postgres)],
we will take the PLPGSQL flavour of the answer provided as such:
```
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
```

From that, we can use the built-in `pg_read_file` to read the file contents, like so:
```
SELECT pg_read_file(filename) AS text 
FROM ls_files_extended('/your/dir/public_domain_texts', NULL, 'filename') 
WHERE substring(filename, '.txt') <> '';
```

We can use `regexp_matches` to capture some internal metadata on the file, including title and author:
```
SELECT title[1], author[1], content[1]
FROM 
(SELECT pg_read_file(filename) AS text FROM ls_files_extended('/your/dir/public_domain_texts', NULL, 'filename') WHERE substring(filename, '.txt') <> '') AS file,
regexp_matches(file.text, 'Title: ([^\u000D]+)', 'g') AS title,
regexp_matches(file.text, 'Author: ([^\u000D]+)', 'g') AS author,
regexp_matches(file.text, '\*\*\* START OF THE PROJECT GUTENBERG EBOOK [^\u000D]+\u000D([\w|\W]+)\*\*\* END', 'g') AS content;
```

Using `ROW_NUMBER` over `regexp_split_to_table` we break apart each file into ~8000 word tokens, and then apply `row_number` again to provide each fragment with a sequence number:
```
SELECT ROW_NUMBER () OVER (PARTITION BY filename) as seq_no, filename, title, author, content
FROM (SELECT filename, title, MAX(author) as author, STRING_AGG(split, ' ') as content     
      FROM (SELECT filename, title[1], author[1], split, ROW_NUMBER () OVER () as wordno
            FROM (SELECT pg_read_file(filename) AS text, filename  
                  FROM ls_files_extended('/your/dir/s/public_domain_texts/test', NULL, 'filename') 
                  WHERE substring(filename, '.txt') <> '') AS file,
      regexp_matches(file.text, 'Title: ([^\u000D]+)', 'g') AS title,
      regexp_matches(file.text, 'Author: ([^\u000D]+)', 'g') AS author,
      regexp_matches(file.text, '\*\*\* START OF THE PROJECT GUTENBERG EBOOK [^\u000D]+\u000D([\w|\W]+)\*\*\* END OF THE PROJECT GUTENBERG EBOOK', 'g') AS content,
      regexp_split_to_table(content[1], '\s')  as split) as texts 
      GROUP BY filename, title, ROUND(wordno / 8000)
      ORDER BY filename, title, ROUND(wordno / 8000)) as r;
```
This will merit us a table in the following form:
| seq\_no |filename |title |author |content |
| --- | --- | --- | --- | --- |
| 1 |/Users/vermeer3030/Downloads/public\_domain\_texts/pg1727.txt |The Odyssey |Homer | The Odyssey    by Homer    rendered into English prose ... some of the headlines, and |
| 2 |/Users/vermeer3030/Downloads/public\_domain\_texts/pg1727.txt |The Odyssey |Homer |here advantage has fool nor coward  henceforward, for Ulysses never broke his word nor left ... Ulysses and of Penelope in your veins |
| 3 |/Users/vermeer3030/Downloads/public\_domain\_texts/pg1727.txt |The Odyssey |Homer | I see no likelihood of your succeeding. Sons are seldom as good men as  their fathers; ... for |

