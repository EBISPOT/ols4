
<a href="https://github.com/EBISPOT/ols4/actions/workflows/test.yml"><img src="https://github.com/EBISPOT/ols4/actions/workflows/test.yml/badge.svg"/></a>

Work in progress

Version 4 of the EMBL-EBI Ontology Lookup Service (OLS), featuring:

* Much faster dataload (hours instead of days)
* Modular dataload pipeline with decoupled, individually testable stages
* Automated CI testing of the dataload with minimal testcase ontologies
* A lossless data representation: everything in the OWL is preserved in the databases
* Support for the latest Solr and Neo4j (no embedded databases, no MongoDB)
* React frontend
* Backwards compatibility with the OLS3 API

This repository contains both the dataloader (`dataload` directory) and the API/webapp server (`server` directory).




# Developing OLS4

OLS is different to most webapps in that its API provides both full text search and recursive graph queries, neither of which are possible and/or performant using traditional RDBMS.
It therefore uses two specialized database servers: [**Solr**](https://solr.apache.org), a Lucene server similar to ElasticSearch; and [**Neo4j**](https://neo4j.com), a graph database. 

The `dataload` directory contains the code which turns OWL ontologies into JSON and CSV datasets which can be loaded into Solr and Neo4j, respectively; and some minimal bash scripts which help with loading them.

The `server` directory contains (1) a Spring Boot application which hosts the OLS API over the above Solr and Neo4j instances; and (2) a React frontend built upon the OLS API.

## Running Solr and Neo4j using Docker

Update the config in `docker_config.json` to your liking. Then:

    docker compose build --no-cache docker compose build --no-cache
    docker compose up --force-recreate --build --always-recreate-deps --attach-dependencies ols4-neo4j ols4-solr

This will build and run the dataload, and start up Solr and Neo4j with your new dataset on ports 7474 and 8983, respectively.  Now you can run the API server and frontend for development.

For the API server, you can set the following environment variables to point it at your local Solr and Neo4j servers:

    OLS_SOLR_HOST=http://localhost:8983
    OLS_NEO4J_HOST=bolt://localhost:7687



## Updating `testcases_expected_output`

If you change something that results in the test output changing (e.g. adding new tests, changing what the output looks like), the CI on this repo will fail.

To fix this, you need to replace the `testcases_expected_output` folder with the new expected output. **You should do this in the same commit as your code/test changes because then we can track exactly what changed in the output.**

First make sure all the JARs are up to date:

    mvn clean package

Then run the script:

    ./test.sh

Remove the existing expected output:

    rm -rf testcases_expected_output

Copy your new output to `testcases_expected_output`:

    cp -r testcases_output testcases_expected_output

You can now add it to your commit:

    git add -A testcases_expected_output






    




