#!/usr/bin/env bash

TEST_CONFIGS=$(find testcases | grep json)

rm -rf testcases_output/*
mkdir testcases_output

export OLS_ORCID_NAME_FIXTURE="${OLS_ORCID_NAME_FIXTURE:-$(pwd)/dev-testing/orcid-name-fixture.json}"

for f in $TEST_CONFIGS
do

BASENAME=$(basename $f .json)
DIRNAME=$(basename $(dirname $f))

TEST_FOLDER=$DIRNAME/$BASENAME
mkdir -p ./testcases_output/$DIRNAME
mkdir -p ./testcases_output/$TEST_FOLDER

./dev-testing/create_datafiles.sh $f ./testcases_output/$TEST_FOLDER --loadLocalFiles --noDates
done

diff --recursive --exclude=.gitkeep testcases_output testcases_expected_output/



