CREATE TABLE IF NOT EXISTS files (file_id SERIAL PRIMARY KEY,
                                  filename TEXT NOT NULL,
                                  title TEXT NOT NULL,
                                  author TEXT NOT NULL,
                                  content TEXT NOT NULL);

CREATE TABLE IF NOT EXISTS file_search_index (file_id INTEGER REFERENCES files(file_id),
                                              sequence_no INTEGER,
                                              content_array TEXT[] NOT NULL,
                                              content_tsv TSVECTOR)
