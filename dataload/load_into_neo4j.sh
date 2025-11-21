#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <neo4jpath> <csvdir> <mem>"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export NEO_MEM=$3
export HEAP_SIZE=$NEO_MEM
export JAVA_OPTS="--add-modules jdk.incubator.vector --add-opens=java.base/java.nio=ALL-UNNAMED -Xms$NEO_MEM -Xmx$NEO_MEM"
export NEO4J_dbms_memory_transaction_total_max=0
export NEO4J_dbms_memory_transaction_max=0

rm -rf $1/data/databases/neo4j
rm -rf $1/data/transactions/neo4j

echo $1/bin/neo4j-admin database import full \
        --ignore-empty-strings=true \
        --legacy-style-quoting=false \
        --multiline-fields=true \
        --read-buffer-size=256m \
        --array-delimiter="|" \
        --max-off-heap-memory=$NEO_MEM \
        --verbose \
        $($SCRIPT_DIR/make_csv_import_cmd.sh $2)


