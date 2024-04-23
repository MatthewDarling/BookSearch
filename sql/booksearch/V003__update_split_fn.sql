 -- Separate long strings into smaller tsvectors
-- Need to avoid all of these issues:
--   https://www.postgresql.org/docs/14/textsearch-limitations.html
-- Trigger Function for AFTER UPDATE/INSERT on file_text
-- Clear any existing TSVector records, and add the smaller chunks to the index
CREATE OR REPLACE FUNCTION tfn_split_file_to_indices() RETURNS trigger
AS $$
DECLARE num_spaces INT;
BEGIN
  DELETE FROM file_lookup_16k WHERE file_id = NEW.file_id;

  INSERT INTO file_lookup_16k (file_id, sequence_no, content_tsv, content_array, content)
  SELECT file_id, ROW_NUMBER() OVER (), to_tspvector(content), TO_TSP_TEXT_ARRAY(content), content
    FROM (SELECT NEW.file_id, LARGE_TEXT_TO_TSVECTORS('english'::REGCONFIG, NEW.content) as content) AS frags
   WHERE content IS NOT NULL;

  RETURN NULL;
END;
$$
LANGUAGE plpgsql;
