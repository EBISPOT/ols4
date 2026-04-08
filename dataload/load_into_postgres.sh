#!/usr/bin/env bash
set -Eeuo pipefail

if [ $# -lt 2 ]; then
    echo "Usage: $0 <output_dir> <tsvdir> [--filter-property <name> ...] [parquet_file ...]"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

OUTPUT_DIR=$1
TSV_DIR=$2
shift 2

# Parse remaining args: --filter-property flags and parquet files
FILTER_PROPERTIES=()
EMBEDDING_PARQUETS=()
SCHEMA_EXTRA_ARGS=()
while [ $# -gt 0 ]; do
    case "$1" in
        --filter-property)
            FILTER_PROPERTIES+=("$2")
            SCHEMA_EXTRA_ARGS+=("--filter-property" "$2")
            shift 2
            ;;
        *.parquet)
            EMBEDDING_PARQUETS+=("$1")
            SCHEMA_EXTRA_ARGS+=("$1")
            shift
            ;;
        *)
            shift
            ;;
    esac
done

# Auto-detect PostgreSQL binaries
if command -v initdb &>/dev/null; then
    PGBIN=$(dirname "$(command -v initdb)")
elif [ -x /usr/lib/postgresql/17/bin/initdb ]; then
    PGBIN=/usr/lib/postgresql/17/bin
else
    echo "ERROR: Cannot find PostgreSQL binaries"
    exit 1
fi

PG_DATA="$OUTPUT_DIR/data"
PG_PORT=5432
PG_USER="postgres"
PG_DB="ols4"

export PGDATA="$PG_DATA"
export PGPORT="$PG_PORT"
export PGUSER="$PG_USER"
export PGDATABASE="$PG_DB"
export PGHOST="/tmp"

# Clean any existing data
rm -rf "$PG_DATA"

echo "=== Initializing PostgreSQL ==="
"$PGBIN/initdb" -D "$PG_DATA" --auth=trust --username="$PG_USER" --no-locale --encoding=UTF8

# Allow network connections (needed for Docker containers)
echo "host all all 0.0.0.0/0 trust" >> "$PG_DATA/pg_hba.conf"

# Configure for bulk import (fast loading, not durability)
cat >> "$PG_DATA/postgresql.conf" <<EOF
listen_addresses = '*'
port = $PG_PORT
unix_socket_directories = '/tmp'
max_connections = 10
shared_buffers = 256MB
work_mem = 256MB
maintenance_work_mem = 1GB
wal_level = minimal
max_wal_senders = 0
fsync = off
synchronous_commit = off
full_page_writes = off
autovacuum = off
max_wal_size = 4GB
checkpoint_completion_target = 0.9
EOF

echo "=== Starting PostgreSQL ==="
"$PGBIN/pg_ctl" -D "$PG_DATA" -l "$PG_DATA/logfile" start -w

echo "=== Creating database ==="
"$PGBIN/createdb" "$PG_DB" || true

echo "=== Creating schema and loading data ==="

# Generate schema SQL (includes tables, embedding columns, indexes, SET LOGGED, ANALYZE)
SCHEMA_SQL=$(python3 "$SCRIPT_DIR/create_postgres_schema.py" ${SCHEMA_EXTRA_ARGS[@]+"${SCHEMA_EXTRA_ARGS[@]}"})

# Execute only table creation and embedding columns (everything before the indexes)
# We split: create tables + add columns first, then COPY, then indexes + LOGGED + ANALYZE
TABLE_SQL=$(echo "$SCHEMA_SQL" | sed -n '1,/^-- Entity lookup indexes/p' | head -n -1)
INDEX_SQL=$(echo "$SCHEMA_SQL" | sed -n '/^-- Entity lookup indexes/,$p')

echo "$TABLE_SQL" | "$PGBIN/psql" -v ON_ERROR_STOP=1

echo "=== Bulk loading TSV files ==="

ls -lh "$TSV_DIR"/*.tsv 2>/dev/null || true

# Base columns: id, type, iri, ontology_id, _json, is_obsolete, label, direct_ancestors, hierarchical_ancestors,
# search_type, short_form, curie, obo_id, synonym, definition, is_defining_ontology,
# has_direct_parents, has_hierarchical_parents, has_direct_children, has_hierarchical_children, is_preferred_root,
# ontology_iri, ontology_preferred_prefix, subset, related_to, curated_from_sources
# Then one filter_* column per filter property, then one embeddings_* column per model
BASE_ENTITY_COLS="id, type, iri, ontology_id, _json, is_obsolete, label, direct_ancestors, hierarchical_ancestors, search_type, short_form, curie, obo_id, synonym, definition, is_defining_ontology, has_direct_parents, has_hierarchical_parents, has_direct_children, has_hierarchical_children, is_preferred_root, ontology_iri, ontology_preferred_prefix, subset, related_to, curated_from_sources"
EDGE_COLS="start_id, end_id, type, _json, property"
EMB_NODE_BASE_COLS="id, type, entity_id"

# Build filter property column list for entities
FILTER_COLS=""
for prop in "${FILTER_PROPERTIES[@]}"; do
    FILTER_COLS="${FILTER_COLS}, \"filter_${prop}\""
done

# Build embedding column list for entities and embedding_nodes
ENTITY_EMB_COLS=""
EMB_NODE_EMB_COLS=""
for parquet in "${EMBEDDING_PARQUETS[@]}"; do
    model_name=$(basename "$parquet" .parquet)
    ENTITY_EMB_COLS="${ENTITY_EMB_COLS}, \"embeddings_${model_name}\""
    EMB_NODE_EMB_COLS="${EMB_NODE_EMB_COLS}, \"embedding_${model_name}\""
done

ENTITY_COLS="$BASE_ENTITY_COLS$FILTER_COLS$ENTITY_EMB_COLS"
EMB_NODE_COLS="$EMB_NODE_BASE_COLS$EMB_NODE_EMB_COLS"

# COPY entities
ENTITY_FILES=$(find "$TSV_DIR" -name '*_entities.tsv' -size +0c | sort)
if [ -n "$ENTITY_FILES" ]; then
    echo "Loading entities..."
    for f in $ENTITY_FILES; do
        echo "  COPY from $f"
        "$PGBIN/psql" -v ON_ERROR_STOP=1 -c "\\COPY ols_entities ($ENTITY_COLS) FROM '$f' WITH (FORMAT text)"
    done
fi

# COPY edges
EDGE_FILES=$(find "$TSV_DIR" -name '*_edges.tsv' -size +0c | sort)
if [ -n "$EDGE_FILES" ]; then
    echo "Loading edges..."
    for f in $EDGE_FILES; do
        echo "  COPY from $f"
        "$PGBIN/psql" -v ON_ERROR_STOP=1 -c "\\COPY ols_edges ($EDGE_COLS) FROM '$f' WITH (FORMAT text)"
    done
fi

# COPY embedding nodes
EMB_FILES=$(find "$TSV_DIR" -name '*_embedding_nodes.tsv' -size +0c | sort)
if [ -n "$EMB_FILES" ]; then
    echo "Loading embedding nodes..."
    for f in $EMB_FILES; do
        echo "  COPY from $f"
        "$PGBIN/psql" -v ON_ERROR_STOP=1 -c "\\COPY ols_embedding_nodes ($EMB_NODE_COLS) FROM '$f' WITH (FORMAT text)"
    done
fi

echo "=== Creating indexes and finalizing ==="
echo "$INDEX_SQL" | "$PGBIN/psql" -v ON_ERROR_STOP=1

echo "=== Stopping PostgreSQL ==="
"$PGBIN/pg_ctl" -D "$PG_DATA" stop -w

# Update postgresql.conf for production
cat > "$PG_DATA/postgresql.auto.conf" <<EOF
wal_level = 'replica'
max_wal_senders = 10
fsync = on
synchronous_commit = on
full_page_writes = on
autovacuum = on
shared_buffers = 256MB
EOF

echo "=== Packaging PostgreSQL data ==="
tar -czf "$(dirname "$TSV_DIR")/postgres.tgz" -C "$OUTPUT_DIR" data

echo "=== Done ==="
