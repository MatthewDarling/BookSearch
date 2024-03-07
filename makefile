

VERSION = 1.0
SCHEMA = public
DB_NAME = default_database
DB_USER = default_user

.PHONY: all compile_sql create_db fill_db kill_db

all: compile_sql

compile_sql:
	bash package.sh 

create_db:
	psql -U $(DB_USER) -d postgres -f ./sql/init.sql
	psql -U $(DB_USER) -d booksearch -f ./sql/create_extensions.sql
	flyway migrate

fill_db:
	psql -U $(DB_USER) -v path="'$(shell pwd)/public_domain_texts'" -d booksearch -f ./sql/load_public_domain_texts.sql

kill_db:
	psql -U $(DB_USER) -d postgres -f ./sql/testing_cleanup.sql