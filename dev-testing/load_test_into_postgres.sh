#!/usr/bin/env bash

if [ $# == 0 ]; then
    echo "Usage: $0 <tsvdir>"
    exit 1
fi

TSV_DIR=$1

# Find all embedding parquet files
PARQUET_FILES=$(find "$TSV_DIR" -name '*.parquet' 2>/dev/null | tr '\n' ' ')

# Run the postgres loading script
python3 $OLS4_HOME/dataload/load_into_postgres.py ./postgres "$TSV_DIR" $PARQUET_FILES
