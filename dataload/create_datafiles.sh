#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <configurl> <outdir> <embeddingsdir> [--loadLocalFiles]"
    exit 1
fi

SCRIPT_PATH=$(dirname $(readlink -f $0))

CONFIG_URL=$1
OUTDIR=$2
EMBEDDINGSDIR=$3

JSON_PATH=$OUTDIR/ontologies.json
JSON_PATH_LINKED=$OUTDIR/ontologies_linked.json
EMBEDDINGS_PATH=$EMBEDDINGSDIR/embeddings.db

rm -f $OUTDIR/*

echo JAVA_OPTS=$JAVA_OPTS

echo rdf2json
java $JAVA_OPTS -DentityExpansionLimit=0 -DtotalEntitySizeLimit=0 -Djdk.xml.totalEntitySizeLimit=0 -Djdk.xml.entityExpansionLimit=0 -jar $SCRIPT_PATH/rdf2json/target/rdf2json-1.0-SNAPSHOT.jar --config "$CONFIG_URL" --output "$JSON_PATH" "${@:3}"

if [ -n "$OPENAI_API_KEY" ]; then
    echo "Found OpenAI API key; embeddings will be created/updated."

    $SCRIPT_PATH/embeddings/target/release/ols_embed \
        --db-path $EMBEDDINGSDIR/embeddings.db \
        --input-file $JSON_PATH 
else
    echo "OPENAI_API_KEY not set; embeddings will not be created/updated"
fi

LINKER_EMBED_OPTS=""
if [ -f "$EMBEDDINGS_PATH" ]; then
    echo "Embeddings were found and will be passed to the linker"
    LINKER_EMBED_OPTS="--embeddingsDb $EMBEDDINGS_PATH"
else
    echo "No embeddings found to pass to linker"
fi

echo linker
java -jar $SCRIPT_PATH/linker/target/linker-1.0-SNAPSHOT.jar --input "$JSON_PATH" --output "$JSON_PATH_LINKED" $LINKER_EMBED_OPTS

echo json2neo
java -jar $SCRIPT_PATH/json2neo/target/json2neo-1.0-SNAPSHOT.jar --input "$JSON_PATH_LINKED" --outDir $OUTDIR

echo json2solr
java -jar $SCRIPT_PATH/json2solr/target/json2solr-1.0-SNAPSHOT.jar --input "$JSON_PATH_LINKED" --outDir $OUTDIR


