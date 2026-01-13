#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <configurl> <outdir>"
    exit 1
fi

SCRIPT_PATH=$(dirname $(readlink -f $0))

CONFIG_URL=$1
OUTDIR=$2

JSON_PATH=$OUTDIR/ontologies.json
LINKER_MANIFEST_PATH=$OUTDIR/linker_manifest.json
JSON_PATH_LINKED=$OUTDIR/ontologies_linked.json

rm -f $OUTDIR/*

echo JAVA_OPTS=$JAVA_OPTS

echo rdf2json
java $JAVA_OPTS -DentityExpansionLimit=0 -DtotalEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -Djdk.xml.entityExpansionLimit=0 \
    -jar $SCRIPT_PATH/../dataload/rdf2json/target/rdf2json-1.0-SNAPSHOT.jar --config "$CONFIG_URL" --output "$JSON_PATH" "${@:3}"

echo linker: create manifest
java -jar $SCRIPT_PATH/../dataload/linker/create_manifest/target/create-manifest-1.0-SNAPSHOT.jar \
    --input "$JSON_PATH" --output "$LINKER_MANIFEST_PATH"

echo linker: link
java -jar $SCRIPT_PATH/../dataload/linker/link/target/link-1.0-SNAPSHOT.jar \
    --manifest "$LINKER_MANIFEST_PATH" --input "$JSON_PATH" --output "$JSON_PATH_LINKED"

echo json2neo
java -jar $SCRIPT_PATH/../dataload/json2neo/target/json2neo-1.0-SNAPSHOT.jar \
    --manifest "$LINKER_MANIFEST_PATH" --input "$JSON_PATH_LINKED" --outDir $OUTDIR

echo json2solr
java -jar $SCRIPT_PATH/../dataload/json2solr/target/json2solr-1.0-SNAPSHOT.jar \
    --input "$JSON_PATH_LINKED" --outDir $OUTDIR


