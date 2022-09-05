#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <neo4jpath>"
    exit 1
fi

$1/bin/neo4j start
sleep 20

echo Creating neo4j indexes...

$1/bin/cypher-shell -a neo4j://127.0.0.1:7687 --non-interactive -f create_indexes.cypher

echo Creating neo4j indexes done

sleep 5

$1/bin/neo4j stop


