/* function: LTSV_LARGE_TEXT_TO_INDEXABLE_FRAGMENTS

Low-level implementation of string splitting for TSVector parsing
compliance. 

DO NOT CALL THESE FUNCTIONS DIRECTLY. 

Prefer: 
`LARGE_TEXT_TO_TSVECTORS ([REGCONFIG,] TEXT)`

Accepts string and left_overlap, the latter is the number of space-separated 
*/

-- 2-arity helper function not to be called directly
-- words
-- from the end of the previous string which the new string will contain
CREATE OR REPLACE FUNCTION LTSV_LARGE_TEXT_TO_INDEXABLE_FRAGMENTS
(string TEXT, left_overlap INT) 
RETURNS TABLE (fragment TEXT)
AS $$
DECLARE cutpoint INT;
DECLARE word_arr TEXT[];
BEGIN
  SELECT REGEXP_SPLIT_TO_ARRAY(string, '\s+') 
    INTO word_arr;

  SELECT LEAST(((ARRAY_LENGTH(word_arr, 1) + 1) / 2) + 1, 
               (2^14 - 2) - left_overlap) 
    INTO cutpoint;

  RETURN QUERY
  (SELECT array_to_string((word_arr)[(cutpoint * (idx - 1)) - (left_overlap - 1):
                                     (cutpoint * idx) 
                                     + (left_overlap 
                                        * (CASE WHEN (idx = 1) THEN 1 
                                           ELSE 0 
                                           END))], 
                          ' ')
     FROM generate_series(1, (ARRAY_LENGTH(word_arr, 1) / cutpoint) + 1) AS idx);
END;
$$
STABLE
LANGUAGE plpgsql;

-- A 1-arity helper function not to be called directly
-- Takes a string and splits it into many smaller chunks that match the TSVector 
-- limitations
-- Each will also overlap 32 words with the previous chunk
CREATE OR REPLACE FUNCTION LTSV_LARGE_TEXT_TO_INDEXABLE_FRAGMENTS
(string TEXT) 
RETURNS TABLE (fragment TEXT)
AS $$
DECLARE num_spaces INT;
BEGIN
  RETURN QUERY (SELECT LTSV_LARGE_TEXT_TO_INDEXABLE_FRAGMENTS(string, 32));
END;
$$
STABLE
LANGUAGE plpgsql;
/* function: LARGE_TEXT_TO_TSVECTORS

Separate long strings into smaller strings, each of which will parse to 
valid and conformant tsvectors.
- Need to avoid all of these issues: 
  https://www.postgresql.org/docs/14/textsearch-limitations.html
- Higher-level function to handle the details of separating long text into 
  smaller pieces for TSVectors.

Returns a recordset of strings, each of which is plain text for a valid TSVector
For indexing, We store both the text and a TSVector, in case we need to 
consult the original text later, or want to use the built-in TS_HEADLINE, or
eventually, TS_SEMANTIC_HEADLINE and TS_FAST HEADLINE
*/
CREATE OR REPLACE FUNCTION LARGE_TEXT_TO_TSVECTORS
(config REGCONFIG, source TEXT) 
RETURNS TABLE (content_fragment TEXT) AS 
$$
DECLARE vector TSVECTOR = TO_TSVECTOR(config, source);
BEGIN
  IF (vector = ''::TSVECTOR) THEN
    -- A trivial TSV, return the original text
    RETURN QUERY (SELECT source);
    -- If we have a text smaller than 256 words, it doesn't need to be divided 
    -- anymore. In some cases the other branch was recurring infinitely.
  ELSIF ( ARRAY_LENGTH(REGEXP_SPLIT_TO_ARRAY(source, E'\\s+'), 1) < 2^8 ) THEN
    RETURN QUERY (SELECT source);
  ELSIF (SELECT
         -- Prevent any positions larger than 16382; since the cap is 16383
         -- and more than 1 lexeme can occupy position 16383.
          ( SELECT MAX(terms.pos)
              FROM (SELECT UNNEST((words).positions) AS pos
                      FROM (SELECT words 
                              FROM (SELECT UNNEST(vector) AS words) 
                                    AS lexes) 
                            AS wordplaces) 
                    AS terms)
            > (2 ^14 - 2)
            OR
            -- Prevent any specific lexeme occurring more than 255 times
            ( SELECT MAX(ARRAY_LENGTH((lex_position_arrays.words).positions, 1))
                FROM (SELECT words 
                        FROM (SELECT UNNEST(vector) AS words)
                              AS lexes) 
                      AS lex_position_arrays)
            > (2 ^ 8 - 2))
  THEN
    -- Recur
    RETURN QUERY (SELECT LARGE_TEXT_TO_TSVECTORS(config, fragment) 
                  FROM LTSV_LARGE_TEXT_TO_INDEXABLE_FRAGMENTS(source));
  ELSE
    -- If it's already or eventually valid, return unchanged
    RETURN QUERY (SELECT source);
  END IF;

-- ** EXCEPTION HANDLING ::
-- If we try to create a TSVector and it's too large, make it smaller and try 
-- again
EXCEPTION WHEN program_limit_exceeded THEN
  -- Recur
  RETURN QUERY (SELECT LARGE_TEXT_TO_TSVECTORS(config, fragment) 
                FROM LTSV_LARGE_TEXT_TO_INDEXABLE_FRAGMENTS(source));
END;
$$
STABLE
LANGUAGE plpgsql;
