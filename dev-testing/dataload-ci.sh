#!/usr/bin/env bash
# dataload-ci.sh — Sequential dataload for CI (GitHub Actions).
#
# Runs INSIDE the ols4-dataload Docker container, which provides:
#   - Java 21 (Amazon Corretto), Maven 3.9.9, Rust 1.90.0 (pre-built binaries)
#   - /opt/neo4j  (Neo4j 2025.03.0 — same version as docker-compose)
#   - /opt/solr   (Solr 9.8.1      — same version as docker-compose)
#
# Invoked by test_api.sh via:
#   docker run --rm -v ./out:/opt/ols/out -v ./tmp:/opt/ols/tmp ols4-dataload:local \
#       bash -c "cd /opt/ols && ./dev-testing/dataload-ci.sh"

set -Eeuo pipefail

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
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

mkdir -p "$NEO_CSVS" "$SOLR_DATA"

# ─── Build artifacts (skipped when pre-built binaries exist in the container) ─
build_all() {
    if [ -f "$RDF2JSON_JAR" ] && [ -f "$RUST_BINS/ols_json2neo" ]; then
        log "Pre-built binaries found — skipping build step"
        return
    fi
    log "Building Java artifacts..."
    mvn -B -ntp install -f "$OLS4_HOME/ols-shared/pom.xml" -DskipTests -q
    mvn -B -ntp package -f "$DATALOAD/rdf2json/pom.xml" -DskipTests -q
    mvn -B -ntp package -f "$DATALOAD/solr_config_builder/pom.xml" -DskipTests -q
    mvn -B -ntp package -f "$DATALOAD/merge_configs/pom.xml" -DskipTests -q
    log "Building Rust artifacts..."
    (cd "$DATALOAD" && cargo build --release -q)
    (cd "$OLS4_HOME/text_tagger" && cargo build --release -q)
}

build_all

# ─── 1. Merge all testcase configs ────────────────────────────────────────────
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
cp "$TMP_DIR/text_tagger_db.bin" "$OUT_DIR/text_tagger_db.bin"

# ─── 3. Set up Solr ───────────────────────────────────────────────────────────
# Copy the full Solr installation from /opt/solr (bundled in the container),
# configure cores, then pre-index all JSONL data so docker-compose can start
# Solr with data already loaded (no separate indexing step needed).
log "Copying Solr installation from /opt/solr..."
rm -rf "$OUT_DIR/solr"
cp -r /opt/solr "$OUT_DIR/solr"
mkdir -p "$OUT_DIR/solr/logs"

log "Building Solr core configs..."
java -jar "$SOLR_CFG_BUILDER_JAR" \
    --manifestPath           "$LINKER_MANIFEST" \
    --solrConfigTemplatePath "$SOLR_CFG_TEMPLATE" \
    --outDir                 "$OUT_DIR/solr/server/solr"

log "Pre-indexing Solr (temporary server on port 8983)..."
export SOLR_ENABLE_REMOTE_STREAMING="true"
export SOLR_SECURITY_MANAGER_ENABLED="false"
export JAVA_TOOL_OPTIONS="-Djava.net.useSystemProxies=false"

/opt/solr/bin/solr start -m 4g -p 8983 -noprompt -force --solr-home "$OUT_DIR/solr"

log "Waiting for Solr to start..."
for i in {1..60}; do
    if curl -sf "http://localhost:8983/solr/admin/info/system" &>/dev/null; then
        log "Solr is ready."
        break
    fi
    sleep 2
done

log "Indexing JSONL files into Solr..."
while IFS= read -r -d '' f; do
    if [[ "$f" == *autocomplete* ]]; then
        core="ols4_autocomplete"
    else
        core="ols4_entities"
    fi
    
    # Retry loop for each file
    success=false
    for attempt in {1..3}; do
        response=$(curl -s -w "\n%{http_code}" -X POST -H "Content-Type: application/json" \
            --data-binary "@$f" \
            "http://127.0.0.1:8983/solr/$core/update/json/docs")
        
        http_code="${response##*$'\n'}"
        body="${response%$'\n'*}"
        
        if [[ "$http_code" == "200" ]]; then
            log "Uploaded $f to $core"
            success=true
            break
        else
            log "Attempt $attempt: Error uploading $f to $core (HTTP $http_code)"
            sleep 5
        fi
    done
    
    if [ "$success" = false ]; then
        err "Failed to upload $f to $core after 3 attempts."
    fi
done < <(find "$SOLR_DATA" -name "*.jsonl" -print0)

log "Committing Solr..."
curl -sf "http://127.0.0.1:8983/solr/ols4_entities/update?commit=true" >/dev/null || err "Failed to commit ols4_entities"
curl -sf "http://127.0.0.1:8983/solr/ols4_autocomplete/update?commit=true" >/dev/null || err "Failed to commit ols4_autocomplete"

/opt/solr/bin/solr stop -p 8983

# ─── 4. Set up Neo4j ──────────────────────────────────────────────────────────
# Use Neo4j 2025.03.0 bundled in the container — the same version as
# docker-compose — so data format is guaranteed compatible.
log "Importing CSVs into Neo4j (using /opt/neo4j)..."
"$DATALOAD/load_into_neo4j.sh" /opt/neo4j "$NEO_CSVS" 4g

log "Copying Neo4j data to output..."
mkdir -p "$OUT_DIR/neo4j/data"
cp -r /opt/neo4j/data/databases "$OUT_DIR/neo4j/data/"
cp -r /opt/neo4j/data/transactions "$OUT_DIR/neo4j/data/" 2>/dev/null || true

log "Dataload CI complete."
