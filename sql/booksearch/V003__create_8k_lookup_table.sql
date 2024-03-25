CREATE TABLE IF NOT EXISTS file_lookup_8k
(file_id INTEGER REFERENCES files(file_id) ON DELETE CASCADE,
 sequence_no INTEGER,
 content_array TEXT[] NOT NULL,
 content_tsv TSPVECTOR,
 content TEXT);

CREATE INDEX "fl_8k_tsv_idx" ON file_lookup_8k USING GIN (content_tsv tsvector_ops);

 -- Separate long strings into smaller tsvectors
-- Need to avoid all of these issues: 
--   https://www.postgresql.org/docs/14/textsearch-limitations.html
-- Trigger Function for AFTER UPDATE/INSERT on file_text
-- Clear any existing TSVector records, and add the smaller chunks to the index
CREATE OR REPLACE FUNCTION tfn_split_file_to_indices_to_8k() RETURNS trigger
AS $$
DECLARE num_spaces INT;
BEGIN
  DELETE FROM file_lookup_8k WHERE file_id = NEW.file_id;

  INSERT INTO file_lookup_8k (file_id, sequence_no, content_tsv, content_array, content)
  SELECT file_id, ROW_NUMBER() OVER (), to_tspvector(content), TO_TSP_TEXT_ARRAY(content), content
    FROM (SELECT NEW.file_id, LARGE_TEXT_TO_TSVECTORS('english', NEW.content, 13::SMALLINT) as content) AS frags
   WHERE content IS NOT NULL;

  RETURN NULL;
END;
$$
LANGUAGE plpgsql;

-- Add the trigger to the files table file_text column
CREATE TRIGGER t_update_file_text_8k_tsv
AFTER INSERT OR UPDATE OF content ON files
FOR EACH ROW EXECUTE FUNCTION tfn_split_file_to_indices_to_8k();
