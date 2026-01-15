#!/usr/bin/env bash

export OLS4_CONFIG=$(find testcases | grep json | paste -sd,)
export OLS4_DATALOAD_ARGS="--loadLocalFiles"
export BUILDKIT_PROGRESS=plain

rm -rf testcases_output_api/*
mkdir -p testcases_output_api

EXIT_CODE=0

# some mock embeddings for duo we can use in tests
export OLS_EMBEDDINGS_PATH=./testcases/embeddings

rm -rf tmp out
./dataload.sh

if [[ "$?" != "0" ]]
then
    EXIT_CODE=1
    echo Dataload returned a non-zero exit code
    exit $EXIT_CODE
fi

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


