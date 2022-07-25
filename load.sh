#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <configurl> <jsonpath>"
    exit 1
fi

CONFIG_URL=$1

JSON_PATH=$2
JSON_FLATTENED_PATH=$(echo "$JSON_PATH" | sed 's/.json$/_flat.json/g')


mvn clean package

rm -f out/*

echo owl2json
java -jar owl2json/target/owl2json-1.0-SNAPSHOT.jar --config "$CONFIG_URL" --output "$JSON_PATH"

echo json2flattened
java -jar json2flattened/target/json2flattened-1.0-SNAPSHOT.jar --input "$JSON_PATH" --output "$JSON_FLATTENED_PATH"

echo json2neo
java -jar json2neo/target/json2neo-1.0-SNAPSHOT.jar --input "$JSON_FLATTENED_PATH" --outDir out

echo json2solr
java -jar json2solr/target/json2solr-1.0-SNAPSHOT.jar --input "$JSON_FLATTENED_PATH" --outDir out


