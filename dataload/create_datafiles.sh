#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <configurl> <outdir> [--loadLocalFiles]"
    exit 1
fi

SCRIPT_PATH=$(dirname $(readlink -f $0))

CONFIG_URL=$1
OUTDIR=$2

JSON_PATH=$OUTDIR/ontologies.json
JSON_FLATTENED_PATH=$OUTDIR/ontologies_flat.json

rm -f $OUTDIR/*

echo owl2json
java -jar $SCRIPT_PATH/owl2json/target/owl2json-1.0-SNAPSHOT.jar --config "$CONFIG_URL" --output "$JSON_PATH" $3

echo json2flattened
java -jar $SCRIPT_PATH/json2flattened/target/json2flattened-1.0-SNAPSHOT.jar --input "$JSON_PATH" --output "$JSON_FLATTENED_PATH"

echo json2neo
java -jar $SCRIPT_PATH/json2neo/target/json2neo-1.0-SNAPSHOT.jar --input "$JSON_FLATTENED_PATH" --outDir $OUTDIR

echo json2solr
java -jar $SCRIPT_PATH/json2solr/target/json2solr-1.0-SNAPSHOT.jar --input "$JSON_FLATTENED_PATH" --outDir $OUTDIR


