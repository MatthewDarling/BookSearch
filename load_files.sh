#!/usr/bin/env sh

for i in $(find `pwd`/public_domain_texts/*.txt); do
    dest=$(basename $i)
    cp $i /tmp/$dest
    psql -U $(whoami) -v filename="'/tmp/$dest'" -d booksearch -f ./sql/load_public_domain_texts.sql
done
