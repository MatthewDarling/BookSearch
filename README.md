# BookSearch
This is a small Clojure(script) + PostgreSQL application, which, on its surface, is a user interface that allows for searching and content retrieval of large texts.  The purpose and reason-to-be for this application is twofold:

### Purpose 1. Vetting Performance & Functionality of PostgreSQL Full-Text Indexing Techniques
Recently, we have been working on two (2) extensions for PostgreSQL, both of which are focussed on PGSQL's text search functionality, performance and limitations.

#### 1A. pg_ts_semantic_headline
The `PG_SEMANTIC_HEADLINE` extension provides PGSQL with the ability to highlight multi-word phrases and dratically improves search result highlighting of multi-word phrases and boolean expressions. Likewise, the extension offers a pattern for pre-realizing lookup vectors and content retrieval arrays to a DB table, that purports to outperform the built-in PGSQL functions by a time factor of 5x 20x faster.

#### 1B. pg_large_text_search
PostgreSQL's Text Search functionality has a number of limitation (read: hard limits) on the size and scope of text it is able to properly index. The `PG_LARGE_TEXT_SEARCH` extension captures these limitations, and given a large text, will divide that text into n fragments, where each fragment has been proven to conform to the PGSQL limitations. In indexing a large text file, one would save n fragments in a lookup table, each with a valid and conformant TSVector lookup column.

### Purpose 2. Greenfield Clojure/Clojurescript Development
We wanted to create an opportunity to explore modern tools for Clojurescript, and more specifically novel approaches to React.JS, effects and hooks. The goal is to broaden our understanding and patterning of frontend state management without component lifecycle methods, and take advantage of next-generation tooling in an otherwise stable Clojure ecosystem.

## Setup
### Prerequisites
- PostgreSQL 14+
- Flyway : https://www.red-gate.com/products/flyway/community/download/
- make : O/S-dependant CLI tool
- 
### Step 1: Make PGSQL extensions from submodules
In order to build to latest version of the PGSQL Extensions we are using, where each is linked as a git submodule under the project root, we will execute the following command:
```
cd BookSearch
make
```
The `make` command will install the following PostgreSQL extensions:
- `pg_large_text_search` - https://github.com/thevermeer/pg_large_text_search - tools for dividing text into fragments that conform to TSVector limitations
- `pg_ts_semantic_headline` - https://github.com/thevermeer/pg_ts_semantic_headline - tools for improve ts_headline functionality and performing content retrieval 5x-20x than built-in `ts_headline` function.
- `pg_tap` - https://pgtap.org/ - Pure SQL Test automation framework.

### Step 2: Create the Database
Setup requires an install of PostgreSQL 14+, and given a connection, will create its own database via flyway. Providing your PGSQL username below:
```shell
make create_db DB_USER=<<USER NAME>>
```
This will create a `ROLE` for the application to connect to Postgres with, and make that `ROLE` the `OWNER` of the `booksearch` database. With the new `booksearch` database, we install our extensions (ie. `CREATE EXTENSTION`), and then run `flyway migrate` to build our table schema from a sequential series of migrations found in the `sql/booksearch` folder.

### Step 3: Fill the Database with Content
The building of search indices is done via `TRIGGER` in the table schema; that is, all we need to do to index content, is to add texts to the `files` table. One could easily do this by inserting into `files`, like so:
```
INSERT INTO files
(filename, author, title, content)
('TwoCities.txt', 'Charles Dickens', 'A tale of Two Cities', 'It was the best of times, it was the worst of times...')
```
That said, we also have filled `public_domain_texts` with a selection of the 50 most dowloaded texts from Project Gutenberg (https://www.gutenberg.org/browse/scores/top), and they can be imported using:
```
make load_files
```
Careful! This script can take a few minutes to run. Grab a drink and take a breath.
** TODO: fix this script... :)

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






### Start a Clojure REPL for the server

```
clj -M:repl
```

### Manage the server

``` clojure
(-main) ;; to start the server
(reset) ;; to update the server with new changes
```

# To Do List

1. Implement logical query_mode
2. Implement ts_fast_headline strategy
3. Improve API response handling, pr-str is probably not the right approach
4. Implement front-end routing
5. Maybe offer searching within a single document, displaying its text
