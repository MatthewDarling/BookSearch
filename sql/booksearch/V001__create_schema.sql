CREATE EXTENSION unaccent;

CREATE TABLE IF NOT EXISTS files (file_id SERIAL PRIMARY KEY,
                                  filename TEXT NOT NULL,
                                  title TEXT NOT NULL,
                                  author TEXT NOT NULL,
                                  content TEXT NOT NULL);

