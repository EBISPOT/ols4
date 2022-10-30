#!/usr/bin/env bash
cd $OLS4_HOME
$OLS4_HOME/test_dataload.sh
cd $OLS4_HOME/dataload

rm -rf $NEO4J_HOME/data/*
$OLS4_HOME/dataload/load_into_neo4j.sh $NEO4J_HOME $OLS4_HOME/testcases_output/owl2-primer

rm -rf $SOLR_HOME/server/solr/ols4/*
cp -r $OLS4_HOME/dataload/solr_config/ols4 $SOLR_HOME/server/solr/
$OLS4_HOME/dataload/load_into_solr.sh $SOLR_HOME $OLS4_HOME/testcases_output/owl2-primer

$NEO4J_HOME/bin/neo4j start

$SOLR_HOME/bin/solr start

 ols.solr.host=http://localhost:8983

java -jar -Dols.solr.host=http://localhost:8983 $OLS4_HOME/backend/target/ols-web-4.0.0-SNAPSHOT.jar

cd $OLS4_HOME/frontend
yarn start