
## Development: Running OLS4 locally

OLS is intended to be run as a containerised application. However, for some debugging scenarios it may be useful to run OLS non-containerised (i.e. outside of Docker).

Software requirements are as follows:

1. Java 21. Later versions of Java are probably fine.
2. Maven 3.x.x
3. PostgreSQL 16 with pgvector

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

`OLS4_HOME` - this should point to the root folder where you have the OLS4 code.

Change the directory to $OLS4_HOME.

    cd $OLS4_HOME

To load a testcase and start PostgreSQL, run:

    ./dev-testing/teststack.sh <rel_json_config_url> <rel_output_dir>

where `<rel_json_config_url>` can be a JSON config file or a directory with JSON file, and `<rel_outdir>`
the output directory, both relative from $OLS4_HOME, i.e.:

    ./dev-testing/teststack.sh ./testcases/owl2-primer/minimal.json ./output

or if you want to load all testcases, you can use

    ./dev-testing/teststack.sh ./testcases ./output

If you need to set the Java heap size, you can set the environment the JAVA_OPTS variable as follows:

     export JAVA_OPTS="-Xms5G -Xmx10G"

Once PostgreSQL is up, to start the backend (REST API) you can run:

    ./dev-testing/start-backend.sh

Once the backend is up, you can start the frontend with:

    ./dev-testing/start-frontend.sh

Once you are done testing, to stop PostgreSQL:

    ./dev-testing/stop-postgres.sh


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

#### Convert JSON to PostgreSQL TSV

    ols_json2postgres \
    --input <LOCAL_DIR>/output_json/ontologies_linked.json \
    --outDir <LOCAL_DIR>/output_tsv/

#### Create PostgreSQL database from pgbin files

Use the `load_into_postgres.py` script to initialize a PostgreSQL instance and bulk-load the pgbin files:

    python3 load_into_postgres.py <LOCAL_DIR>/postgres_out <LOCAL_DIR>/output_tsv/

#### Create data archive for PostgreSQL

Finally, create an archive of the PostgreSQL data folder.

    tar --use-compress-program="pigz --fast --recursive" \
    -cf <LOCAL_DIR>/postgres.tgz -C <LOCAL_DIR>/postgres_out/data .

### Running the API server backend locally

The API server Spring Boot application located in `backend`. Set the following environment variables to point it at your
local (Dockerized) PostgreSQL server:

    OLS_POSTGRES_HOST=localhost
    OLS_POSTGRES_PORT=5432

### Running the frontend locally

The frontend is a React application in `frontend`. See [frontend docs](frontend/README.md)
for details on how to run the frontend.
