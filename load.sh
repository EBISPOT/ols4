#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <configurl> <jsonpath>"
    exit 1
fi

CONFIG_URL=$1
JSONPATH=$2

mvn clean package

rm -f out/*

java -jar owl2json/target/owl2json-1.0-SNAPSHOT.jar --config "$CONFIG_URL" --output "$JSONPATH"
java -jar json2csv/target/json2csv-1.0-SNAPSHOT.jar --input "$JSONPATH" --outDir out


