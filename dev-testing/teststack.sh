#!/usr/bin/env bash

# Todo: Allow using either a configurl or a directory. When a configurl is provided, only that config is tested against.
# When a directory is provided, all config from that directory is picked up.

if [ $# == 0 ]; then
    echo "Usage: $0 <rel_configurl> <rel_outdir>"
    exit 1
fi

CONFIG_URL=$1
OUTDIR=$2
BASENAME=$(basename "$CONFIG_URL" .json)

if [ -d "$OUTDIR" ]; then
    echo "$OUTDIR already exists and will now be cleaned."
    rm -Rf $OUTDIR/*
else
    echo "$OUTDIR does not exist and will now be created."
    mkdir $OUTDIR
fi
mkdir $OUTDIR/$BASENAME

RELATIVE_OUTDIR=$OUTDIR/$BASENAME
mkdir $RELATIVE_OUTDIR

ABSOLUTE_OUTDIR=$(realpath -s $RELATIVE_OUTDIR)
echo "absolute_outdir="$ABSOLUTE_OUTDIR

$OLS4_HOME/dataload/create_datafiles.sh $CONFIG_URL $ABSOLUTE_OUTDIR


$OLS4_HOME/dev-testing/clean-neo4j.sh
$OLS4_HOME/dev-testing/load_test_into_neo4j.sh $ABSOLUTE_OUTDIR
$OLS4_HOME/dev-testing/start-neo4j.sh

$OLS4_HOME/dev-testing/clean-solr.sh
$OLS4_HOME/dev-testing/load_test_into_solr.sh $ABSOLUTE_OUTDIR
