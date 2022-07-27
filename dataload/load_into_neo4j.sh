#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <neo4jpath> <csvdir>"
    exit 1
fi

rm -rf $1/data/databases/neo4j
rm -rf $1/data/transactions/neo4j

$1/bin/neo4j-admin import \
        --ignore-empty-strings=true \
        --legacy-style-quoting=false \
        --multiline-fields=true \
        --array-delimiter="|" \
        --database=neo4j \
        --processors=16 \
        $(./make_csv_import_cmd.sh $2)


