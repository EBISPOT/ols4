#!/bin/bash

export OLS_CONFIG="/home/james/ols4/dataload/nextflow/test.json"
export OLS_OUT_DIR="/home/james/ols4/dataload/nextflow/testout"
export OLS_DATALOAD_HOME="/home/james/ols4/dataload"
export OLS_EMBEDDINGS_PATH="/home/james/ols4"

nextflow run ols_dataload.nf -c local_nextflow.config 
