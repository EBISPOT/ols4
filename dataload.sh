#!/bin/bash

if ! command -v docker &> /dev/null
then
    echo "Docker could not be found. Please install Docker to proceed."
    exit 1
fi

if [ -z "$OLS4_CONFIG" ]; then
  echo "Error: OLS4_CONFIG environment variable is not set."
  exit 1
fi

# These folders are mounted to have the same path in the Docker containers as they do on the host.
# This includes: 
#   - The nextflow container that runs nextflow (which we start below)
#   - The containers nextflow starts to run processes, configured in the nextflow config files
#
OLS_HOME=$(dirname "$(readlink -f "$0")")
if [ -z "$OLS_EMBEDDINGS_PATH" ]; then
  OLS_EMBEDDINGS_PATH=$OLS_HOME/embeddings
fi

if [ -z "$OLS_NF_CONFIG" ]; then
  OLS_NF_CONFIG=$OLS_HOME/dataload/nextflow/local_nextflow.config
fi


TMP_DIR=$OLS_HOME/tmp
OUT_DIR=$OLS_HOME/out

mkdir -p $TMP_DIR/work $TMP_DIR/NXF_HOME $TMP_DIR/NXF_TEMP $TMP_DIR/NXF_CACHE_DIR

DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)

# Ensure nested Docker containers (spawned by Nextflow) run with the same UID/GID as the host user
# to avoid permission issues when writing to the bind-mounted work directory on GitHub Actions.
HOST_UID=$(id -u)
HOST_GID=$(id -g)
NXF_DOCKER_OPTS_VAL="-u ${HOST_UID}:${HOST_GID}"

docker run \
  --user "${HOST_UID}":"${HOST_GID}" \
  --group-add $DOCKER_GID \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v $OLS_HOME:$OLS_HOME \
  -v $OLS_EMBEDDINGS_PATH:$OLS_EMBEDDINGS_PATH \
  -e OLS_HOME=$OLS_HOME \
  -e OLS_OUT_DIR=$OUT_DIR \
  -e OLS4_CONFIG=$OLS4_CONFIG \
  -e OLS4_DATALOAD_ARGS="$OLS4_DATALOAD_ARGS" \
  -e OLS_EMBEDDINGS_PATH=$OLS_EMBEDDINGS_PATH \
  -e NXF_USRMAP=${HOST_UID} \
  -e NXF_DOCKER_OPTS="$NXF_DOCKER_OPTS_VAL" \
  -e NXF_WORK=$TMP_DIR/work \
  -e NXF_HOME=$TMP_DIR/NXF_HOME\
  -e NXF_TEMP=$TMP_DIR/NXF_TEMP \
  -e NXF_CACHE_DIR=$TMP_DIR/NXF_CACHE_DIR \
  ghcr.io/ebispot/ols4-nextflow:dev \
  bash -c "cd $OLS_HOME && nextflow run $OLS_HOME/dataload/nextflow/ols_dataload.nf \
    -c $OLS_NF_CONFIG -resume"


