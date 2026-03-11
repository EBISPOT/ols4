#!/bin/bash

set -Eeuo pipefail

if ! command -v docker &> /dev/null
then
  echo "Docker could not be found. Please install Docker to proceed."
  exit 1
fi

if [ -z "${OLS4_CONFIG:-}" ]; then
  echo "Error: OLS4_CONFIG environment variable is not set."
  exit 1
fi

# These folders are mounted to have the same path in the Docker containers as they do on the host.
# This includes: 
#   - The nextflow container that runs nextflow (which we start below)
#   - The containers nextflow starts to run processes, configured in the nextflow config files
#+
# Determine repository root in a POSIX-compatible way (works on macOS and Linux)
OLS_HOME="$(cd "$(dirname "$0")" && pwd -P)"

# Default embeddings path if not provided
if [ -z "${OLS_EMBEDDINGS_PATH:-}" ]; then
  OLS_EMBEDDINGS_PATH="$OLS_HOME/embeddings"
fi
OLS_EMBEDDINGS_PATH="$(realpath "$OLS_EMBEDDINGS_PATH")"

# Default Nextflow config if not provided
if [ -z "${OLS_NF_CONFIG:-}" ]; then
  OLS_NF_CONFIG="$OLS_HOME/dataload/nextflow/local_nextflow.config"
fi

if [ -z "${OLS4_DATALOAD_IMAGE:-}" ]; then
  echo "OLS4_DATALOAD_IMAGE environment variable is not set. Using dev image."
  OLS4_DATALOAD_IMAGE="ghcr.io/ebispot/ols4-dataload:dev"
else
  echo "Using OLS4_DATALOAD_IMAGE: $OLS4_DATALOAD_IMAGE"
fi

if [ -z "${OLS4_EMBED_IMAGE:-}" ]; then
  echo "OLS4_EMBED_IMAGE environment variable is not set. Using dev image."
  OLS4_EMBED_IMAGE="ghcr.io/ebispot/ols4-embed:dev"
else
  echo "Using OLS4_EMBED_IMAGE: $OLS4_EMBED_IMAGE"
fi

TMP_DIR="$OLS_HOME/tmp"
OUT_DIR="$OLS_HOME/out"

mkdir -p "$TMP_DIR/work" "$TMP_DIR/NXF_HOME" "$TMP_DIR/NXF_TEMP" "$TMP_DIR/NXF_CACHE_DIR"

# Ensure nested Docker containers (spawned by Nextflow) run with the same UID/GID as the host user
# to avoid permission issues when writing to the bind-mounted work directory on GitHub Actions.
HOST_UID=$(id -u)
HOST_GID=$(id -g)

USER_OPT=""
if [ "$(uname)" != "Darwin" ]; then
  USER_OPT="--user $HOST_UID:$HOST_GID --group-add $(stat -c %g /var/run/docker.sock)"
fi

# Resolve curations path to absolute (if set)
if [ -n "${OLS4_CURATIONS_PATH:-}" ]; then
  OLS4_CURATIONS_PATH="$OLS_HOME/$OLS4_CURATIONS_PATH"
fi

docker run \
  $USER_OPT \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v "$OLS_HOME":"$OLS_HOME" \
  -v "$OLS_EMBEDDINGS_PATH":"$OLS_EMBEDDINGS_PATH" \
  -e OLS_HOME="$OLS_HOME" \
  -e OLS_OUT_DIR="$OUT_DIR" \
  -e OLS4_CONFIG="$OLS4_CONFIG" \
  -e OLS4_DATALOAD_ARGS="${OLS4_DATALOAD_ARGS:-}" \
  -e OLS_EMBEDDINGS_PATH="$OLS_EMBEDDINGS_PATH" \
  -e OLS_EMBEDDINGS_CONFIG="${OLS_EMBEDDINGS_CONFIG:-}" \
  -e OLS_EMBEDDINGS_PREV="${OLS_EMBEDDINGS_PREV:-}" \
  -e OLS4_DATALOAD_IMAGE="$OLS4_DATALOAD_IMAGE" \
  -e OLS4_EMBED_IMAGE="$OLS4_EMBED_IMAGE" \
  -e NXF_USRMAP="${HOST_UID}" \
  -e HOST_UID="${HOST_UID}" \
  -e HOST_GID="${HOST_GID}" \
  -e NXF_WORK="$TMP_DIR/work" \
  -e NXF_HOME="$TMP_DIR/NXF_HOME" \
  -e NXF_TEMP="$TMP_DIR/NXF_TEMP" \
  -e NXF_CACHE_DIR="$TMP_DIR/NXF_CACHE_DIR" \
  -e OLS4_CURATIONS_PATH="${OLS4_CURATIONS_PATH:-}" \
  ghcr.io/ebispot/ols4-nextflow:dev \
  bash -c "cd \"$OLS_HOME\" && nextflow run \"$OLS_HOME/dataload/nextflow/ols_dataload.nf\" \
    -c \"$OLS_NF_CONFIG\" -resume"


