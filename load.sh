#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <configurl> <jsonpath>"
    exit 1
fi

CONFIG_URL=$1
JSONPATH=$2

mvn clean package

rm -f out/*

#java -jar owl2json/target/owl2json-1.0-SNAPSHOT.jar --config "$CONFIG_URL" --output "$JSONPATH"
#java -jar json2neo/target/json2neo-1.0-SNAPSHOT.jar --input "$JSONPATH" --outDir out
java -jar json2solr/target/json2solr-1.0-SNAPSHOT.jar --input "$JSONPATH" --outDir out


