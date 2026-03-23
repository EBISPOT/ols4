<a href="https://github.com/EBISPOT/ols4/actions/workflows/test.yml"><img src="https://github.com/EBISPOT/ols4/actions/workflows/test.yml/badge.svg"/></a>

The Ontology Lookup Service (OLS) is a repository for biomedical ontologies that aims to provide a single point of access to the latest ontology versions. It provides a [website](https://www.ebi.ac.uk/ols4/), [REST API](https://www.ebi.ac.uk/ols4/api-docs), and [MCP server](https://www.ebi.ac.uk/ols4/mcp).

See also:

* The public OLS instance at EMBL-EBI: <b>[https://www.ebi.ac.uk/ols4/](https://www.ebi.ac.uk/ols4/)</a></b>
* [<i>OLS4: a new Ontology Lookup Service for a growing interdisciplinary knowledge ecosystem</i>](https://academic.oup.com/bioinformatics/article/41/5/btaf279/8125017)
* [REST API docs](https://www.ebi.ac.uk/ols4/api-docs)
* MCP endpoint (Streamable HTTP): `https://www.ebi.ac.uk/ols4/api/mcp`

If you use OLS in your work, please cite [our recent publication in <i>Bioinformatics</i>](https://academic.oup.com/bioinformatics/article/41/5/btaf279/8125017).

---

This repository contains three projects:

* The dataloader (`dataload` directory)
* The API server (`backend` directory)
* The React frontend (`frontend` directory)

# Deploying OLS4

First run the OLS dataload (requires Docker):

    OLS4_CONFIG=./dataload/configs/efo.json ./dataload.sh

This will create Solr and Neo4j databases in the `out` directory. Now start the OLS stack:

    HOST_UID=$(id -u) HOST_GID=$(id -g) docker compose up

You should now be able to access the OLS4 frontend at `http://localhost:8081`.

If you want to test it with your own ontology, copy the OWL or RDFS ontology file into this repository folder.  Then make a new config file for your ontology; you can use `efo.json` from `dataload/configs` as a
template. For the `ontology_purl` property in the config, use the relative path in this repository to your ontology e.g. `./myontology.owl`. Then follow the above steps for efo with the config filename you created.

## Deployment: Using Kubernetes with GitHub Packages

To deploy OLS4 using Kubernetes, Docker images built and uploaded to this repository (using GitHub Packages) are
utilized. Software requirements are as follows:

1. Kubernetes command-line tool, _kubectl_
2. Kubernetes package manager, _helm_

### Create data archives for Solr and Neo4j

First run the OLS dataload (requires Docker):

    OLS4_CONFIG=./dataload/configs/efo.json ./dataload.sh

This will create Solr and Neo4j databases in the `out` directory.

### Startup OLS4 deployment

Uninstall existing `ols4` deployments, if any, before installing a new one. Do not forget to set `KUBECONFIG`
environment variable.

**IMPORTANT**: The use of `imageTag` is to specify the Docker image (uploaded to this repository) that will be used in the deployment. If not familiar, simply
use either the `dev` or `stable` image.

    export KUBECONFIG=<K8S_CONFIG>
    helm install ols4 <OLS4_DIR>/k8chart/ols4 --set imageTag=dev

# Developing OLS4

OLS is different to most webapps in that its API provides both full text search and recursive graph queries, neither of
which are possible and/or performant using traditional RDBMS. It therefore uses two specialized database servers: [**Solr**](https://solr.apache.org), a Lucene server similar to ElasticSearch; and [**Neo4j**](https://neo4j.com), a graph
database which is also used to store embedding vectors.

* The `dataload` directory contains the code which turns ontologies from RDF (specified using OWL and/or RDFS) into JSON
  and CSV datasets which can be loaded into Solr and Neo4j, respectively; and some minimal bash scripts which help with
  loading them.
* The `backend` directory contains a Spring Boot application which hosts the OLS API over the above Solr and Neo4j
  instances
* The `frontend` directory contains the React frontend built upon the `backend` above.

![OLS4 overview](docs/overview.png)

## Running OLS4 components using Docker

You can run OLS4, or any combination of its consistuent parts (dataload, backend, frontend) in Docker. When developing,
it is often useful to run, for example, just Solr and Neo4j in Docker, while running the API server locally; or to run
Solr, Neo4j, and the backend API server in Docker while running the frontend locally.

First install the latest version of Docker Desktop (or compatible, such as Rancher Desktop) if you are on Mac or Windows. This now includes the `docker compose`
command. If you are on Linux, make sure you have the `docker compose` plugin
installed (`apt install docker.io docker-compose-plugin` on Ubuntu).

Then, start up the components you would like to run. For example, Solr and Neo4j only (to develop the backend API server
and/or frontend):

    docker compose up --force-recreate --build --always-recreate-deps --attach-dependencies ols4-solr ols4-neo4j

This will start up Solr and Neo4j with your new dataset on ports 8983 and 7474,
respectively. To start Solr and Neo4j **AND** the backend API server (to develop the frontend):

    docker compose up --force-recreate --build --always-recreate-deps --attach-dependencies ols4-solr ols4-neo4j ols4-backend

To start everything, including the frontend:

    docker compose up --force-recreate --build --always-recreate-deps --attach-dependencies ols4-solr ols4-neo4j ols4-backend ols4-frontend


## Making the tests pass

OLS has a comprehensive suite of automated CI tests for the dataload and API. If code changes change the output such that it no longer matches `testcases_expected_output` (mock dataload) and/or `testcases_expected_output_api` (full Nextflow dataload and API) the CI will fail, and you will need to update the expected output.

Before running your testcases, ensure that your work is already committed. Create a new branch based on the branch you worked on
but with a `-testcases` suffix. I.e., if your branch is called "fix-xyz", the new branch for the testcases will be 
`fix-xyz-testcases`. We commit testcases to a separate branch due to the large number of files updated when testcases are run.

### Testing the mock dataload

First, build an up to date Docker image for the dataload:

    docker build -t ols4-dataload:local -f ./dataload/Dockerfile . --no-cache

Remove the old `testcases_expected_output` contents from your local working tree:

    rm -rf testcases_expected_output/*

Re-populate `testcases_expected_output` directory with updated test output:

    docker run \
        -v $(pwd)/testcases_expected_output:/opt/ols/testcases_output \
        ols4-dataload:local \
        bash -c "cd /opt/ols && ./test_dataload.sh"

Now you can inspect any changes to the files in `testcases_expected_output` and make sure they are intentional, e.g. using `git diff` or from VS Code. When you are happy, stage and commit the updated `testcases_expected_output`.

    git add -A testcases_expected_output
    git commit -m "Update testcase output"

### Testing the full Nextflow dataload and API

First follow the instructions above for testing the mock dataload. Then build up to date Docker images for remainder of the OLS stack:

For backend use following docker command:

    docker build -t ols4-backend:local -f ./backend/Dockerfile . --no-cache

For frontend use following docker command:

    docker build -t ols4-frontend:local -f ./frontend/Dockerfile ./frontend --no-cache

For apitester use following docker command:

    docker build -t ols4-apitester4:local -f ./apitester4/Dockerfile ./apitester4 --no-cache

and then run the API tests with the new images:

    export OLS4_BACKEND_IMAGE=ols4-backend:local
    export OLS4_FRONTEND_IMAGE=ols4-frontend:local  
    export OLS4_APITESTER_IMAGE=ols4-apitester4:local
    HOST_UID=$(id -u) HOST_GID=$(id -g) docker compose --profile run-api-tests build --no-cache

Run the test script to produce a `testcases_output_api` directory:

    OLS4_DATALOAD_IMAGE=ols4-dataload:local ./test_api.sh

The log file `testcases_output_api/apitester4.log` contains diff information. You can also manually compare the files in `testcases_output_api` with the files in `testcases_expected_output_api`. Once you are happy the changes are intentional, replace the old test outputs with the new ones:

    rm -rf testcases_expected_output_api
    mv testcases_output_api testcases_expected_output_api

Stage and commit the updated `testcases_expected_output_api`:

    git add -A testcases_expected_output_api
    git commit -m "Update API testcase output"

## Running OLS locally

OLS is intended to be run as a containerised application. However, for some debugging scenarios it may be useful to run OLS non-containerised (i.e. outside of Docker). Best effort instructions are provided in [RUNNING_LOCALLY.md](docs/RUNNING_LOCALLY.md), though these may not be suitable for all platforms.

# Reasoning
OLS does not do any OWL reasoning on ontologies at all. The assumption is that ontologies loaded into OLS are pre-reasoned. 

