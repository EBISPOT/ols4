#!/usr/bin/env bash

TEST_CONFIGS=$(find testcases | grep json)
#TEST_CONFIGS=testcases/hierarchical-properties/config.json

rm -rf testcases_output_api/*
mkdir testcases_output_api

IS_FIRST_RUN=1
EXIT_CODE=0

for f in $TEST_CONFIGS
do

    echo Running test config: $f    

    TEST_FOLDER=$(basename $(dirname $f))
    echo Test folder: $TEST_FOLDER

    mkdir ./testcases_output_api/$TEST_FOLDER
    touch ./testcases_output_api/$TEST_FOLDER/.gitkeep

    export OLS4_CONFIG=$f
    export OLS4_APITEST_OUTDIR=$(pwd)/testcases_output_api/$TEST_FOLDER
    export OLS4_APITEST_COMPAREDIR=$(pwd)/testcases_expected_output_api/$TEST_FOLDER
    export OLS4_DATALOAD_ARGS="--loadLocalFiles"
    export BUILDKIT_PROGRESS=plain

    docker compose down -t 120 -v --rmi all

    if [[ "$IS_FIRST_RUN" == "1" ]]
    then
        docker compose build --no-cache
        IS_FIRST_RUN=0
    fi

    docker compose --profile run-api-tests \
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

    cat $OLS4_APITEST_OUTDIR/apitester4.log

    docker compose down -t 120 -v

done

echo API test exit code: $EXIT_CODE
exit $EXIT_CODE


