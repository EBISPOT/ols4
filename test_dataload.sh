#!/usr/bin/env bash

TEST_CONFIGS=$(find testcases | grep json)

rm -rf testcases_output/*
mkdir testcases_output

for f in $TEST_CONFIGS
do

BASENAME=$(basename $f .json)

# Compute the relative directory path from "testcases/" to the parent of $f,
# preserving nested structure (e.g. testcases/datatypes/nesting-issue/foo.json
# → datatypes/nesting-issue).  For files directly inside testcases/ (e.g.
# testcases/duo.json) the resulting REL_DIR is empty and TEST_FOLDER is just
# the basename, so output lands in testcases_output/duo/ rather than the
# incorrect testcases_output/testcases/duo/.
REL_DIR=$(dirname $f | sed 's|^testcases/*||')

if [ -z "$REL_DIR" ]; then
    TEST_FOLDER=$BASENAME
else
    TEST_FOLDER=$REL_DIR/$BASENAME
fi

mkdir -p ./testcases_output/$TEST_FOLDER

./dev-testing/create_datafiles.sh $f ./testcases_output/$TEST_FOLDER --loadLocalFiles --noDates
done

diff --recursive --exclude=.gitkeep testcases_output testcases_expected_output/




