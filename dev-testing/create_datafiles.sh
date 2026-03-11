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
$SCRIPT_PATH/../dataload/target/release/ols_create_manifest \
    --input "$JSON_PATH" --output "$LINKER_MANIFEST_PATH"

echo linker: link
SSSOM_FILES=$(find $SCRIPT_PATH/../testcases/curations -name '*.sssom.tsv' 2>/dev/null | tr '\n' ' ')
$SCRIPT_PATH/../dataload/target/release/ols_link \
    --manifest "$LINKER_MANIFEST_PATH" --input "$JSON_PATH" --output "$JSON_PATH_LINKED" \
    ${SSSOM_FILES:+--sssom $SSSOM_FILES}

echo json2neo
EMBEDDING_PARQUETS=$(find $SCRIPT_PATH/../testcases/embeddings -name '*.parquet' 2>/dev/null | tr '\n' ' ')
$SCRIPT_PATH/../dataload/target/release/ols_json2neo \
    --manifest "$LINKER_MANIFEST_PATH" --input "$JSON_PATH_LINKED" --outDir $OUTDIR \
    ${EMBEDDING_PARQUETS:+--embeddingParquets $EMBEDDING_PARQUETS}

echo json2solr
$SCRIPT_PATH/../dataload/target/release/ols_json2solr \
    --input "$JSON_PATH_LINKED" --outDir $OUTDIR


