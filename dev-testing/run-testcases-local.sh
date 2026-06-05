#!/usr/bin/env bash
# run-testcases-local.sh — Build all dataload artifacts and run testcases locally
#
# Usage: ./dev-testing/run-testcases-local.sh
#
# Prerequisites: Java 17+, Maven, Rust/Cargo

set -Eeuo pipefail

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[run-testcases]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
OLS4_HOME=$(cd "$SCRIPT_DIR/.." && pwd)
DATALOAD="$OLS4_HOME/dataload"
RUST_BINS="$DATALOAD/target/release"

# ─── Prereq checks ────────────────────────────────────────────────────────────
command -v java  &>/dev/null || err "java not found on PATH"
command -v mvn   &>/dev/null || err "mvn not found on PATH"
command -v cargo &>/dev/null || err "cargo not found on PATH"

# ─── Build ────────────────────────────────────────────────────────────────────
log "Building ols-shared..."
mvn -B -ntp install -f "$OLS4_HOME/ols-shared/pom.xml" -DskipTests -q

log "Building rdf2json..."
mvn -B -ntp package -f "$DATALOAD/rdf2json/pom.xml" -DskipTests -q

log "Building Rust workspace..."
(cd "$DATALOAD" && cargo build --release -q)

for bin in ols_create_manifest ols_link ols_json2postgres; do
    [ -f "$RUST_BINS/$bin" ] || err "Rust binary missing after build: $RUST_BINS/$bin"
done

log "Build complete."

# ─── Run dataload testcases ───────────────────────────────────────────────────
log "Running dataload testcases..."
cd "$OLS4_HOME"
bash test_dataload.sh
