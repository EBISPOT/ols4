#!/usr/bin/env bash

if [ $# -lt 3 ]; then
    echo "Usage: $0 <neo4jpath> <csvdir> <mem> [parquet_file ...]"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

NEO4J_PATH=$1
CSV_DIR=$2
NEO_MEM=$3
shift 3
EMBEDDING_PARQUETS=("$@")

export NEO_MEM
export HEAP_SIZE=$NEO_MEM
export JAVA_OPTS="--add-modules jdk.incubator.vector --add-opens=java.base/java.nio=ALL-UNNAMED -Xms$NEO_MEM -Xmx$NEO_MEM"
export NEO4J_dbms_memory_transaction_total_max=0
export NEO4J_dbms_memory_transaction_max=0

rm -rf $NEO4J_PATH/data/databases/neo4j
rm -rf $NEO4J_PATH/data/transactions/neo4j

ls -Lhl $CSV_DIR

$NEO4J_PATH/bin/neo4j-admin database import full \
        --ignore-empty-strings=true \
        --legacy-style-quoting=false \
        --multiline-fields=true \
        --read-buffer-size=256m \
        --array-delimiter="|" \
        --max-off-heap-memory=$NEO_MEM \
        --verbose \
        $($SCRIPT_DIR/make_csv_import_cmd.sh $CSV_DIR)

# Create indexes after import
echo "Creating Neo4j indexes..."

# Generate index creation script (includes standard indexes + dynamic embedding indexes)
CYPHER_SCRIPT=$(mktemp)
python3 "$SCRIPT_DIR/create_neo4j_indexes.py" "${EMBEDDING_PARQUETS[@]}" > "$CYPHER_SCRIPT"

cat "$CYPHER_SCRIPT"

# Start Neo4j, create indexes, then stop
export NEO4J_AUTH=none
$NEO4J_PATH/bin/neo4j start --verbose
sleep 20

$NEO4J_PATH/bin/cypher-shell -a neo4j://127.0.0.1:7687 --non-interactive -f "$CYPHER_SCRIPT"

echo "Creating Neo4j indexes done"
sleep 5

$NEO4J_PATH/bin/neo4j stop

rm -f "$CYPHER_SCRIPT"


