# The bsh script concatenates all files in the /sql directory and into a single install package

cd pg_large_text_search
make && make install
cd ../pg_tap
make && make install
cd ../pg_ts_semantic_headline
make && make install