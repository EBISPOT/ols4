#!/usr/bin/env bash

TEST_CONFIGS=$(find testcases | grep json)

rm -rf testcases_output_api/*

docker-compose build --no-cache

for f in $TEST_CONFIGS
do

TEST_FOLDER=$(basename $(dirname $f))
mkdir ./testcases_output_api/$TEST_FOLDER

export OLS4_CONFIG=$f
export OLS4_APITEST_OUTDIR=$(pwd)/testcases_output_api/$TEST_FOLDER
export OLS4_DATALOAD_ARGS="--noDates --loadLocalFiles"
export BUILDKIT_PROGRESS=plain

docker-compose up --force-recreate run-api-tests 

done

diff --brief --recursive testcases_output_api testcases_expected_output_api/





