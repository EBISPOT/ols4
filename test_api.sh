#!/usr/bin/env bash

TEST_CONFIGS=$(find testcases | grep json)

rm -rf testcases_output_api/*
mkdir testcases_output_api

IS_FIRST_RUN=1

for f in $TEST_CONFIGS
do

    echo Running test config: $f    

    TEST_FOLDER=$(basename $(dirname $f))
    echo Test folder: $TEST_FOLDER

    mkdir ./testcases_output_api/$TEST_FOLDER
    touch ./testcases_output_api/$TEST_FOLDER/.gitkeep

    export OLS4_CONFIG=$f
    export OLS4_APITEST_OUTDIR=$(pwd)/testcases_output_api/$TEST_FOLDER
    export OLS4_DATALOAD_ARGS="--noDates --loadLocalFiles"
    export BUILDKIT_PROGRESS=plain

    docker-compose down -t 120 -v --rmi all

    if [[ "$IS_FIRST_RUN" == "1" ]]
    then
        docker-compose build --no-cache
        IS_FIRST_RUN=0
    fi

    docker-compose up --force-recreate --always-recreate-deps -V run-api-tests 
    docker-compose down -t 120 -v

done

diff --recursive --exclude=.gitkeep testcases_output_api testcases_expected_output_api/


