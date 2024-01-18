#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <csvdir>"
    exit 1
fi

$NEO4J_HOME/bin/neo4j-admin import \
        --ignore-empty-strings=true \
        --legacy-style-quoting=false \
        --multiline-fields=true \
        --array-delimiter="|" \
        --database=neo4j \
        --processors=2 \
        --read-buffer-size=134217728 \
        $($OLS4_HOME/dev-testing/make_csv_import_cmd.sh $1)