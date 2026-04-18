#!/usr/bin/env bash

export OLS4_CONFIG=$(find testcases -name "*.json" | sort | paste -sd, -)
echo "Found $(echo $OLS4_CONFIG | tr ',' '\n' | wc -l) testcase config files"
export OLS4_DATALOAD_ARGS="--loadLocalFiles --noDates"
export BUILDKIT_PROGRESS=plain

rm -rf testcases_output_api/*
mkdir -p testcases_output_api

EXIT_CODE=0

# some mock embeddings for duo we can use in tests
export OLS_EMBEDDINGS_PATH=./testcases/embeddings
# test curated text-to-term mappings (SSSOM)
export OLS4_CURATIONS_PATH=./testcases/curations/*.sssom.tsv
rm -rf tmp out

# Run the full dataload pipeline inside the pre-built ols4-dataload container.
# The container provides Java 21, Rust, Neo4j 2025.03.0, and Solr 9.8.1 —
# no host toolchain or Docker-in-Docker needed.
# Outputs mounted to host for docker-compose:
#   ./out/solr           — full Solr install with data pre-indexed
#   ./out/neo4j/data     — Neo4j database (imported + indexes created)
#   ./out/text_tagger_db.bin
mkdir -p tmp out

docker run --rm \
    -u $(id -u):$(id -g) \
    -v "$PWD/tmp:/opt/ols/tmp" \
    -v "$PWD/out:/opt/ols/out" \
    -v "$PWD/dev-testing/dataload-ci.sh:/opt/ols/dev-testing/dataload-ci.sh:ro" \
    -e OLS4_CONFIG="$OLS4_CONFIG" \
    "${OLS4_DATALOAD_IMAGE:-ols4-dataload:local}" \
    bash -c "cd /opt/ols && ./dev-testing/dataload-ci.sh"

if [[ "$?" != "0" ]]
then
    EXIT_CODE=1
    echo Dataload returned a non-zero exit code
    exit $EXIT_CODE
fi

# Solr data was pre-indexed inside the container above — just start the service.
HOST_UID=$(id -u) HOST_GID=$(id -g) docker compose up -d ols4-solr

echo "Waiting for Solr to be ready..."
for i in {1..30}; do
    if curl -sf "http://localhost:8983/solr/ols4_entities/admin/ping" &>/dev/null; then
        echo "Solr is ready."
        break
    fi
    sleep 2
done

echo "Starting remaining backend services..."
HOST_UID=$(id -u) HOST_GID=$(id -g) docker compose --profile run-api-tests \
    up -d ols4-backend

# Wait for backend
echo "Waiting for backend..."
for i in {1..30}; do
    if curl -sf "http://localhost:8080/api/ontologies?size=1" &>/dev/null; then
        echo "Backend is ready."
        break
    fi
    sleep 3
done

HOST_UID=$(id -u) HOST_GID=$(id -g) docker compose --profile run-api-tests \
    up \
--force-recreate \
--always-recreate-deps \
-V \
--exit-code-from run-api-tests \
run-api-tests 

if [[ "$?" != "0" ]]
then
    EXIT_CODE=1
    echo Test $TEST_FOLDER returned a non-zero exit code, so the API tests will report failure
fi

cat testcases_output_api/apitester4.log

HOST_UID=$(id -u) HOST_GID=$(id -g) docker compose down -t 120 -v

echo API test exit code: $EXIT_CODE
exit $EXIT_CODE


