#!/usr/bin/env bash
#
# Populate an existing (managed) PostgreSQL database with OLS4 data.
#
# Unlike load_into_postgres.sh, this script does NOT create or manage a local
# PostgreSQL instance. It connects to an external database using standard libpq
# environment variables and loads data via client-side \COPY.
#
# Required env vars:
#   PGHOST      - database hostname (e.g. pgsql-hlvm-139)
#   PGDATABASE  - database name     (e.g. spotolsexp)
#   PGUSER      - database user     (e.g. spot)
#
# Optional env vars:
#   PGPASSWORD  - database password (omit for trust/cert auth)
#   PGPORT      - database port     (default: 5432)
#   PGSSLMODE   - SSL mode          (e.g. require)
#
# Prerequisites on the managed database:
#   - The database must already exist
#   - Extensions pgvector and pg_trgm must be available (CREATE EXTENSION IF NOT EXISTS is attempted)
#
# Usage: $0 <tsvdir> [--filter-property <name> ...] [parquet_file ...]
#
set -Eeuo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <tsvdir> [--filter-property <name> ...] [parquet_file ...]"
    echo ""
    echo "Environment variables: PGHOST, PGDATABASE, PGUSER, PGPASSWORD (optional), PGPORT (default 5432)"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

TSV_DIR=$1
shift

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

# Validate required env vars
: "${PGHOST:?PGHOST must be set (e.g. pgsql-hlvm-139)}"
: "${PGDATABASE:?PGDATABASE must be set (e.g. spotolsexp)}"
: "${PGUSER:?PGUSER must be set (e.g. spot)}"
export PGPORT="${PGPORT:-5432}"

echo "=== Connecting to ${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE} ==="

# Validate connectivity
if ! psql -c "SELECT 1" >/dev/null 2>&1; then
    echo "ERROR: Cannot connect to PostgreSQL at ${PGHOST}:${PGPORT}/${PGDATABASE} as ${PGUSER}"
    echo "Check PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD and network connectivity."
    exit 1
fi
echo "Connection OK."

echo "=== Dropping existing OLS tables ==="
psql -v ON_ERROR_STOP=1 <<'EOF'
DROP TABLE IF EXISTS ols_embedding_nodes CASCADE;
DROP TABLE IF EXISTS ols_edges CASCADE;
DROP TABLE IF EXISTS ols_entities CASCADE;
EOF

echo "=== Creating schema ==="

# Generate schema SQL (includes tables, embedding columns, indexes, ANALYZE)
SCHEMA_SQL=$(python3 "$SCRIPT_DIR/create_postgres_schema.py" ${SCHEMA_EXTRA_ARGS[@]+"${SCHEMA_EXTRA_ARGS[@]}"})

# Split: create tables + add columns first, then COPY, then indexes + ANALYZE
TABLE_SQL=$(echo "$SCHEMA_SQL" | sed -n '1,/^-- Entity lookup indexes/p' | head -n -1)
INDEX_SQL=$(echo "$SCHEMA_SQL" | sed -n '/^-- Entity lookup indexes/,$p')

echo "$TABLE_SQL" | psql -v ON_ERROR_STOP=1

echo "=== Bulk loading binary COPY files ==="

ls -lh "$TSV_DIR"/*.pgbin 2>/dev/null || true

# Column lists (must match create_postgres_schema.py column order)
BASE_ENTITY_COLS="id, type, iri, ontology_id, _json, is_obsolete, label, direct_ancestors, hierarchical_ancestors, search_type, short_form, curie, obo_id, synonym, definition, is_defining_ontology, has_direct_parents, has_hierarchical_parents, has_direct_children, has_hierarchical_children, is_preferred_root, ontology_iri, ontology_preferred_prefix, subset, related_to, curated_from_sources"
EDGE_COLS="start_id, end_id, type, _json, property"
EMB_NODE_BASE_COLS="id, type, entity_id"

# Build filter property column list for entities
FILTER_COLS=""
for prop in "${FILTER_PROPERTIES[@]}"; do
    FILTER_COLS="${FILTER_COLS}, \"filter_${prop}\""
done

# Sort embedding parquets to match json2postgres column order (alphabetical)
IFS=$'\n' EMBEDDING_PARQUETS=($(sort <<<"${EMBEDDING_PARQUETS[*]}")); unset IFS

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

# Number of parallel COPY streams
PARALLEL_JOBS="${PARALLEL_JOBS:-4}"

copy_binary_file() {
    local table=$1 cols=$2 f=$3
    local size_mb=$(( $(stat -L -c "%s" "$f" 2>/dev/null || stat -f "%z" "$f" 2>/dev/null || echo 0) / 1048576 ))
    echo "  COPY ${table} from $(basename "$f") (${size_mb}MB)"
    psql -v ON_ERROR_STOP=1 -c "COPY ${table} (${cols}) FROM STDIN WITH (FORMAT binary)" < "$f"
}
export -f copy_binary_file
export PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD

# COPY entities (parallel)
ENTITY_FILES=$(find "$TSV_DIR" -maxdepth 1 -name '*_entities.pgbin' -size +0c | sort)
if [ -n "$ENTITY_FILES" ]; then
    echo "Loading entities (${PARALLEL_JOBS} parallel streams)..."
    echo "$ENTITY_FILES" | xargs -P "$PARALLEL_JOBS" -I{} bash -c "copy_binary_file ols_entities '$ENTITY_COLS' '{}'"
fi

# COPY edges (parallel)
EDGE_FILES=$(find "$TSV_DIR" -maxdepth 1 -name '*_edges.pgbin' -size +0c | sort)
if [ -n "$EDGE_FILES" ]; then
    echo "Loading edges (${PARALLEL_JOBS} parallel streams)..."
    echo "$EDGE_FILES" | xargs -P "$PARALLEL_JOBS" -I{} bash -c "copy_binary_file ols_edges '$EDGE_COLS' '{}'"
fi

# COPY embedding nodes (parallel)
EMB_FILES=$(find "$TSV_DIR" -maxdepth 1 -name '*_embedding_nodes.pgbin' -size +0c | sort)
if [ -n "$EMB_FILES" ]; then
    echo "Loading embedding nodes (${PARALLEL_JOBS} parallel streams)..."
    echo "$EMB_FILES" | xargs -P "$PARALLEL_JOBS" -I{} bash -c "copy_binary_file ols_embedding_nodes '$EMB_NODE_COLS' '{}'"
fi

echo "=== Creating indexes and finalizing ==="
echo "$INDEX_SQL" | psql -v ON_ERROR_STOP=1

echo "=== Done ==="
echo "Database ${PGDATABASE} on ${PGHOST} has been populated."
echo "To connect the backend, set:"
echo "  OLS_POSTGRES_HOST=${PGHOST}"
echo "  OLS_POSTGRES_PORT=${PGPORT}"
echo "  OLS_POSTGRES_DB=${PGDATABASE}"
echo "  OLS_POSTGRES_USER=${PGUSER}"
echo "  OLS_POSTGRES_PASSWORD=<password>"
