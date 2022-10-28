#!/usr/bin/env bash
export NEO4J_HOME=~/tools/neo4j/neo4j-community-4.4.7-unix/neo4j-community-4.4.7
export SOLR_HOME=~/tools/solr/solr-9.0.0

$NEO4J_HOME/bin/neo4j stop
$SOLR_HOME/bin/solr stop
