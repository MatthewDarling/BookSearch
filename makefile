VERSION = 1.0
SCHEMA = public
DB_HOST = localhost
DB_USER = default_user
DB_PASS = default_password
DB_PORT = 5432

.PHONY: all compile_sql create_db load_files kill_db

all: compile_sql

compile_sql:
	bash package.sh 

create_db:
	psql -U $(DB_USER) -d postgres -f ./sql/init.sql
	psql -U $(DB_USER) -d booksearch -f ./sql/create_extensions.sql
	flyway migrate

load_files:
	DB_HOST=$(DB_HOST) DB_NAME=booksearch DB_USER=$(DB_USER) DB_PASS=$(DB_PASS) DB_PORT=$(DB_PORT) bb --classpath . --main load-gutenberg

kill_db:
	psql -U $(DB_USER) -d postgres -f ./sql/testing_cleanup.sql
