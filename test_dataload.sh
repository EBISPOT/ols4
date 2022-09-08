#!/usr/bin/env bash

TEST_CONFIGS=$(find testcases | grep json)

rm -rf testcases_output/*
mkdir testcases_output

for f in $TEST_CONFIGS
do

TEST_FOLDER=$(basename $(dirname $f))
mkdir ./testcases_output/$TEST_FOLDER

./dataload/create_datafiles.sh $f ./testcases_output/$TEST_FOLDER --loadLocalFiles --noDates
done

diff --recursive --exclude=.gitkeep testcases_output testcases_expected_output/




