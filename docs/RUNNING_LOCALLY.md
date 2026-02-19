
## Development: Running OLS4 locally

OLS is intended to be run as a containerised application. However, for some debugging scenarios it may be useful to run OLS non-containerised (i.e. outside of Docker).

Software requirements are as follows:

1. Java 21. Later versions of Java are probably fine.
2. Maven 3.x.x
3. Neo4j 2025.03.0-community
4. Solr 9.8.1

### Acquire source and build

Clone repo:

    git clone git@github.com:EBISPOT/ols4.git

Build Java components (dataload and backend):

    mvn clean package

Build frontend:

    cd frontend
    npm install

### Test testcases from dataload to UI

The scripts below assume you have the following environment variables set:

`NEO4J_HOME`

`SOLR_HOME`

`OLS4_HOME` - this should point to the root folder where you have the OLS4 code.

Change the directory to $OLS4_HOME.

    cd $OLS4_HOME

To load a testcase and start Neo4J and Solr, run:

    ./dev-testing/teststack.sh <rel_json_config_url> <rel_output_dir>

where `<rel_json_config_url>` can be a JSON config file or a directory with JSON file, and `<rel_outdir>`
the output directory, both relative from $OLS4_HOME, i.e.:

    ./dev-testing/teststack.sh ./testcases/owl2-primer/minimal.json ./output

or if you want to load all testcases, you can use

    ./dev-testing/teststack.sh ./testcases ./output

If you need to set the Java heap size, you can set the environment the JAVA_OPTS variable as follows:

     export JAVA_OPTS="-Xms5G -Xmx10G"

Once Neo4J and Solr is up, to start the backend (REST API) you can run:

    ./dev-testing/start-backend.sh

Once the backend is up, you can start the frontend with:

    ./dev-testing/start-frontend.sh

Once you are done testing, to stop everything:

    ./stopNeo4JSolr.sh


### Running the dataload locally

In some cases it may be useful to run the dataload outside of Docker.

All related files for loading and processing data are in `dataload`.
First, make sure the configuration files (that determine which ontologies to load) are ready and to build all the JAR files:

    cd dataload
    mvn clean package

#### Pre-download RDF

    java \
    -DentityExpansionLimit=0 \
    -DtotalEntitySizeLimit=0 \
    -Djdk.xml.totalEntitySizeLimit=0 \
    -Djdk.xml.entityExpansionLimit=0 \
    -jar predownloader.jar \
    --config <CONFIG_FILE> \
    --downloadPath <DOWNLOAD_PATH>

#### Convert RDF to JSON

    java \
    -DentityExpansionLimit=0 \
    -DtotalEntitySizeLimit=0 \
    -Djdk.xml.totalEntitySizeLimit=0 \
    -Djdk.xml.entityExpansionLimit=0 \
    -jar rdf2json.jar \
    --downloadedPath <DOWNLOAD_PATH> \
    --config <CONFIG_FILE> \
    --output <LOCAL_DIR>/output_json/ontologies.json

#### Run ontologies linker

    java \
    -jar linker.jar \
    --input <LOCAL_DIR>/output_json/ontologies.json \
    --output <LOCAL_DIR>/output_json/ontologies_linked.json

#### Convert JSON to Neo4j CSV

    ols_json2neo \
    --input <LOCAL_DIR>/output_json/ontologies_linked.json \
    --outDir <LOCAL_DIR>/output_csv/ \
    --manifest <LOCAL_DIR>/output_json/linker_manifest.json

#### Create Neo4j from CSV

Run Neo4j `import` command:

    ./neo4j-admin database import full \
    --ignore-empty-strings=true \
    --legacy-style-quoting=false \
    --array-delimiter="|" \
    --multiline-fields=true \
    --read-buffer-size=134217728 \
    $(<LOCAL_DIR>/make_csv_import_cmd.sh)

Here is a sample `make_csv_import_cmd.sh` file:

    for f in ./output_csv/*_ontologies.csv
    do
    echo -n "--nodes=$f "
    done
    
    for f in ./output_csv/*_classes.csv
    do
    echo -n "--nodes=$f "
    done
    
    for f in ./output_csv/*_properties.csv
    do
    echo -n "--nodes=$f "
    done
    
    for f in ./output_csv/*_individuals.csv
    do
    echo -n "--nodes=$f "
    done
    
    for f in ./output_csv/*_edges.csv
    do
    echo -n "--relationships=$f "
    done

#### Make Neo4j indexes

Start Neo4j locally and then run the sample database commands, which are also defined in `create_indexes.cypher` inside the `dataload` directory:

    CREATE INDEX FOR (n:OntologyClass) ON n.id;
    CREATE INDEX FOR (n:OntologyIndividual) ON n.id;
    CREATE INDEX FOR (n:OntologyProperty) ON n.id;
    CREATE INDEX FOR (n:OntologyEntity) ON n.id;
    
    CALL db.awaitIndexes(10800);

After creating the indexes, stop Neo4j as needed.

#### Convert JSON output to Solr JSON

    ols_json2solr \
    --input <LOCAL_DIR>/output_json/ontologies_linked.json \
    --outDir <LOCAL_DIR>/output_jsonl/

#### Update Solr indexes

Before running Solr, make sure to copy the configuration (`solr_config`) from inside `dataload` directory to local, e.g., `<SOLR_DIR>/server/solr/`.
Then, start Solr locally and use the generated JSON files to update. See sample commands below:

    wget \
    --method POST --no-proxy -O - --server-response --content-on-error=on \
    --header="Content-Type: application/json" \
    --body-file <LOCAL_DIR>/output_jsonl/ontologies.jsonl \
    http://localhost:8983/solr/ols4_entities/update/json/docs?commit=true
    
    wget \
    --method POST --no-proxy -O - --server-response --content-on-error=on \
    --header="Content-Type: application/json" \
    --body-file <LOCAL_DIR>/output_jsonl/classes.jsonl \
    http://localhost:8983/solr/ols4_entities/update/json/docs?commit=true
    
    wget --method POST --no-proxy -O - --server-response --content-on-error=on \
    --header="Content-Type: application/json" \
    --body-file <LOCAL_DIR>/output_jsonl/properties.jsonl \
    http://localhost:8983/solr/ols4_entities/update/json/docs?commit=true
    
    wget --method POST --no-proxy -O - --server-response --content-on-error=on \
    --header="Content-Type: application/json" \
    --body-file <LOCAL_DIR>/output_jsonl/individuals.jsonl \
    http://localhost:8983/solr/ols4_entities/update/json/docs?commit=true
    
    wget --method POST --no-proxy -O - --server-response --content-on-error=on \
    --header="Content-Type: application/json" \
    --body-file <LOCAL_DIR>/output_jsonl/autocomplete.jsonl \
    http://localhost:8983/solr/ols4_autocomplete/update/json/docs?commit=true

Update `ols4_entities` core:

    wget --no-proxy -O - --server-response --content-on-error=on \
    http://localhost:8983/solr/ols4_entities/update?commit=true

Update `ols4_autocomplete` core:

    wget --no-proxy -O - --server-response --content-on-error=on \
    http://localhost:8983/solr/ols4_autocomplete/update?commit=true

After updating the indexes, stop Solr as needed.

#### Create data archives for Solr and Neo4j

Finally, create archives for both Solr and Neo4j data folders.

    tar --use-compress-program="pigz --fast --recursive" \
    -cf <LOCAL_DIR>/neo4j.tgz -C <LOCAL_DIR>/neo4j/data .

    tar --use-compress-program="pigz --fast --recursive" \
    -cf <LOCAL_DIR>/solr.tgz -C <LOCAL_DIR>/solr/server solr

### Running the API server backend locally

The API server Spring Boot application located in `backend`. Set the following environment variables to point it at your
local (Dockerized) Solr and Neo4j servers:

    OLS_SOLR_HOST=http://localhost:8983
    OLS_NEO4J_HOST=bolt://localhost:7687

### Running the frontend locally

The frontend is a React application in `frontend`. See [frontend docs](frontend/README.md)
for details on how to run the frontend.
