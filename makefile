

VERSION = 1.0
SCHEMA = public
DB_NAME = postgres
DB_USER = default_user

.PHONY: all compile_sql create_db

all: compile_sql

compile_sql:
	bash package.sh 

create_db:
	psql -U $(DB_USER) -d $(DB_NAME) -f ./sql/init.sql