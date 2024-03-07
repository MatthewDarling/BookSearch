# BookSearch
Application for viewing and searching inside books

## Quick setup
```shell
npx create-uix-app@latest my-app # bare-bones project
npx create-uix-app@latest my-app --re-frame # adds re-frame setup
```

## Development
```shell
yarn # install NPM deps
yarn dev # run dev build in watch mode with CLJS REPL
```

## Production
```shell
yarn release # build production bundle
```

* Install Postgres
* [Install Flyway](https://documentation.red-gate.com/fd/command-line-184127404.html?_gl=1*8e1rlp*_ga*MTMxMTcwMTk5OC4xNzA5NjU0MjUw*_ga_X7VDRWRT4P*MTcwOTY1NDI0OS4xLjAuMTcwOTY1NDI0OS42MC4wLjA.)
* Apply the contents of sql/init.sql somehow (not yet automated)
* Apply migrations:
    flyway migrate


## Make Directives

## Install Prerequisite Extensions
```
make
```

### Create DB

### Fill DB

### Kill DB

### Start from Scratch with new content
```
make kill_db DB_USER=thevermeer && make create_db DB_USER=thevermeer &&  make fill_db DB_USER=thevermeer
```

