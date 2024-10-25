#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <neo4jpath> <csvdir>"
    exit 1
fi

SCRIPT_PATH=$(dirname $(readlink -f $0))

rm -rf $1/data/databases/neo4j
rm -rf $1/data/transactions/neo4j

$1/bin/neo4j start
sleep 20
echo csv2neo
java -jar $SCRIPT_PATH/csv2neo/target/csv2neo-1.0-SNAPSHOT.jar -m i -d $2 -bs 1000 -ps 20 -t 5



