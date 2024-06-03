#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <solrpath> <csvdir>"
    exit 1
fi

$1/bin/solr start -force -Djetty.host=127.0.0.1
sleep 10

wget --method POST --no-proxy -O - --server-response --content-on-error=on --header="Content-Type: application/json" --body-file $2/ontologies.jsonl \
    http://127.0.0.1:8983/solr/ols4_entities/update/json/docs?commit=true

wget --method POST --no-proxy -O - --server-response --content-on-error=on --header="Content-Type: application/json" --body-file $2/classes.jsonl \
    http://127.0.0.1:8983/solr/ols4_entities/update/json/docs?commit=true

wget --method POST --no-proxy -O - --server-response --content-on-error=on --header="Content-Type: application/json" --body-file $2/properties.jsonl \
    http://127.0.0.1:8983/solr/ols4_entities/update/json/docs?commit=true

wget --method POST --no-proxy -O - --server-response --content-on-error=on --header="Content-Type: application/json" --body-file $2/individuals.jsonl \
    http://127.0.0.1:8983/solr/ols4_entities/update/json/docs?commit=true

wget --method POST --no-proxy -O - --server-response --content-on-error=on --header="Content-Type: application/json" --body-file $2/autocomplete.jsonl \
    http://127.0.0.1:8983/solr/ols4_autocomplete/update/json/docs?commit=true

sleep 5

wget --no-proxy http://127.0.0.1:8983/solr/ols4_entities/update?commit=true

sleep 5

wget --no-proxy http://127.0.0.1:8983/solr/ols4_autocomplete/update?commit=true

sleep 5

$1/bin/solr stop




