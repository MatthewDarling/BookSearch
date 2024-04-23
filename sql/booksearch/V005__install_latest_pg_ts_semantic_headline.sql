-- TYPE/DOMAIN for restricting, differentiating and marshalling pre- v.
--  post-processed TS Query and Vectors

-- TYPE: TSPQuery
-- OVERLOADS: TSQuery
-- Enforces a query that is BOTH UNACCENTed and contains no infix characters; 
-- \W+ will capture 
CREATE DOMAIN TSPQuery AS TSQuery 
NOT NULL CHECK (value::TEXT !~ '[\w+][\W+][\w]');

-- Note:
-- 1) in TO_TSPVECTOR er inject a dummy, marker node into the max lexeme 
--    position, 16383
-- 2) multiple lexemes can occupy position 16,383 without logically 
--    interfering with each other

-- A TSPVector 'proves' it has been pre-processed by inserting the
-- ProcessedUnaccentedTSPIndexableText at the maximum position.
CREATE DOMAIN TSPVector AS TSVector 
NOT NULL CHECK (value @@ 'ProcessedUnaccentedTSPIndexableText'::TSQUERY);/*
Function: PHRASETO_TSPQUERY

1:1 replacement for the built-in PHRASETO_TSQuery function:

Accepts 
- config       REGCONFIG - PGSQL Text Search Language Configuration
- query_string TEXT - a common language string as a phrase, or ordered 
                      combination of multiple words.

Returns a TSPQuery that represents the query phrase after its treament with 
TSP_INDEXABLE_TEXT. This is done to attain positional alignment between raw text
and the rendered TSVector. As we are searching on a treated vector, we need to treat
the phrase used to render a TSPQuery in the same way
*/

CREATE OR REPLACE FUNCTION PHRASETO_TSPQUERY(config REGCONFIG, query_string TEXT)
RETURNS TSPQUERY AS
$$
BEGIN
    RETURN PHRASETO_TSQUERY(config, TSP_INDEXABLE_TEXT(UNACCENT(query_string)))::TSPQuery;
END;
$$
STABLE
LANGUAGE plpgsql;

-- OVERLOAD Arity-2 form, to infer the default_text_search_config for parsing
CREATE OR REPLACE FUNCTION PHRASETO_TSPQUERY(query_string TEXT)
RETURNS TSPQUERY AS
$$
BEGIN    
    RETURN PHRASETO_TSPQUERY(current_setting('default_text_search_config')::REGCONFIG, 
                             query_string);
END;
$$
STABLE
LANGUAGE plpgsql;
/*
Function: REPLACE_MULTIPLE_STRINGS

A simple method of replacing multiple strings in a text at the same time.

Accepts:
- source_text   TEXT   - The original text to be altered be replacements 
- find_array    TEXT[] - Array of strings to be replaced
- replace_array TEXT[] - Array of strings to replace find_array entries 
                         with
*/
CREATE OR REPLACE FUNCTION REPLACE_MULTIPLE_STRINGS 
(source_text text, find_array text[], replace_array text[])
RETURNS text AS
$$
DECLARE
    i integer;
BEGIN
	IF (find_array IS NULL) THEN RETURN source_text; END IF;
    FOR i IN 1..array_length(find_array, 1)
    LOOP
        source_text := replace(source_text, find_array[i], replace_array[i]);
    END LOOP;
    RETURN source_text;
END;
$$
LANGUAGE plpgsql;
/* Function: SLICE_ARRAY
For a whole_array ARRAY[], performs whole_array[start_pos:end_pos] without the syntactic sugar.
Very helpful is quickly moving through honeysql notation issue in clojure :)
*/

CREATE OR REPLACE FUNCTION SLICE_ARRAY 
(whole_array TEXT[], start_pos BIGINT, end_pos BIGINT)
RETURNS text[] AS
$$
BEGIN
    RETURN (whole_array)[start_pos:end_pos];
END;
$$
STABLE
LANGUAGE plpgsql;
/*
Function: TO_TSP_TEXT_ARRAY

Simple wrapper function to ensure we split text arrays the same time, every time.
This is very much a convenience wrapper.
*/

CREATE OR REPLACE FUNCTION TO_TSP_TEXT_ARRAY(string TEXT)
RETURNS TEXT[] AS
$$
BEGIN
	RETURN REGEXP_SPLIT_TO_ARRAY(TSP_INDEXABLE_TEXT(string), '[\s]+');
END;
$$
STABLE
LANGUAGE plpgsql;/*
Function: TO_TSPQUERY

Accepts:
- config       REGCONFIG - PGSQL Text Search Language Configuration
- query_string TEXT      - String of search terms connected with TSQuery
                           operators.

Akin to the builtin function `to_tsquery`, this function converts text to a 
tsquery, normalizing words according to the specified or default configuration. 
The words must be combined by valid tsquery operators.

TO_TSPQUERY('english', 'The & Fat & Rats') → 'fat' & 'rat'

For the purposes of a TSQuery, this function is the treatment for TSQueries for
index-friendly positioning and is paralleled with TSP_INDEXABLE_TEXT in TSVectors
*/

CREATE OR REPLACE FUNCTION TO_TSPQUERY(config REGCONFIG, query_string TEXT)
RETURNS TSPQuery AS
$$
BEGIN
    -- We perform the chararacter substitution twice to catch any terms with 
	-- multiple character-delimiter substrings
	query_string := ' ' || UNACCENT(query_string) || ' ';
	query_string := regexp_replace(query_string, '(\w)([^[:alnum:]&^<>|\s]+)(\w)', E'\\1\\2\<1>\\3', 'g');
	query_string := regexp_replace(query_string, '(\w)([^[:alnum:]&^<>|\s]+)(\w)', E'\\1\\2\<1>\\3', 'g');	    
    
	RETURN TO_TSQUERY(config, query_string)::TSPQuery;
END;
$$
STABLE
LANGUAGE plpgsql;

-- OVERLOAD Arity-2 form, to infer the default_text_search_config for parsing
CREATE OR REPLACE FUNCTION TO_TSPQUERY(query_string TEXT)
RETURNS TSPQuery AS
$$
BEGIN    
    RETURN TO_TSPQUERY(current_setting('default_text_search_config')::REGCONFIG, 
	                     query_string);
END;
$$
STABLE
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION TO_TSPQUERY(config REGCONFIG, query TSQUERY)
RETURNS TSPQuery AS
$$
DECLARE string TEXT = regexp_replace(UNACCENT(query::TEXT), 
	                   '''(\w+)(\W)(\w+)'' <-> ''(\w+)'' <-> ''(\w+)''', 
                       E'''\\4'' <-> ''\\5''',
                       'g');
BEGIN    
	RETURN TO_TSPQUERY(config, 
                     regexp_replace(string, 
	                   '''(\W)(\w+)''', 
                       E'''\\2''',
                       'g'));
END;
$$
STABLE
LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION TO_TSPQUERY(query TSQUERY)
RETURNS TSPQuery AS
$$

BEGIN    
	RETURN TO_TSPQUERY(current_setting('default_text_search_config')::REGCONFIG,
                     query::TSQUERY);
END;
$$
STABLE
LANGUAGE plpgsql;/*
Function: TO_TSPQUERY

Accepts:
- config       REGCONFIG - PGSQL Text Search Language Configuration
- query_string TEXT      - String of search terms connected with TSQuery
                           operators.

Akin to the builtin function `to_tsquery`, this function converts text to a 
tsquery, normalizing words according to the specified or default configuration. 
The words must be combined by valid tsquery operators.

TO_TSPQUERY('english', 'The & Fat & Rats') → 'fat' & 'rat'

For the purposes of a TSQuery, this function is the treatment for TSQueries for
index-friendly positioning and is paralleled with TSP_INDEXABLE_TEXT in TSVectors
*/

CREATE OR REPLACE FUNCTION TO_TSPVECTOR(config REGCONFIG, string TEXT)
RETURNS TSPVECTOR AS
$$
BEGIN
	RETURN (TO_TSVECTOR(config, TSP_INDEXABLE_TEXT(unaccent(string))) || ('''ProcessedUnaccentedTSPIndexableText'':16384')::TSVECTOR)::TSPVECTOR;
END;
$$
STABLE
LANGUAGE plpgsql;

-- OVERLOAD Arity-2 form, to infer the default_text_search_config for parsing
CREATE OR REPLACE FUNCTION TO_TSPVECTOR(string TEXT)
RETURNS TSPVECTOR AS
$$
BEGIN    
    RETURN TO_TSPVECTOR(current_setting('default_text_search_config')::REGCONFIG, 
	                       string);
END;
$$
STABLE
LANGUAGE plpgsql;

-- Internal function that will transform a TSVector into a TSPVector WITHOUT 
-- checking internal lexemes, thus asserting that the TSVector is a well-formed
-- TSPVector. This function should ONLY be used on a candidate TSVector that is 
-- known to also be a well-formed TSPVector.
-- Internally this is used to coerce the disassembled portions of a known TSPVector
-- into pieces for semantic checking against a query phrase within tsp_query_matches.
CREATE OR REPLACE FUNCTION ASSERT_TSPVECTOR(vec TSVector)
RETURNS TSPVECTOR AS
$$
BEGIN    
    RETURN (vec || ('''ProcessedUnaccentedTSPIndexableText'':16384')::TSVECTOR)::TSPVECTOR;
END;
$$
STABLE
LANGUAGE plpgsql;/*
Function: TS_FAST_HEADLINE
Accepts: 
- config       REGCONFIG - PGSQL Text Search Language Configuration
- haystack_arr TEXT[]    - Ordered array of words, as they appear in the source
                           document, delimited by spaces. Assumes text is 
						   preprocessed by tsp_indexable text function
- content_tsv  TSVECTOR  - TSVector representation of the source document. 
                           Assumes text is preprocessed by tsp_indexable text 
						   function to maintain the correct positionality of 
						   lexemes.
- search_query TSQUERY   - TSQuery representation of a user-inputted search.
- options      TEXT      - Configuration options in the same form as the 
                           TS_HEADLINE options, with some semantic difference 
						   in interpretting parameters.

Internally, this function calls TSP_QUERY_MATCHES, aggregates ranges based on 
frequency in range (akin to cover density), and returns results from the start 
of the document forward. This diverges from the implementation of cover density 
in ts_headline, and in making these sacrifices in order to better performance.

As the internals of this function guarantee that each fragment will semantically 
abide the TSQuery and its phrase semantics, in whole or in part, the goal is to
return evidence of the search match, and thus we make concessions on headline 
robustness, for speed of recall.

Returns a string of 1 or more passages of text, with content matching one or more 
phrase patterns in the TSQuery highlighted, meaning wrapped by the StartSel and 
StopSel options.

Options:
* `MaxWords`, `MinWords` (integers): these numbers determine the number of words, 
   beyond the length of a phrase in TSQuery, to return in each headline. For instance, 
   a value of MinWords=4 will put min. 2 words on either side of a phrase headline.
* `ShortWord` (integer): NOT IMPLEMENTED: The system does not use precisely the same 
   `rank_cd` ordering to return the most cover-dense headline segments first, but rather 
   find the FIRST find n matching passages within the document. See `tsp_exact_matches` 
   function for more.
* `HighlightAll` (boolean): TODO: Implement this option.
* `MaxFragments` (integer): maximum number of text fragments to display. The default 
  value of zero selects a non-fragment-based headline generation method. A value 
  greater than zero selects fragment-based headline generation (see below).
* `StartSel`, `StopSel` (strings): the strings with which to delimit query words 
   appearing in the document, to distinguish them from other excerpted words. The 
   default values are “<b>” and “</b>”, which can be suitable for HTML output.
* `FragmentDelimiter` (string): When more than one fragment is displayed, the fragments 
   will be separated by this string. The default is “ ... ”.
*/

-- Arity-5 Form of fast TS_FAST_HEADLINE with pre-computed arr & tsv
CREATE OR REPLACE FUNCTION TS_FAST_HEADLINE 
(config REGCONFIG, haystack_arr TEXT[], content_tsv TSPVECTOR, search_query TSPQUERY, options TEXT DEFAULT '')
RETURNS TEXT AS
$$
DECLARE
    -- Parse Options string to JSON map --
    opts JSON = (SELECT JSON_OBJECT_AGG(grp[1], COALESCE(grp[2], grp[3])) AS opt 
                 FROM REGEXP_MATCHES(options, 
                                    '(\w+)=(?:"([^"]+)"|((?:(?![\s,]+\w+=).)+))', 
                                    'g') as matches(grp));
BEGIN
    RETURN (
        SELECT TSP_PRESENT_TEXT(STRING_AGG(headline,
                                           COALESCE(opts->>'FragmentDelimiter', '...')),
                                COALESCE(opts->>'StopSel', '</b>'))
        FROM TS_FAST_HEADLINE_COVER_DENSITY(config, haystack_arr, content_tsv, search_query, options));
END;
$$
STABLE
LANGUAGE plpgsql;


-- OVERLOAD Arity-5 form, to infer the default_text_search_config for parsing
-- Arity-4 Form of fast TS_FAST_HEADLINE with pre-computed arr & tsv
CREATE OR REPLACE FUNCTION TS_FAST_HEADLINE 
(haystack_arr TEXT[], content_tsv TSPVECTOR, search_query TSPQUERY, options TEXT DEFAULT '')
RETURNS TEXT AS
$$
BEGIN
    RETURN TS_FAST_HEADLINE(current_setting('default_text_search_config')::REGCONFIG,
                            haystack_arr,
                            content_tsv,
                            search_query, 
                            options);
END;
$$
STABLE
LANGUAGE plpgsql;
  
/*
Function: TS_FAST_HEADLINE_COVER_DENSITY
This function does the vast majority of the heavy-lifting for ts_fast_headline,
however, for large text files that required more than 1 TSVector to fully index,
we found it necessary to have the TS_FAST_HEADLINE_COVER_DENSITY be separated
in order to allow results from multiple sections of the document be aggregated
according to the number of results contained in a fragment.

Accepts: 
- config       REGCONFIG - PGSQL Text Search Language Configuration
- haystack_arr TEXT[]    - Ordered array of words, as they appear in the source
                           document, delimited by spaces. Assumes text is 
						   preprocessed by tsp_indexable text function
- content_tsv  TSVECTOR  - TSVector representation of the source document. 
                           Assumes text is preprocessed by tsp_indexable text 
						   function to maintain the correct positionality of 
						   lexemes.
- search_query TSQUERY   - TSQuery representation of a user-inputted search.
- options      TEXT      - Configuration options in the same form as the 
                           TS_HEADLINE options, with some semantic difference 
						   in interpretting parameters.

Internally, this function calls TSP_QUERY_MATCHES, aggregates ranges based on 
frequency in range (akin to cover density), and returns results from the start 
of the document forward. This diverges from the implementation of cover density 
in ts_headline, and in making these sacrifices in order to better performance.

As the internals of this function guarantee that each fragment will semantically 
abide the TSQuery and its phrase semantics, in whole or in part, the goal is to
return evidence of the search match, and thus we make concessions on headline 
robustness, for speed of recall.

Returns a string of 1 or more passages of text, with content matching one or more 
phrase patterns in the TSQuery highlighted, meaning wrapped by the StartSel and 
StopSel options.

Options:
* `MaxWords`, `MinWords` (integers): these numbers determine the number of words, 
   beyond the length of a phrase in TSQuery, to return in each headline. For instance, 
   a value of MinWords=4 will put min. 2 words on either side of a phrase headline.
* `ShortWord` (integer): NOT IMPLEMENTED: The system does not use precisely the same 
   `rank_cd` ordering to return the most cover-dense headline segments first, but rather 
   find the FIRST find n matching passages within the document. See `tsp_exact_matches` 
   function for more.
* `HighlightAll` (boolean): TODO: Implement this option.
* `MaxFragments` (integer): maximum number of text fragments to display. The default 
  value of zero selects a non-fragment-based headline generation method. A value 
  greater than zero selects fragment-based headline generation (see below).
* `StartSel`, `StopSel` (strings): the strings with which to delimit query words 
   appearing in the document, to distinguish them from other excerpted words. The 
   default values are “<b>” and “</b>”, which can be suitable for HTML output.
* `FragmentDelimiter` (string): When more than one fragment is displayed, the fragments 
   will be separated by this string. The default is “ ... ”.
*/

-- Arity-5 Form of fast TS_FAST_HEADLINE with pre-computed arr & tsv

CREATE OR REPLACE FUNCTION TS_FAST_HEADLINE_COVER_DENSITY 
(config REGCONFIG, haystack_arr TEXT[], content_tsv TSPVECTOR, search_query TSPQUERY, options TEXT DEFAULT '')
RETURNS TABLE(headline TEXT, density INTEGER) AS
$$
DECLARE
    -- Parse Options string to JSON map --
    opts          JSON    = (SELECT JSON_OBJECT_AGG(grp[1], COALESCE(grp[2], grp[3])) AS opt 
                             FROM REGEXP_MATCHES(options, 
                                                 '(\w+)=(?:"([^"]+)"|((?:(?![\s,]+\w+=).)+))', 
                                                 'g') as matches(grp));
    -- Options Map and Default Values --
    tag_range     TEXT    = COALESCE(opts->>'StartSel', '<b>') || 
                            E'\\1' || 
                            COALESCE(opts->>'StopSel', '</b>');
    min_words     INTEGER = COALESCE((opts->>'MinWords')::SMALLINT / 2, 10);
    max_words     INTEGER = COALESCE((opts->>'MaxWords')::SMALLINT, 30);
    max_offset    INTEGER = max_words / 2 + 1;
    max_fragments INTEGER = COALESCE((opts->>'MaxFragments')::INTEGER, 1);
BEGIN
    RETURN QUERY (
        SELECT REGEXP_REPLACE(-- Aggregate the source text over a Range
                              ' ' || 
                              ARRAY_TO_STRING(haystack_arr[MIN(start_pos) - GREATEST((max_offset - (MAX(end_pos) - MIN(start_pos) / 2 + 1)), min_words): 
                                                           MAX(end_pos)   + GREATEST((max_offset - (MAX(end_pos) - MIN(start_pos) / 2 + 1)), min_words)], 
                                              ' ') || ' ', 
                              -- Capture Exact Matches over Range
                              E' (' || STRING_AGG(REGEXP_REPLACE(words, '([\.\+\*\?\^\$\(\)\[\]\{\}\|\\])', '\\\1', 'g'), '|') || ') ', 
                              -- Replace with Tags wrapping Content
                              ' ' || tag_range || ' ', 
                              'g') AS highlighted_text,
               COUNT(*)::INTEGER AS match_count
        FROM TSP_QUERY_MATCHES (config, 
                                haystack_arr, 
                                content_tsv, 
                                search_query, 
                                max_fragments + 6, 
                                COALESCE(opts->>'DisableSematics', 'FALSE')::BOOLEAN)
        GROUP BY (start_pos / (max_words + 1)) * (max_words + 1)
        ORDER BY COUNT(*) DESC, (start_pos / (max_words + 1)) * (max_words + 1)
        LIMIT max_fragments);
END;
$$
STABLE
LANGUAGE plpgsql;
/*
Function: TS_SEMANTIC_HEADLINE
This function is intended as a 1:1 replacement for ts_headline and maintains
the same signature as ts_headline

Accepts: 
- config       REGCONFIG - PGSQL Text Search Language Configuration
- content      TEXT      - The source text to be fragmented and highlighted
- user_search  TSQuery   - TSQuery search as a collection of phrases, separated
                           by logical operators. Do NOT pass a TSPQuery to this 
                           function.
- options      TEXT      - Configuration options in the same form as the 
                           TS_HEADLINE options, with some semantic difference 
						   in interpretting parameters.


Returns a string of 1 or more passages of text, with content matching one or more 
phrase patterns in the TSQuery highlighted, meaning wrapped by the StartSel and 
StopSel options.

Options:
* `MaxWords`, `MinWords` (integers): these numbers determine the number of words, 
   beyond the length of a phrase in TSQuery, to return in each headline. For instance, 
   a value of MinWords=4 will put min. 2 words on either side of a phrase headline.
* `ShortWord` (integer): NOT IMPLEMENTED: The system does not use precisely the same 
   `rank_cd` ordering to return the most cover-dense headline segments first, but rather 
   find the FIRST find n matching passages within the document. See `tsp_exact_matches` 
   function for more.
* `HighlightAll` (boolean): TODO: Implement this option.
* `MaxFragments` (integer): maximum number of text fragments to display. The default 
  value of zero selects a non-fragment-based headline generation method. A value 
  greater than zero selects fragment-based headline generation (see below).
* `StartSel`, `StopSel` (strings): the strings with which to delimit query words 
   appearing in the document, to distinguish them from other excerpted words. The 
   default values are “<b>” and “</b>”, which can be suitable for HTML output.
* `FragmentDelimiter` (string): When more than one fragment is displayed, the fragments 
   will be separated by this string. The default is “ ... ”.
*/

/*
Note: This form is the 1:1 replacement for ts_headline:

Likewise, this function uses TS_HEADLINE under the hood to handle content that 
does NOT use precomputed TEXT[] and TSVector columns. Rather, this implementation
calls ts_headline to return fragments, and then applies the arity-5 form of
TS_SEMANTIC_HEADLINE to the results to achieve semantically accurate phrase
highlighting.
*/


-- Arity-4 Form of simplified TS_SEMANTIC_HEADLINE 
CREATE OR REPLACE FUNCTION TS_SEMANTIC_HEADLINE
(config REGCONFIG, content TEXT, user_search TSQUERY, options TEXT DEFAULT '')
RETURNS TEXT AS
$$
DECLARE headline TEXT = ts_headline(config, 
                                    content, 
                                    user_search, 
                                    options || ',StartSel="",StopSel="",FragmentDelimiter=XDUMMYFRAGMENTX,');
BEGIN
    user_search := TO_TSPQUERY(config, user_search);
    headline := regexp_replace(' ' || headline || ' ', 'XDUMMYFRAGMENTX', ' ... ', 'g');
    IF NOT(options = '') THEN options := options || ','; END IF;
    RETURN COALESCE(TS_FAST_HEADLINE(config,
                                     TO_TSP_TEXT_ARRAY(headline), 
                                     TO_TSPVECTOR(config, headline), 
                                     user_search,
                                     options || 'MaxFragments=30,MinWords=64,MaxWords=64' ),
                    TRIM(headline));
END;
$$
STABLE
LANGUAGE plpgsql;

-- OVERLOAD Arity-4 form #2, to infer the default_text_search_config for parsing
-- Arity-3 Form of simplified TS_SEMANTIC_HEADLINE 
CREATE OR REPLACE FUNCTION TS_SEMANTIC_HEADLINE
(content TEXT, user_search TSQUERY, options TEXT DEFAULT '')
RETURNS TEXT AS
$$
BEGIN
   RETURN TS_SEMANTIC_HEADLINE(current_setting('default_text_search_config')::REGCONFIG, 
                               content, 
                               user_search, 
                               options);
END;
$$
STABLE
LANGUAGE plpgsql;
/*
Function: TSP_INDEXABLE_TEXT

Accepts: 
- result_text TEXT - the source text to be prepared, by having indexing tokens 
                     removed

Returns a string with the words delimited by special characters broken apart 
by inserting indexing tokens of a Bell Character (u0001) + SPACE.
The purpose of this function is to break apart character-delimiter terms into 
individual tokens for rendering a TSVector. Performing this preparation results 
in a TSVector (for english-stem, so far) that maintains lexeme positions that 
will match the source text word postions, provided that both the TSVector and 
the source text are prepared with this function.

The effect of the `TSP_INDEXABLE_TEXT` function can be reversed by 
applying the ``TSP_PRESENT_TEXT` function. One should be careful 
as applying these two functions is intended for fast recall of search results 
and applying these 2 functions consecutively is NOT an idempotent transformation. 
Specifically, applying the two functions will remove all sequences of exclusively 
special characters and eliminate consecutive whitespace.

How to harvest special characters:
Use the following query to determine the blank characters used by each of the 
installed PGSQL Text Search Language configurations. Note, we range from 2 to 
the unicode limit of 55295, and interpret each of the characters in each of 
the installed languages. We omit character 0001 as that is a bell character 
used in computed string and thus omitted. The following query SHOULD return 217 
rows (representing each of the 64 space characters, plus the character that, 
when UNACCENTed, create space characters in pgsql ts lexizing), each with a 
count of 29 (representing the number of default languages in the system).
Use:
-------------------------------------------------------------------------------
SELECT 
alias, seqno, chr(seqno), count(*), '\u' || lpad(to_hex(seqno)::TEXT, 4, '0') as unicode_char
FROM (SELECT cfgname::REGCONFIG AS lang  FROM pg_ts_config) AS tlang,
     -- The important part is to unaccent the characters BEFORE ts_debug!!!
	 (SELECT (SELECT STRING_AGG(alias, '') as alias FROM ts_debug(UNACCENT(chr(seqno)))) AS alias, 
	         chr(seqno) AS char, 
	         seqno 
      FROM generate_series(2, 55295) as seqno) as a1
WHERE substring(alias, 'blank') IS NOT NULL
-- omit the actual space character
AND seqno <> 32
GROUP BY seqno, alias;
-------------------------------------------------------------------------------
At the time of writing, in pgsql14, all 217 rows should return a count of 29.
This will be a prime assertion in testing. 

In order to actually aggregate the collection of unicode characters used below, run:
-------------------------------------------------------------------------------
WITH blanks AS 
(SELECT alias, seqno, chr(seqno), count(*), '\u' || lpad(to_hex(seqno)::TEXT, 4, '0') as unicode_char
FROM (SELECT cfgname::REGCONFIG AS lang  FROM pg_ts_config) AS tlang,
	 (SELECT (SELECT STRING_AGG(alias, '') as alias FROM ts_debug(UNACCENT(chr(seqno)))) AS alias, 
	         chr(seqno) AS char, 
	         seqno 
      FROM generate_series(2, 55295) as seqno) as a1
WHERE substring(alias, 'blank') IS NOT NULL
-- omit the actual space character
AND seqno <> 32
GROUP BY seqno, alias)
SELECT STRING_AGG(unicode_char, '|') from blanks;
-------------------------------------------------------------------------------
*/

CREATE OR REPLACE FUNCTION TSP_INDEXABLE_TEXT(result_string text)
RETURNS text AS
$$
DECLARE
    -- All Unicode Characters that, when processed with UNACCENT will be treated
	-- in TS_Vector/Query lexiing, as blanks OR characters and blanks 
    space_making_chars TEXT = '\u24a5|\u24a4|\u000c|\u00bb|\u0002|\u02dc|' || 
	'\u3002|\u24a6|\u003d|\u02bc|\u2033|\u0008|\u301a|\u00a9|\u2477|\u203c|' || 
	'\u0021|\u0004|\u2212|\u2489|\u301d|\u2a75|\u249a|\u0149|\u2215|\u2480|' || 
	'\u300b|\u2986|\u2046|\u003a|\u24a8|\u3009|\u2499|\u2493|\u007e|\u0025|' || 
	'\u02c8|\u02c2|\u247b|\u2225|\u2026|\u001b|\u249c|\u2496|\u2011|\u247e|' || 
	'\u20a4|\u0040|\u2047|\u2a76|\u226b|\u007d|\u24b0|\u2490|\u2486|\u249f|' || 
	'\u00a1|\u001a|\u0012|\u2216|\u2018|\u201e|\u2483|\u005e|\u33d8|\u002d|' || 
	'\u02d7|\u301e|\u0014|\u24ad|\u002a|\u002e|\u0007|\u0028|\u005c|\u2015|' || 
	'\u0029|\u00ab|\u2048|\u02bb|\u24a7|\u3014|\u2482|\u0023|\u24b5|\u24ac|' || 
	'\u201b|\u301b|\u2013|\u0009|\u002f|\u2485|\u2039|\u003c|\u24a2|\u00f7|' || 
	'\u00b1|\u24aa|\u3015|\u2044|\u2487|\u248f|\u005b|\u20a3|\u0019|\u249e|' || 
	'\u0011|\u001e|\u2045|\u2478|\u24b2|\u2484|\u02cb|\u000e|\u24b1|\u2494|' || 
	'\u2476|\u02c6|\u24a9|\u02d0|\u2492|\u248d|\u001c|\u2016|\u007c|\u24af|' || 
	'\u2012|\u0026|\u2014|\u2985|\u249d|\u003f|\u00bf|\u204e|\u02b9|\u0005|' || 
	'\u24a0|\u2498|\u2481|\u002b|\u2479|\u2488|\u33c7|\u24b3|\u203a|\u007f|' || 
	'\u2497|\u3019|\u24b4|\u0017|\u247d|\u001d|\u0018|\u0015|\u003e|\u007b|' || 
	'\u0016|\u000f|\u000b|\u2024|\u247f|\u2049|\u2010|\u005f|\u3018|\u2491|' || 
	'\u2223|\u201c|\u201a|\u226a|\u24a1|\u2032|\u2019|\u24a3|\u248b|\u0060|' || 
	'\u3008|\u02ba|\u215f|\u003b|\u0003|\u247c|\u0022|\u0010|\u24ae|\u2475|' || 
	'\u248a|\u201d|\u0027|\u0013|\u2a74|\u000d|\u005d|\u0024|\u3001|\u2474|' || 
	'\u02c4|\u33c2|\u000a|\u00d7|\u249b|\u00ad|\u24ab|\u2117|\u00ae|\u300a|' || 
	'\u02bd|\u02d6|\u002c|\u201f|\u248e|\u248c|\u02c3|\u001f|\u0006|\u2495|' || 
	'\u247a';
BEGIN
result_string := regexp_replace(result_string, '\n|\r', ' ', 'g');
result_string := regexp_replace(result_string, '^\W+', '', 'g');
-- Any word-breaking character is treated, in its raw (NOT UNACCENTED) form,
-- by inserting a bell character + space after the character, forcing separate
-- tokenization of deliniated terms
result_string := regexp_replace(result_string, 
                                '(['|| space_making_chars ||']+)\s', 
                                E'\\1\u0001 ', 
                                'g');

result_string := regexp_replace(result_string, 
                                '(['|| space_making_chars ||'|\u0001]+)', 
                                E'\\1\u0001 ', 
                                'g');

result_string := regexp_replace(result_string,
                                '(\s)(['|| space_making_chars ||']+) ', 
                                E'\\1\\2', 
                                'g');


-- removes all non-word token sequences
result_string := regexp_replace(result_string, 
                                '\s(['|| space_making_chars ||']+\u0001+)', 
                                ' ', 
                                'g');
-- removes redundant spaces
result_string := regexp_replace(result_string, 
                                '[\s]+', 
                                ' ', 
                                'g');
-- Trim the result and return the string
RETURN TRIM(result_string);
END;
$$
STABLE
LANGUAGE plpgsql;

/*
Function: TSP_PRESENT_TEXT
Accepts: 
- input_text    TEXT - the source text to be prepared, by having indexing tokens 
                       removed
- end_delimiter TEXT - the StopSel parameter provided as part of TS_HEADLINE 
                       options, and the closing tag of a headline. Defaults 
                       to '</b>'

Returns a string with the indexing tokens of Bell Character (u0001) + SPACE removed, 
including those sequences which are divided by a specified end_delimiter. Reverses 
the effect of `TSP_INDEXABLE_TEXT` function.
*/

CREATE OR REPLACE FUNCTION TSP_PRESENT_TEXT (input_text TEXT, end_delimiter TEXT DEFAULT '</b>')
RETURNS TEXT AS
$$
BEGIN
	
    -- Removes Bell Char + SPACE sequences
    input_text := regexp_replace(input_text, E'\u0001\u0001 ', ' ', 'g');
    input_text := regexp_replace(input_text, E'\u0001 ', '', 'g');

    -- Removes Bell Char + end_delimiter + SPACE sequences
    input_text := regexp_replace(input_text,  E'\u0001\u0001(' || end_delimiter || ') ', E'\\2\\1 ', 'g');
    input_text := regexp_replace(input_text,  E'\u0001(' || end_delimiter || ') ', E'\\2\\1', 'g');

    -- Having cleaned the added spaces, now we removes all Bell Chars
    input_text := regexp_replace(input_text, E'\u0001', '', 'g');
    
    -- Trim string and return
	RETURN TRIM(input_text);
END;
$$
STABLE
LANGUAGE plpgsql;
/*
Function: TSP_QUERY_MATCHES
Accepts: 
- config       REGCONFIG - PGSQL Text Search Language Configuration
- haystack_arr TEXT[]    - Ordered array of words, as they appear in the source
                           document, delimited by spaces. Assumes text is preprocessed
                           by tsp_indexable text function
- content_tsv  TSVECTOR  - TSVector representation of the source document. Assumes text 
                           is preprocessed by tsp_indexable text function to maintain
                           the correct positionality of lexemes.
- search_query TSPQUERY  - TSPQuery representation of a user-inputted search.
- match_limit  INTEGER   - Number of matches to return from the start of the document.
                           Defaults to 5.

Returns a table of exact matches returned from the fuzzy TSPQuery search, Each row contains:
- words     TEXT     - the exact string found in the text
- ts_query  TSQUERY  - the TSQuery phrase pattern that matches `words` text. A given TSQuery 
                       can contain multiple phrase patterns
- start_pos SMALLINT - the first word position of the found term within the document.
- end_pos   SMALLINT - the last word position of the found term within the document.

Reduces the TSPQuery into a collection of TSVector phrase patterns; Reduces the source
TSVector to a filtered TSV containing only the lexemes in the TSPQuery. JOINs the 
exploded TSPQuery (as a table of lexemes and positions) to the TSvector (also as a table)
of lexemes and positions. JOINing, reducing and GROUPing, herein we implement the 
matching pattern inherent in the TSVECTOR @@ TSQUERY searching operation.

Performing this lookup on pre-computed, pre-treated (ts_indexable_text + UNACCENT) 
ts_vectors is surprisingly fast. Using a pre-computer TEXT[] array as a RECALL index
drastically reduces the computational and memory overhead of this function.

Support TSQuery logical operators (&, |, !) as well as phrase distance/proximity 
operators (<->, <n>).

Currently does NOT support TSQuery Wildcards (*), but that is the only known 
exception at present.
*/


-- Internal Helper Function, broken out for debugging
-- Returns a filtered TSV containing ONLY the lexemes within
CREATE OR REPLACE FUNCTION tsp_filter_tsvector_with_tsquery
(config REGCONFIG, tspv TSPVECTOR, search_query TSQUERY)
RETURNS TSPVECTOR AS
$$    
BEGIN
   RETURN 
    (SELECT ASSERT_TSPVECTOR(ts_filter(setweight(tspv, 'A', ARRAY_AGG(lexes)), '{a}'))
     FROM (SELECT UNNEST(tsvector_to_array(vec.phrase_vector)) AS lexes
           FROM TSQUERY_TO_TSVECTOR(config, search_query) AS vec) AS query2vec);
END;
$$
STABLE
LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION TSP_QUERY_MATCHES
(config REGCONFIG, haystack_arr TEXT[], content_tsv TSPVECTOR, search_query TSPQUERY, 
 match_limit INTEGER DEFAULT 5, 
 disable_semantic_check BOOLEAN DEFAULT FALSE)

RETURNS TABLE(words TEXT, 
              ts_query TSPQUERY, 
              start_pos SMALLINT, 
              end_pos SMALLINT) AS
$$    
BEGIN
    -- Reduce the input TSV to only the lexemes matching the query
    content_tsv := tsp_filter_tsvector_with_tsquery(config, content_tsv, search_query);

    RETURN QUERY
    (   SELECT array_to_string(haystack_arr[first:last], ' '),           
               query::TSPQUERY,
               first, 
               last
        FROM (SELECT MIN(pos) AS first, 
                     MAX(pos) AS last, 
                     range_start AS range_start, 
                     MAX(lex) as lex,
                     phrase_query as query
              FROM (SELECT phrase_vector,
                           query_vec.phrase_query,
                           (SELECT COUNT(*) FROM TSVECTOR_TO_TABLE(phrase_vector)) AS query_length, 
                           haystack.lex,
                           haystack.pos AS pos, 
                           haystack.pos - query_vec.pos 
                           + (SELECT MIN(pos) 
                              FROM TSVECTOR_TO_TABLE(query_vec.phrase_vector)) as range_start
                    FROM TSQUERY_TO_TABLE(config, search_query) AS query_vec 
                    INNER JOIN TSVECTOR_TO_TABLE(content_tsv) AS haystack 
                    ON haystack.lex = query_vec.lexeme) AS joined_terms
              GROUP BY range_start, query, query_length 
              HAVING COUNT(*) = query_length) AS phrase_agg
        WHERE (last - first) = (SELECT MAX(pos) - MIN(pos) 
                                FROM TSQUERY_TO_TABLE(config, query))
        AND (disable_semantic_check 
             OR TO_TSPVECTOR(config, array_to_string(haystack_arr[first:last], ' ')) @@ query::TSQUERY)
        LIMIT match_limit);
END;
$$
STABLE
LANGUAGE plpgsql;


-- OVERLOAD Arity-5 form, to infer the default_text_search_config for parsing
CREATE OR REPLACE FUNCTION TSP_QUERY_MATCHES
(haystack_arr TEXT[], content_tsv TSPVECTOR, search_query TSPQUERY, match_limit INTEGER DEFAULT 5)
RETURNS TABLE(words TEXT, 
              ts_query TSPQUERY, 
              start_pos SMALLINT, 
              end_pos SMALLINT) AS
$$    
BEGIN
   RETURN QUERY
    (SELECT *
     FROM   TSP_QUERY_MATCHES(current_setting('default_text_search_config')::REGCONFIG,
                             haystack_arr, 
                             content_tsv, 
                             search_query, 
                             match_limit));
END;
$$
STABLE
LANGUAGE plpgsql;
/*
Function: TSQUERY_TO_TABLE

Accepts: 
- config      REGCONFIG - PGSQL Text Search Language Configuration
- input_query TEXT - the source text to be prepared, by having indexing tokens removed

Divides a TSQuery into phrases separated by logical operators. For each phrase, applies
TSQUERY_TO_TSVECTOR. For each resulting TSVector, we apply TSVECTOR_TO_TABLE.

Returns a table with each record representing a lexeme and position within a TSVector, 
and for posterity each row also contains a phrase_vector TSVECTOR and the corresponding 
phrase_query TSQUERY that produced the vector.

In effect, this divides the TSQuery into a series of equivalent lexeme patters in a TSVector.
*/

CREATE OR REPLACE FUNCTION TSQUERY_TO_TABLE(config REGCONFIG, input_query TSQUERY)
RETURNS TABLE(phrase_vector TSVECTOR, phrase_query TSQUERY, lexeme TEXT, pos SMALLINT) AS
$$
BEGIN
	RETURN QUERY 
	(WITH phrases AS (SELECT DISTINCT(phrase.phrase_vector), phrase.phrase_query 
	                  FROM TSQUERY_TO_TSVECTOR(config, input_query) AS phrase)
      SELECT phrases.phrase_vector, 
             phrases.phrase_query,
             word.lex, 
             word.pos
      FROM phrases, TSVECTOR_TO_TABLE(phrases.phrase_vector) AS word);
END;
$$
STABLE
LANGUAGE plpgsql;

-- OVERLOADS Arity-2 form, to infer the default_text_search_config for parsing
CREATE OR REPLACE FUNCTION TSQUERY_TO_TABLE(input_query TSQUERY)
RETURNS TABLE(phrase_vector TSVECTOR, phrase_query TSQUERY, lexeme TEXT, pos SMALLINT) AS
$$
BEGIN
  RETURN QUERY 
  (SELECT * FROM TSQUERY_TO_TABLE(current_setting('default_text_search_config')::REGCONFIG, 
                                  input_query));
END;
$$
STABLE
LANGUAGE plpgsql;/*
Function: TSQUERY_TO_TSVECTOR

Accepts: 
- config      REGCONFIG - PGSQL Text Search Language Configuration
- input_query TSQuery - a well-formed TSQuery that is may be complex, containing 
  logical and phrase/distance operators

Returns a TABLE where each row contains:
- phrase_vector as a TSVector representation of a phrase
- phrase_query as a TSQuery representation of a phrase pattern

In effect, this function considers a TSQuery to contain a list of phrase queries 
separated by brackets and logical operators.

Though negated terms are removed from the resulting table, other logical operators 
and brackets are ignored, and the table is then a representation of a list of 
phrase patterns in the query
*/

CREATE OR REPLACE FUNCTION TSQUERY_TO_TSVECTOR(config REGCONFIG, input_query TSQUERY)
RETURNS TABLE(phrase_vector TSVECTOR, phrase_query TSQUERY) AS
$$
DECLARE
    input_text TEXT;
BEGIN
    -- Remove Negated query terms and replace <-> with the equivalent <1> distance term
    input_text := replace(querytree(input_query)::TEXT, '<->', '<1>');
    -- Strip Brackets
    input_text := regexp_replace(input_text, '\(|\)', '', 'g');

    RETURN QUERY 
    -- ts_filter + setweight is used to remove dummy lexeme from TSVector
    -- Set everything to weight A, set dummy word to D, filter for A, remove weights
    (SELECT setweight(ts_filter(setweight(setweight(phrase_vec, 'A'), 
                                          'D',
                                          ARRAY['xdummywordx']), 
                                '{a}'), 
                      'D') AS phrase_vector, 
            split_query AS phrase_query
     -- REPLACE_MULTIPLE_STRINGS will replace each of the <n> strings with n dummy word  entries
     FROM (SELECT to_tsvector(config,
                              (SELECT REPLACE_MULTIPLE_STRINGS(split_query, 
                                                               array_agg('<' || g[1] || '>'), 
                                                               array_agg(REPEAT(' xdummywordx ', g[1]::SMALLINT - 1)))
                               -- regexp for all of the <n> terms in a phrase segment
                               FROM regexp_matches(split_query, '<(\d+)>', 'g') AS matches(g))) as phrase_vec,
                  split_query::TSQUERY
           -- Splits Query as Text into a collection of phrases delimiter by AND/OR symbols
           FROM (SELECT regexp_split_to_table(input_text, '\&|\|') AS split_query) AS terms) AS termvec);
END;
$$
STABLE
LANGUAGE plpgsql;

-- OVERLOAD Arity-2 form, to infer the default_text_search_config for parsing
CREATE OR REPLACE FUNCTION TSQUERY_TO_TSVECTOR(input_query TSQUERY)
RETURNS TABLE(phrase_vector TSVECTOR, phrase_query TSQUERY) AS
$$
BEGIN
   RETURN QUERY 
   (SELECT * FROM TSQUERY_TO_TSVECTOR(current_setting('default_text_search_config')::REGCONFIG, input_query));
END;
$$
STABLE
LANGUAGE plpgsql;/*
Function: TSVECTOR_TO_TABLE

Accepts: 
- input_vector TSVECTOR - a TSVector containing BOTH lexemes and positions 

Returns a table of the lexemes and positions of the TSVector, ordered by
position ASC. In effect, this function UNNESTs a TSVector into a table 
of lexemes and positions.

This function can be used on any TSVector that includes positions.
*/

CREATE OR REPLACE FUNCTION TSVECTOR_TO_TABLE(input_vector TSVECTOR)
RETURNS TABLE(lex TEXT, pos SMALLINT) AS
$$
BEGIN
  RETURN QUERY (SELECT lexeme, UNNEST(positions) AS position 
                FROM UNNEST(input_vector)  
                ORDER BY position);
END;
$$
STABLE
LANGUAGE plpgsql;