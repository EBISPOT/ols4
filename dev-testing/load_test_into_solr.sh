#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <csvdir>"
    exit 1
fi

wget --method POST --no-proxy -O - --server-response --content-on-error=on --header="Content-Type: application/json" --body-file $1/ontologies.jsonl \
    http://localhost:8983/solr/ols4_entities/update/json/docs?commit=true

wget --method POST --no-proxy -O - --server-response --content-on-error=on --header="Content-Type: application/json" --body-file $1./output/ontologies.jsonl \
    http://localhost:8983/solr/ols4_entities/update/json/docs?commit=true

wget --method POST --no-proxy -O - --server-response --content-on-error=on --header="Content-Type: application/json" --body-file $1/classes.jsonl \
    http://localhost:8983/solr/ols4_entities/update/json/docs?commit=true

wget --method POST --no-proxy -O - --server-response --content-on-error=on --header="Content-Type: application/json" --body-file $1/properties.jsonl \
    http://localhost:8983/solr/ols4_entities/update/json/docs?commit=true

wget --method POST --no-proxy -O - --server-response --content-on-error=on --header="Content-Type: application/json" --body-file $1/individuals.jsonl \
    http://localhost:8983/solr/ols4_entities/update/json/docs?commit=true

wget --method POST --no-proxy -O - --server-response --content-on-error=on --header="Content-Type: application/json" --body-file $1/autocomplete.jsonl \
    http://localhost:8983/solr/ols4_autocomplete/update/json/docs?commit=true

sleep 5
#
#wget http://localhost:8983/solr/ols4_entities/update?commit=true
#
#wget http://localhost:8983/solr/ols4_autocomplete/update?commit=true
#
#sleep 5
#
#$1/bin/solr stop




