#!/usr/bin/env bash

TEST_CONFIGS=$(find testcases | grep json)

rm -rf testcases_output_api/*

IS_FIRST_RUN=1

for f in $TEST_CONFIGS
do

    echo Running test config: $f    

    TEST_FOLDER=$(basename $(dirname $f))
    echo Test folder: $TEST_FOLDER

    mkdir ./testcases_output_api/$TEST_FOLDER

    export OLS4_CONFIG=$f
    export OLS4_APITEST_OUTDIR=$(pwd)/testcases_output_api/$TEST_FOLDER
    export OLS4_DATALOAD_ARGS="--noDates --loadLocalFiles"
    export BUILDKIT_PROGRESS=plain

    if [[ "$IS_FIRST_RUN" == "1" ]]
    then
        docker-compose build --no-cache
        IS_FIRST_RUN=0
    fi

    docker-compose up --force-recreate run-api-tests 

done

diff --recursive --exclude=.gitkeep testcases_output_api testcases_expected_output_api/





