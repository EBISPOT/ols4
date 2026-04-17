#!/usr/bin/env bash
# dataload-ci.sh — Sequential dataload for CI environment (GitHub Actions)
# Bypasses Nextflow to fit in 7GB RAM by running tasks one-by-one on the host.

set -Eeuo pipefail

# ─── Colours ──────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[dataload-ci]${NC} $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

# ─── Paths ────────────────────────────────────────────────────────────────────
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
OLS4_HOME=$(cd "$SCRIPT_DIR/.." && pwd)

DATALOAD="$OLS4_HOME/dataload"
RDF2JSON_JAR="$DATALOAD/rdf2json/target/rdf2json-1.0-SNAPSHOT.jar"
SOLR_CFG_BUILDER_JAR="$DATALOAD/solr_config_builder/target/solr_config_builder-1.0-SNAPSHOT.jar"
SOLR_CFG_TEMPLATE="$DATALOAD/solr_config_template"
MERGE_CONFIGS_JAR="$DATALOAD/merge_configs/target/merge_configs-1.0-SNAPSHOT.jar"
RUST_BINS="$DATALOAD/target/release"
TEXT_TAGGER_BIN="$OLS4_HOME/text_tagger/target/release/ols_text_tagger"

OUT_DIR="$OLS4_HOME/out"
TMP_DIR="$OLS4_HOME/tmp"
mkdir -p "$OUT_DIR" "$TMP_DIR"

COMBINED_CONFIG="$TMP_DIR/combined_config.json"
ONTOLOGIES_JSON="$TMP_DIR/ontologies.json"
LINKER_MANIFEST="$TMP_DIR/linker_manifest.json"
ONTOLOGIES_LINKED="$TMP_DIR/ontologies_linked.json"
NEO_CSVS="$TMP_DIR/neo-csvs"
SOLR_DATA="$TMP_DIR/solr-data"
SOLR_HOME_DIR="$TMP_DIR/solr-home"

mkdir -p "$NEO_CSVS" "$SOLR_DATA" "$SOLR_HOME_DIR"

# ─── 1. Merge all testcase configs ─────────────────────────────────────────
log "Merging all testcase configs..."
# OLS4_CONFIG should already be set by test_api.sh
if [ -z "${OLS4_CONFIG:-}" ]; then
    OLS4_CONFIG=$(find "$OLS4_HOME/testcases" -name "*.json" | sort | paste -sd, -)
fi

java -jar "$MERGE_CONFIGS_JAR" \
    --config "$OLS4_CONFIG" \
    --output "$COMBINED_CONFIG"

# ─── 2. Run data pipeline ──────────────────────────────────────────────────
log "Running rdf2json..."
(cd "$OLS4_HOME" && java -Xmx4g -jar "$RDF2JSON_JAR" \
    --config "$COMBINED_CONFIG" \
    --output "$ONTOLOGIES_JSON" \
    --loadLocalFiles --noDates)

log "Running linker manifest..."
"$RUST_BINS/ols_create_manifest" --input "$ONTOLOGIES_JSON" --output "$LINKER_MANIFEST"

log "Running linker..."
# Collect SSSOM curations
SSSOM_OPTS=()
SSSOM_FILES=$(find "$OLS4_HOME/testcases/curations" -name "*.sssom.tsv" 2>/dev/null || true)
if [ -n "$SSSOM_FILES" ]; then
    SSSOM_OPTS=(--sssom $SSSOM_FILES)
fi

"$RUST_BINS/ols_link" \
    --manifest "$LINKER_MANIFEST" \
    --input    "$ONTOLOGIES_JSON" \
    --output   "$ONTOLOGIES_LINKED" \
    "${SSSOM_OPTS[@]}"

log "Running json2neo..."
"$RUST_BINS/ols_json2neo" \
    --manifest "$LINKER_MANIFEST" \
    --input    "$ONTOLOGIES_LINKED" \
    --outDir   "$NEO_CSVS"

log "Running json2solr..."
"$RUST_BINS/ols_json2solr" \
    --input  "$ONTOLOGIES_LINKED" \
    --outDir "$SOLR_DATA"

log "Building text tagger database..."
"$RUST_BINS/extract_strings_from_terms" "$ONTOLOGIES_LINKED" > "$TMP_DIR/terms.tsv"
"$TEXT_TAGGER_BIN" build --output "$TMP_DIR/text_tagger_db.bin" < "$TMP_DIR/terms.tsv"
# Move to expected location for backend
mkdir -p "$OLS4_HOME/testcases_api_pipeline_out"
cp "$TMP_DIR/text_tagger_db.bin" "$OLS4_HOME/testcases_api_pipeline_out/text_tagger_db.bin"

# ─── 3. Set up Solr ─────────────────────────────
log "Building Solr config..."
java -jar "$SOLR_CFG_BUILDER_JAR" \
    --manifestPath           "$LINKER_MANIFEST" \
    --solrConfigTemplatePath "$SOLR_CFG_TEMPLATE" \
    --outDir                 "$SOLR_HOME_DIR"

log "Preparing final Solr directory..."
mkdir -p "$OUT_DIR/solr"
cp -r "$SOLR_HOME_DIR"/* "$OUT_DIR/solr/"

# ─── 4. Preparing Neo4j ───────────────────────────────────────────────────
log "Preparing Neo4j data (neo4j-admin import)..."
mkdir -p "$OUT_DIR/neo4j/data"

# The container has neo4j installed at /opt/neo4j
# We can use the existing load_into_neo4j.sh script
/opt/ols/dataload/load_into_neo4j.sh /opt/neo4j "$NEO_CSVS" 4g

# Move the imported data to our mapped output directory
cp -r /opt/neo4j/data/databases "$OUT_DIR/neo4j/data/"
cp -r /opt/neo4j/data/transactions "$OUT_DIR/neo4j/data/"

log "Dataload CI preparation complete."
log "JSONL files are in $SOLR_DATA and will be indexed after Solr starts in docker-compose."
