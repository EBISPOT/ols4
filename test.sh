#!/usr/bin/env bash

TEST_CONFIGS=$(find testcases | grep json)

rm -rf testcases_output && mkdir testcases_output

for f in $TEST_CONFIGS
do
./dataload/create_datafiles.sh $f ./testcases_output --loadLocalFiles
done

diff --brief --recursive testcases_output testcases_expected_output/




