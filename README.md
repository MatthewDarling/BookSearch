![image](https://github.com/MatthewDarling/BookSearch/assets/6935998/44034698-f08b-4120-93b8-43f3756bf3f6)
# BookSearch
This is a small Clojure(script) + PostgreSQL application, which, on its surface, is a user interface that allows for searching and content retrieval of large texts.  The purpose and reason-to-be for this application is twofold:

## Purpose 1. Vetting Performance & Functionality of PostgreSQL Full-Text Indexing Techniques
Recently, we have been working on two (2) extensions for PostgreSQL, both of which are focussed on PGSQL's text search functionality, performance and limitations.
#### 1A. pg_ts_semantic_headline
The `PG_SEMANTIC_HEADLINE` extension provides PGSQL with the ability to highlight multi-word phrases and dratically improves search result highlighting of multi-word phrases and boolean expressions. Likewise, the extension offers a pattern for pre-realizing lookup vectors and content retrieval arrays to a DB table, that purports to outperform the built-in PGSQL functions by a time factor of 5x 20x faster.
#### 1B. pg_large_text_search
PostgreSQL's Text Search functionality has a number of limitation (read: hard limits) on the size and scope of text it is able to properly index. The `PG_LARGE_TEXT_SEARCH` extension captures these limitations, and given a large text, will divide that text into n fragments, where each fragment has been proven to conform to the PGSQL limitations. In indexing a large text file, one would save n fragments in a lookup table, each with a valid and conformant TSVector lookup column.

## Purpose 2. Greenfield Clojure/Clojurescript Development
We wanted to create an opportunity to explore modern tools for Clojurescript, and more specifically novel approaches to React.JS, effects and hooks. The goal is to broaden our understanding and patterning of frontend state management without component lifecycle methods, and take advantage of next-generation tooling in an otherwise stable Clojure ecosystem.
#### Technologies Used
*Frontend*
- `UiX` - https://github.com/pitch-io/uix - Introduces React Effects and Hooks instead of React Lifecycle methods; doea away with `hiccup` to greatly improve clinet-side rendering speed
- `cljs-ajax` - https://github.com/JulianBirch/cljs-ajax - Preferred to `cljs-http` for its overt declaration of `on-success` and `on-error` handlers, and its elimination of clojure.core.async/go.
- `semantic-ui-react` - https://react.semantic-ui.com/ - Brilliant semantic classing structure, quite attractive, and has a name befitting of this project.
- `shadow-cljs` - https://github.com/thheller/shadow-cljs - compile your ClojureScript code with a focus on simplicity and ease of use.
*Backend*
- PostgreSQL - https://www.postgresql.org/ -  open source object-relational database system with over 35 years of active development that has earned it a strong reputation for reliability, feature robustness, and performance... except when it comes to the performance of full-text search... :)
- Flyway - https://flywaydb.org/ - Database Schema Management and Automation
- HoneySQL - https://github.com/seancorfield/honeysql - Version 2! 
- Next.JDBC - https://github.com/seancorfield/next-jdbc
- Reitit Router - https://github.com/metosin/reitit

## UI in Pictures

![image](https://github.com/MatthewDarling/BookSearch/assets/6935998/f01c9331-fad7-40b6-9f9f-59310e4a3ec7)

| fast TS_FAST_HEADLINE |builtin TS_HEADLINE |
| --- | --- |
| ![image](https://github.com/MatthewDarling/BookSearch/assets/6935998/6215ca00-bda1-4ea9-934a-aab068975a4f) |![image](https://github.com/MatthewDarling/BookSearch/assets/6935998/570ec151-95f3-4d63-ac1f-7762f17b333f) |
| ![image](https://github.com/MatthewDarling/BookSearch/assets/6935998/19b1563a-bc05-439f-838a-f446196fc2c4) |![image](https://github.com/MatthewDarling/BookSearch/assets/6935998/bc78a51a-6ade-498e-a403-6b08d9c76c4f) |
| ![image](https://github.com/MatthewDarling/BookSearch/assets/6935998/94297ed6-66c1-4358-9910-ce270a856843) |![image](https://github.com/MatthewDarling/BookSearch/assets/6935998/843e0d53-502a-457d-9b82-5a1dfdf33a9c) |
| ![image](https://github.com/MatthewDarling/BookSearch/assets/6935998/5ecdaabe-7069-40f4-b71e-2c3505e9a1b3) |![image](https://github.com/MatthewDarling/BookSearch/assets/6935998/601adf6b-c833-4de0-93e8-48b51f299c4a) |
| ![image](https://github.com/MatthewDarling/BookSearch/assets/6935998/14fd9ead-8fcc-4ffe-9468-cc98fec69e31) |![image](https://github.com/MatthewDarling/BookSearch/assets/6935998/046d9502-4bff-45bf-80c4-405eeb272433) |



## Setup
### Prerequisites
- PostgreSQL 14+
- Flyway : https://www.red-gate.com/products/flyway/community/download/
- make : O/S-dependant CLI tool
- yarn : https://yarnpkg.com/
  
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
** TODO: improve the performance of this script... :)

### Step 4: Start the Server
From the `BookSearch` directory, run the following to install backend dependancies:
```
clj -M:repl
```
To start the application REPL in the core server namespace. Once loaded, you will see a `app.server=>` prompt. In that prompt, type:
```
(-main)
```
to open the server to requests. This should produce:
```
app.server=> (-main)
server running in port 3000
#object[org.eclipse.jetty.server.Server 0x4678320a "Server@4678320a{STARTED}[11.0.18,sto=0]"]
```

### Step 5: Start the Client Application
To start the client application for development and hot-reloading of code, use:
```shell
yarn      # install NPM deps
yarn dev  # run dev build in watch mode with CLJS REPL
```
and this should produce something like:
```
shadow-cljs - HTTP server available at http://localhost:8080
shadow-cljs - server version: 2.25.8 running at http://localhost:9630
shadow-cljs - nREPL server started on port 50868
shadow-cljs - watching build :app
[:app] Configuring build.
[:app] Compiling ...
[:app] Build completed. (845 files, 0 compiled, 0 warnings, 4.40s)
```
Visit http://localhost:8080 to see the application running.


## Production
```shell
yarn release # build production bundle
```

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

1. Implement logical query_mode - DONE!
2. Implement ts_fast_headline strategy - DONE!
3. Improve API response handling, pr-str is probably not the right approach
4. Implement front-end routing - DONE!
5. Maybe offer searching within a single document, displaying its text - DONE!
