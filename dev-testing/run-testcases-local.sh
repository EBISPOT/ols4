#!/usr/bin/env bash
# run-testcases-local.sh — Run OLS4 testcases without Docker
#
# Usage: ./dev-testing/run-testcases-local.sh [dataload|api|all]
#   dataload — Part 1: mock dataload test (mirrors test_dataload.sh)
#   api      — Part 2: full API test against live backend (mirrors test_api.sh)
#   all      — Both tests (default)
#
# Prerequisites: Java 21, Maven, Rust/Cargo, Python 3
# For api/all: NEO4J_HOME and SOLR_HOME must be set.

set -Eeuo pipefail

# ─── Colours ──────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[run-testcases]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

# ─── Args ─────────────────────────────────────────────────────────────────────
MODE="${1:-all}"
[[ "$MODE" =~ ^(dataload|api|all)$ ]] || err "Usage: $0 [dataload|api|all]"

# ─── Paths ────────────────────────────────────────────────────────────────────
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
OLS4_HOME=$(cd "$SCRIPT_DIR/.." && pwd)

DATALOAD="$OLS4_HOME/dataload"
RDF2JSON_JAR="$DATALOAD/rdf2json/target/rdf2json-1.0-SNAPSHOT.jar"
SOLR_CFG_BUILDER_JAR="$DATALOAD/solr_config_builder/target/solr_config_builder-1.0-SNAPSHOT.jar"
SOLR_CFG_TEMPLATE="$DATALOAD/solr_config_template"
NEO4J_INDEXES_PY="$DATALOAD/create_neo4j_indexes.py"
MERGE_CONFIGS_JAR="$DATALOAD/merge_configs/target/merge_configs-1.0-SNAPSHOT.jar"
RUST_BINS="$DATALOAD/target/release"

TESTCASES_DIR="$OLS4_HOME/testcases"
TESTCASES_OUTPUT="$OLS4_HOME/testcases_output"
TESTCASES_EXPECTED="$OLS4_HOME/testcases_expected_output"
TESTCASES_OUTPUT_API="$OLS4_HOME/testcases_output_api"
TESTCASES_EXPECTED_API="$OLS4_HOME/testcases_expected_output_api"
BACKEND_JAR="$OLS4_HOME/backend/target/ols4-backend-4.0.0-SNAPSHOT.jar"
APITESTER_JAR="$OLS4_HOME/apitester4/target/apitester-1.0-SNAPSHOT-jar-with-dependencies.jar"

BACKEND_PID_FILE="/tmp/ols4-testcases-backend.pid"
BACKEND_LOG="/tmp/ols4-testcases-backend.log"
API_PIPELINE_OUT="$OLS4_HOME/testcases_api_pipeline_out"

TEXT_TAGGER_HOME="$OLS4_HOME/text_tagger"
TEXT_TAGGER_BIN="$TEXT_TAGGER_HOME/target/release/ols_text_tagger"

# ─── Prereq checks ────────────────────────────────────────────────────────────
command -v java    &>/dev/null || err "java not found on PATH"
command -v mvn     &>/dev/null || err "mvn not found on PATH"
command -v cargo   &>/dev/null || err "cargo not found on PATH"
command -v python3 &>/dev/null || err "python3 not found on PATH"

if [[ "$MODE" == "api" || "$MODE" == "all" ]]; then
    [ -z "${NEO4J_HOME:-}" ] && err "NEO4J_HOME not set (point at Neo4j install dir)"
    [ -z "${SOLR_HOME:-}" ]  && err "SOLR_HOME not set (point at Solr install dir)"
fi

# ─── Build all artifacts ──────────────────────────────────────────────────────
build_all() {
    log "Building ols-shared..."
    mvn -B -ntp install -f "$OLS4_HOME/ols-shared/pom.xml" -DskipTests -q

    log "Building rdf2json..."
    mvn -B -ntp package -f "$DATALOAD/rdf2json/pom.xml" -DskipTests -q

    log "Building solr_config_builder..."
    mvn -B -ntp package -f "$DATALOAD/solr_config_builder/pom.xml" -DskipTests -q

    log "Building merge_configs..."
    mvn -B -ntp package -f "$DATALOAD/merge_configs/pom.xml" -DskipTests -q

    log "Building Rust workspace..."
    (cd "$DATALOAD" && cargo build --release -q)

    for bin in ols_create_manifest ols_link ols_json2neo ols_json2solr; do
        [ -f "$RUST_BINS/$bin" ] || err "Rust binary missing after build: $RUST_BINS/$bin"
    done

    if [[ "$MODE" == "api" || "$MODE" == "all" ]]; then
        log "Building backend..."
        mvn -B -ntp package -f "$OLS4_HOME/backend/pom.xml" -DskipTests -q

        log "Building apitester4..."
        mvn -B -ntp package -f "$OLS4_HOME/apitester4/pom.xml" -DskipTests -q

        log "Building ols_text_tagger..."
        (cd "$TEXT_TAGGER_HOME" && cargo build --release -q)
        [ -f "$TEXT_TAGGER_BIN" ] || err "ols_text_tagger binary missing after build: $TEXT_TAGGER_BIN"
    fi

    log "Build complete."
}

# ─── Diff report helper ───────────────────────────────────────────────────────
# Prints a structured summary: which files differ/added/removed, then content.
# Returns 0 if identical, 1 if differences found.
_diff_report() {
    local actual="$1"
    local expected="$2"
    local rc=0

    # Collect brief diff lines (only file-level: differ / only in X)
    BRIEF=$(diff --recursive --brief --exclude=.gitkeep "$actual" "$expected" 2>/dev/null || true)

    if [ -z "$BRIEF" ]; then
        return 0
    fi

    rc=1

    # ── Summary ───────────────────────────────────────────────────────────────
    local n_differ n_only_out n_only_exp
    n_differ=$(echo "$BRIEF"  | grep -c "^Files.*differ$"             || true)
    n_only_out=$(echo "$BRIEF" | grep -c "^Only in $actual"           || true)
    n_only_exp=$(echo "$BRIEF" | grep -c "^Only in $expected"         || true)

    echo ""
    echo -e "${RED}── Diff summary ─────────────────────────────────────────────${NC}"
    [ "$n_differ"   -gt 0 ] && echo -e "  ${YELLOW}CHANGED${NC}  $n_differ file(s) differ"
    [ "$n_only_out" -gt 0 ] && echo -e "  ${YELLOW}EXTRA${NC}    $n_only_out file(s) only in output (not in expected)"
    [ "$n_only_exp" -gt 0 ] && echo -e "  ${YELLOW}MISSING${NC}  $n_only_exp file(s) only in expected (not in output)"

    # ── Per-file listing ──────────────────────────────────────────────────────
    echo ""
    echo "  Files:"
    while IFS= read -r line; do
        # "Files A and B differ"  →  show relative path
        if [[ "$line" == Files* ]]; then
            # extract "actual" side path, strip leading actual/ prefix
            FILE=$(echo "$line" | sed "s|Files ||; s| and .*||")
            REL="${FILE#$actual/}"
            echo -e "    ${YELLOW}~${NC}  $REL"
        # "Only in DIR: FILE"  →  show with +/- indicator
        elif [[ "$line" == Only\ in\ $actual* ]]; then
            DIR=$(echo "$line" | sed 's/Only in //; s/: .*//')
            FILE=$(echo "$line" | sed 's/.*: //')
            REL="${DIR#$actual/}/$FILE"
            echo -e "    ${RED}+${NC}  $REL  (extra, not in expected)"
        elif [[ "$line" == Only\ in\ $expected* ]]; then
            DIR=$(echo "$line" | sed 's/Only in //; s/: .*//')
            FILE=$(echo "$line" | sed 's/.*: //')
            REL="${DIR#$expected/}/$FILE"
            echo -e "    ${RED}-${NC}  $REL  (missing from output)"
        fi
    done <<< "$BRIEF"

    # ── Content diffs for changed files ──────────────────────────────────────
    if [ "$n_differ" -gt 0 ]; then
        echo ""
        echo -e "${RED}── Content diffs ────────────────────────────────────────────${NC}"
        while IFS= read -r line; do
            if [[ "$line" == Files* ]]; then
                FILE_A=$(echo "$line" | sed "s/Files //; s/ and .*//")
                FILE_B=$(echo "$line" | sed "s/.* and //; s/ differ//")
                REL="${FILE_A#$actual/}"
                echo ""
                echo -e "  ${YELLOW}~~~ $REL ~~~${NC}"
                diff --unified=3 "$FILE_A" "$FILE_B" || true
            fi
        done <<< "$BRIEF"
    fi

    echo ""
    return $rc
}

# ─── PART 1: Mock dataload test ───────────────────────────────────────────────
# Mirrors test_dataload.sh but ensures artifacts are built first.
# For each testcase JSON config, runs the full data pipeline (rdf2json → link →
# json2neo → json2solr) and diffs output against testcases_expected_output/.
run_dataload_test() {
    log "=== Part 1: Mock dataload test ==="

    rm -rf "$TESTCASES_OUTPUT"
    mkdir -p "$TESTCASES_OUTPUT"

    # Collect all testcase JSON configs (bash 3.x compatible)
    CONFIGS=()
    while IFS= read -r f; do
        CONFIGS+=("$f")
    done < <(find "$TESTCASES_DIR" -name "*.json" | sort)

    for CONFIG_FILE in "${CONFIGS[@]}"; do
        BASENAME=$(basename "$CONFIG_FILE" .json)
        DIRNAME=$(basename "$(dirname "$CONFIG_FILE")")
        OUT_DIR="$TESTCASES_OUTPUT/$DIRNAME/$BASENAME"
        mkdir -p "$OUT_DIR"
        log "  $DIRNAME/$BASENAME"
        "$SCRIPT_DIR/create_datafiles.sh" "$CONFIG_FILE" "$OUT_DIR" --loadLocalFiles --noDates
    done

    log "Comparing output against testcases_expected_output/ ..."
    _diff_report "$TESTCASES_OUTPUT" "$TESTCASES_EXPECTED"
    local rc=$?
    if [ "$rc" -eq 0 ]; then
        log "Part 1 PASSED"
        return 0
    else
        warn "Part 1 FAILED"
        return 1
    fi
}

# ─── API diff summary helper ──────────────────────────────────────────────────
# Parses the apitester4.log and prints a structured diff summary.
# apitester4 (RecursiveJsonDiff) prints three line patterns:
#   "X differed from Y"              → content mismatch
#   "X was not present in Y"         → file only in one dir
# We separate these into: CHANGED / EXTRA (in output, not expected) / MISSING.
_api_diff_summary() {
    local log_file="$1"
    [ -f "$log_file" ] || return 0

    # Collect each category (strip full paths to keep output readable)
    # Separate local declaration from assignment — bash 3.x (macOS) does not
    # initialise arrays declared as "local arr=()" under set -u.
    local changed extra missing
    changed=() extra=() missing=()

    while IFS= read -r line; do
        if [[ "$line" == *" differed from "* ]]; then
            # "path/to/file.json differed from path/to/file.json"
            local rel
            rel=$(echo "$line" | sed "s| differed from .*||; s|$TESTCASES_OUTPUT_API/||")
            changed+=("$rel")
        elif [[ "$line" == *"was not present in $TESTCASES_EXPECTED_API"* ]]; then
            # file in output but not in expected → extra
            local rel
            rel=$(echo "$line" | sed "s| was not present in .*||; s|$TESTCASES_OUTPUT_API/||")
            extra+=("$rel")
        elif [[ "$line" == *"was not present in $TESTCASES_OUTPUT_API"* ]]; then
            # file in expected but not in output → missing
            local rel
            rel=$(echo "$line" | sed "s| was not present in .*||; s|$TESTCASES_EXPECTED_API/||")
            missing+=("$rel")
        fi
    done < "$log_file"

    local total=$(( ${#changed[@]} + ${#extra[@]} + ${#missing[@]} ))
    [ "$total" -eq 0 ] && return 0

    echo ""
    echo -e "${RED}── API diff summary ─────────────────────────────────────────${NC}"
    [ "${#changed[@]}"  -gt 0 ] && echo -e "  ${YELLOW}CHANGED${NC}  ${#changed[@]} file(s) — content differs"
    [ "${#extra[@]}"    -gt 0 ] && echo -e "  ${YELLOW}EXTRA${NC}    ${#extra[@]} file(s) — in output, not in expected"
    [ "${#missing[@]}"  -gt 0 ] && echo -e "  ${YELLOW}MISSING${NC}  ${#missing[@]} file(s) — in expected, not in output"
    echo ""

    # Highlight tag_text results explicitly
    local tag_changed tag_missing tag_extra
    tag_changed=() tag_missing=() tag_extra=()
    for f in "${changed[@]}";  do [[ "$f" == *tag_text* || "$f" == *curation* ]] && tag_changed+=("$f");  done
    for f in "${missing[@]}";  do [[ "$f" == *tag_text* || "$f" == *curation* ]] && tag_missing+=("$f");  done
    for f in "${extra[@]}";    do [[ "$f" == *tag_text* || "$f" == *curation* ]] && tag_extra+=("$f");    done

    if [ $(( ${#tag_changed[@]} + ${#tag_missing[@]} + ${#tag_extra[@]} )) -gt 0 ]; then
        echo -e "  ${RED}Text tagging diffs:${NC}"
        for f in "${tag_changed[@]}";  do echo -e "    ${YELLOW}~${NC}  $f"; done
        for f in "${tag_missing[@]}";  do echo -e "    ${RED}-${NC}  $f  (missing from output)"; done
        for f in "${tag_extra[@]}";    do echo -e "    ${RED}+${NC}  $f  (extra, not in expected)"; done
        echo ""
        echo "  Tag_text depends on:"
        echo "    • owl/skos/rdfs ontologies loaded (from testcases/annotation-properties/gitIssue502.json)"
        echo "    • SSSOM curations processed (testcases/curations/*.sssom.tsv → 'test-curations' source)"
        echo ""
    fi

    # Changed files list (up to 30 to avoid wall of text)
    if [ "${#changed[@]}" -gt 0 ]; then
        echo "  Changed files:"
        local shown=0
        for f in "${changed[@]}"; do
            echo -e "    ${YELLOW}~${NC}  $f"
            (( shown++ )) || true
            if [ "$shown" -ge 30 ] && [ "${#changed[@]}" -gt 30 ]; then
                echo "    ... and $(( ${#changed[@]} - 30 )) more — see $TESTCASES_OUTPUT_API/apitester4.log"
                break
            fi
        done
    fi

    if [ "${#extra[@]}" -gt 0 ]; then
        echo "  Extra (not in expected):"
        for f in "${extra[@]}"; do echo -e "    ${RED}+${NC}  $f"; done
    fi

    if [ "${#missing[@]}" -gt 0 ]; then
        echo "  Missing from output:"
        for f in "${missing[@]}"; do echo -e "    ${RED}-${NC}  $f"; done
    fi

    echo ""
    echo "  To update expected output after intentional changes:"
    echo "    rm -rf $TESTCASES_EXPECTED_API"
    echo "    mv $TESTCASES_OUTPUT_API $TESTCASES_EXPECTED_API"
    echo ""
}

# ─── PART 2: API test ─────────────────────────────────────────────────────────
# Mirrors test_api.sh but without Docker:
#   1. Merge all testcase configs into one combined config
#   2. Run full data pipeline on the combined config
#   3. Load into local Solr + Neo4j
#   4. Start backend (background)
#   5. Run apitester4.jar against localhost:8080
#   6. Compare against testcases_expected_output_api/
run_api_test() {
    log "=== Part 2: API test ==="

    local NEO_CSVS="$API_PIPELINE_OUT/neo-csvs"
    local SOLR_DATA="$API_PIPELINE_OUT/solr-data"
    local SOLR_HOME_DIR="$API_PIPELINE_OUT/solr-home"
    local COMBINED_CONFIG="$API_PIPELINE_OUT/combined_config.json"
    local ONTOLOGIES_JSON="$API_PIPELINE_OUT/ontologies.json"
    local LINKER_MANIFEST="$API_PIPELINE_OUT/linker_manifest.json"
    local ONTOLOGIES_LINKED="$API_PIPELINE_OUT/ontologies_linked.json"

    rm -rf "$API_PIPELINE_OUT"
    mkdir -p "$NEO_CSVS" "$SOLR_DATA" "$SOLR_HOME_DIR"
    rm -rf "$TESTCASES_OUTPUT_API"
    mkdir -p "$TESTCASES_OUTPUT_API"

    # ── 1. Merge all testcase configs ─────────────────────────────────────────
    # Use merge_configs.jar — same tool as Nextflow/Docker pipeline. It deduplicates
    # by ontology id (case-insensitive), matching production behaviour exactly.
    log "Merging all testcase configs..."
    CONFIG_CSV=$(find "$TESTCASES_DIR" -name "*.json" | sort | paste -sd, -)
    java -jar "$MERGE_CONFIGS_JAR" \
        --config "$CONFIG_CSV" \
        --output "$COMBINED_CONFIG"
    log "Merged config written to $COMBINED_CONFIG"

    # ── 2. Run data pipeline ──────────────────────────────────────────────────
    # rdf2json must run from OLS4_HOME: testcase configs use ./testcases/... relative paths.
    log "Running rdf2json on combined config..."
    (cd "$OLS4_HOME" && java ${JAVA_OPTS:-} \
        -DentityExpansionLimit=0 -DtotalEntitySizeLimit=0 \
        -Djdk.xml.totalEntitySizeLimit=0 -Djdk.xml.entityExpansionLimit=0 \
        -jar "$RDF2JSON_JAR" \
        --config "$COMBINED_CONFIG" \
        --output "$ONTOLOGIES_JSON" \
        --loadLocalFiles \
        --noDates)
    [ -f "$ONTOLOGIES_JSON" ] || err "rdf2json produced no output"

    log "Running ols_create_manifest..."
    "$RUST_BINS/ols_create_manifest" \
        --input  "$ONTOLOGIES_JSON" \
        --output "$LINKER_MANIFEST"
    [ -f "$LINKER_MANIFEST" ] || err "create_manifest produced no output"

    log "Running ols_link..."
    # Collect SSSOM curations (bash 3.x compatible)
    SSSOM_ARGS=()
    while IFS= read -r -d '' f; do
        SSSOM_ARGS+=("$f")
    done < <(find "$TESTCASES_DIR/curations" -name "*.sssom.tsv" -print0 2>/dev/null)

    SSSOM_OPTS=()
    if [ "${#SSSOM_ARGS[@]}" -gt 0 ]; then
        SSSOM_OPTS=(--sssom "${SSSOM_ARGS[@]}")
    fi

    "$RUST_BINS/ols_link" \
        --manifest "$LINKER_MANIFEST" \
        --input    "$ONTOLOGIES_JSON" \
        --output   "$ONTOLOGIES_LINKED" \
        "${SSSOM_OPTS[@]}"
    [ -f "$ONTOLOGIES_LINKED" ] || err "ols_link produced no output"

    log "Running ols_json2neo..."
    EMBED_ARGS=()
    while IFS= read -r -d '' f; do
        EMBED_ARGS+=("$f")
    done < <(find "$TESTCASES_DIR/embeddings" -name "*.parquet" -print0 2>/dev/null)

    EMBED_OPTS=()
    if [ "${#EMBED_ARGS[@]}" -gt 0 ]; then
        EMBED_OPTS=(--embeddingParquets "${EMBED_ARGS[@]}")
    fi

    "$RUST_BINS/ols_json2neo" \
        --manifest "$LINKER_MANIFEST" \
        --input    "$ONTOLOGIES_LINKED" \
        --outDir   "$NEO_CSVS" \
        "${EMBED_OPTS[@]}"

    log "Running ols_json2solr..."
    "$RUST_BINS/ols_json2solr" \
        --input  "$ONTOLOGIES_LINKED" \
        --outDir "$SOLR_DATA"

    # ── 2b. Build text tagger database ────────────────────────────────────────
    # extract_strings_from_terms → terms.tsv → ols_text_tagger build → .bin
    # The backend TextTaggerService needs this binary database + ols_text_tagger on PATH.
    log "Building text tagger database..."
    "$RUST_BINS/extract_strings_from_terms" "$ONTOLOGIES_LINKED" \
        > "$API_PIPELINE_OUT/terms.tsv"
    "$TEXT_TAGGER_BIN" build \
        --output "$API_PIPELINE_OUT/text_tagger_db.bin" \
        < "$API_PIPELINE_OUT/terms.tsv"
    log "Text tagger DB built: $API_PIPELINE_OUT/text_tagger_db.bin"

    # ── 3. Set up Solr ────────────────────────────────────────────────────────
    log "Building Solr config..."
    java -jar "$SOLR_CFG_BUILDER_JAR" \
        --manifestPath           "$LINKER_MANIFEST" \
        --solrConfigTemplatePath "$SOLR_CFG_TEMPLATE" \
        --outDir                 "$SOLR_HOME_DIR"

    SOLR_XML_SRC="$SOLR_HOME/server/solr/solr.xml"
    [ -f "$SOLR_XML_SRC" ] || err "solr.xml not found at $SOLR_XML_SRC — check SOLR_HOME"
    cp "$SOLR_XML_SRC" "$SOLR_HOME_DIR/solr.xml"

    for core in ols4_entities ols4_autocomplete; do
        mkdir -p "$SOLR_HOME_DIR/$core"
        echo "name=$core" > "$SOLR_HOME_DIR/$core/core.properties"
    done

    log "Stopping any running Solr..."
    "$SOLR_HOME/bin/solr" stop -all 2>/dev/null || true
    sleep 3

    export SOLR_HEAP="${SOLR_HEAP:-2g}"
    log "Starting Solr (port 8983, heap: $SOLR_HEAP)..."
    "$SOLR_HOME/bin/solr" start -s "$SOLR_HOME_DIR" -p 8983 -force

    log "Waiting for Solr..."
    for i in $(seq 1 30); do
        if curl -sf "http://localhost:8983/solr/ols4_entities/admin/ping" &>/dev/null; then
            log "Solr ready."; break
        fi
        [ "$i" -eq 30 ] && err "Solr did not become ready within 60 seconds"
        sleep 2
    done

    log "Loading JSONL into Solr..."
    while IFS= read -r -d '' jsonl_file; do
        if [[ "$jsonl_file" == *autocomplete* ]]; then
            core="ols4_autocomplete"
        else
            core="ols4_entities"
        fi
        response=$(curl -s -w "\n%{http_code}" \
            -X POST -H "Content-Type: application/json" \
            --data-binary "@$jsonl_file" \
            "http://localhost:8983/solr/$core/update/json/docs")
        http_code="${response##*$'\n'}"
        body="${response%$'\n'*}"
        [[ "$http_code" == "200" ]] || err "Solr rejected $(basename "$jsonl_file") on $core (HTTP $http_code): $body"
    done < <(find "$SOLR_DATA" -name "*.jsonl" -print0)

    curl -sf "http://localhost:8983/solr/ols4_entities/update?commit=true" >/dev/null \
        || err "Solr commit failed for ols4_entities"
    curl -sf "http://localhost:8983/solr/ols4_autocomplete/update?commit=true" >/dev/null \
        || err "Solr commit failed for ols4_autocomplete"
    log "Solr loaded and committed."

    # ── 4. Set up Neo4j ───────────────────────────────────────────────────────
    log "Stopping any running Neo4j..."
    "$NEO4J_HOME/bin/neo4j" stop 2>/dev/null || true
    sleep 5

    log "Importing CSVs into Neo4j..."
    NODE_ARGS=()
    REL_ARGS=()

    for pattern in "*_ontologies.csv" "*_classes.csv" "*_properties.csv" "*_individuals.csv" "*_embedding_nodes.csv"; do
        while IFS= read -r -d '' f; do
            NODE_ARGS+=("--nodes=$f")
        done < <(find "$NEO_CSVS" -name "$pattern" -print0 2>/dev/null)
    done

    while IFS= read -r -d '' f; do
        REL_ARGS+=("--relationships=$f")
    done < <(find "$NEO_CSVS" -name "*_edges.csv" -print0 2>/dev/null)

    rm -rf "$NEO4J_HOME/data/databases/neo4j"
    rm -rf "$NEO4J_HOME/data/transactions/neo4j"

    "$NEO4J_HOME/bin/neo4j-admin" database import full neo4j \
        --overwrite-destination \
        --ignore-empty-strings=true \
        --legacy-style-quoting=false \
        --multiline-fields=true \
        --array-delimiter="|" \
        --threads=4 \
        --read-buffer-size=134217728 \
        "${NODE_ARGS[@]}" \
        "${REL_ARGS[@]}"
    log "Neo4j import complete."

    log "Starting Neo4j..."
    "$NEO4J_HOME/bin/neo4j" start

    log "Waiting for Neo4j on bolt://localhost:7687..."
    for i in $(seq 1 90); do
        if "$NEO4J_HOME/bin/cypher-shell" -a neo4j://127.0.0.1:7687 --non-interactive "RETURN 1;" >/dev/null 2>&1; then
            log "Neo4j ready."; break
        fi
        [ "$i" -eq 90 ] && err "Neo4j did not become ready within 90 seconds"
        sleep 1
    done

    log "Creating Neo4j indexes (including vector indexes for embeddings)..."
    # Pass embedding parquets so create_neo4j_indexes.py generates vector index Cypher.
    # Requires pyarrow: pip install pyarrow
    python3 "$NEO4J_INDEXES_PY" "${EMBED_ARGS[@]}" | \
        "$NEO4J_HOME/bin/cypher-shell" --non-interactive 2>&1 | { grep -v "^$" || true; }
    log "Neo4j indexes created."

    # ── 5. Start backend ──────────────────────────────────────────────────────
    [ -f "$BACKEND_JAR" ] || err "Backend JAR not found: $BACKEND_JAR"

    # Kill any previous backend left over
    if [ -f "$BACKEND_PID_FILE" ]; then
        OLD_PID=$(cat "$BACKEND_PID_FILE")
        kill "$OLD_PID" 2>/dev/null || true
        rm -f "$BACKEND_PID_FILE"
    fi

    # Check port 8080 not already in use
    if lsof -ti tcp:8080 &>/dev/null; then
        err "Port 8080 already in use. Stop whatever is running there first."
    fi

    log "Starting backend (log: $BACKEND_LOG)..."
    # ols_text_tagger must be on PATH for TextTaggerService to start.
    # ols.text.tagger.db points to the binary database we built above.
    PATH="$TEXT_TAGGER_HOME/target/release:$PATH" \
    java -jar "$BACKEND_JAR" \
        --server.port=8080 \
        --ols.text.tagger.db="$API_PIPELINE_OUT/text_tagger_db.bin" \
        > "$BACKEND_LOG" 2>&1 &
    BACKEND_PID=$!
    echo "$BACKEND_PID" > "$BACKEND_PID_FILE"

    log "Waiting for backend on http://localhost:8080..."
    for i in $(seq 1 60); do
        if curl -sf "http://localhost:8080/api/ontologies?size=1" &>/dev/null; then
            log "Backend ready."; break
        fi
        if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
            err "Backend process died. Check $BACKEND_LOG"
        fi
        [ "$i" -eq 60 ] && err "Backend timeout after 180s. Check $BACKEND_LOG"
        sleep 3
    done

    # ── 6. Run apitester ──────────────────────────────────────────────────────
    log "Running apitester4 against http://localhost:8080..."

    # Disable errexit+pipefail so we can capture java's exit code through the pipe.
    set +e
    set +o pipefail
    java --add-opens java.base/java.lang=ALL-UNNAMED \
        -jar "$APITESTER_JAR" \
        --url         "http://localhost:8080" \
        --outDir      "$TESTCASES_OUTPUT_API" \
        --compareDir  "$TESTCASES_EXPECTED_API" \
        --deep \
        2>&1 | tee "$TESTCASES_OUTPUT_API/apitester4.log"
    APITESTER_EXIT=${PIPESTATUS[0]}
    set -e
    set -o pipefail

    # ── Cleanup backend ───────────────────────────────────────────────────────
    log "Stopping backend (PID $BACKEND_PID)..."
    kill "$BACKEND_PID" 2>/dev/null || true
    rm -f "$BACKEND_PID_FILE"

    # ── Diff summary ──────────────────────────────────────────────────────────
    _api_diff_summary "$TESTCASES_OUTPUT_API/apitester4.log"

    if [ "$APITESTER_EXIT" -eq 0 ]; then
        log "Part 2 PASSED"
        return 0
    else
        warn "Part 2 FAILED"
        return 1
    fi
}

# ─── Main ─────────────────────────────────────────────────────────────────────
build_all

OVERALL_EXIT=0

if [[ "$MODE" == "dataload" || "$MODE" == "all" ]]; then
    run_dataload_test || OVERALL_EXIT=1
fi

if [[ "$MODE" == "api" || "$MODE" == "all" ]]; then
    run_api_test || OVERALL_EXIT=1
fi

echo ""
if [ "$OVERALL_EXIT" -eq 0 ]; then
    echo -e "${GREEN}All tests PASSED${NC}"
else
    echo -e "${RED}Some tests FAILED${NC}"
fi
exit "$OVERALL_EXIT"