#!/usr/bin/env bash
#
# setup-solr-schema.sh
#
# Runs solr_config_builder with the given manifest to produce a full Solr
# schema (with explicit <field> and <copyField> entries for every OLS property),
# then hot-reloads the ols4_entities core so Solr picks it up immediately.
#
# Usage: setup-solr-schema.sh <path-to-linker_manifest.json>
#
# Environment variables (with defaults):
#   SOLR_HOME  - root of the Solr installation  (default: /opt/solr)

if [ $# -lt 1 ]; then
    echo "Usage: $0 <manifest_json_path>"
    exit 1
fi

MANIFEST_PATH=$1

# Resolve the script directory in a way that works on both macOS and Linux
SCRIPT_PATH="$(cd "$(dirname "$0")" && pwd -P)"

SOLR_HOME=${SOLR_HOME:-/opt/solr}
GENERATED_CONFIG_DIR=/tmp/ols_solr_config_$$

echo "--- setup-solr-schema: building Solr schema from manifest: $MANIFEST_PATH"

java -jar "$SCRIPT_PATH/../dataload/solr_config_builder/target/solr_config_builder-1.0-SNAPSHOT.jar" \
    --manifestPath "$MANIFEST_PATH" \
    --solrConfigTemplatePath "$SCRIPT_PATH/../dataload/solr_config_template" \
    --outDir "$GENERATED_CONFIG_DIR"

if [ $? -ne 0 ]; then
    echo "ERROR: solr_config_builder failed - Solr schema not updated."
    rm -rf "$GENERATED_CONFIG_DIR"
    exit 1
fi

echo "--- setup-solr-schema: applying generated schema.xml to Solr core ols4_entities"
cp "$GENERATED_CONFIG_DIR/ols4_entities/conf/schema.xml" \
   "$SOLR_HOME/server/solr/ols4_entities/conf/schema.xml"

rm -rf "$GENERATED_CONFIG_DIR"

echo "--- setup-solr-schema: reloading Solr core ols4_entities"
wget --quiet -O - "http://localhost:8983/solr/admin/cores?action=RELOAD&core=ols4_entities"

echo "--- setup-solr-schema: done"
