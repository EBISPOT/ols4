#!/usr/bin/env bash
OLS_SOLR_HOST=${OLS_SOLR_HOST:-http://localhost:8983}
OLS_NEO4J_HOST=${OLS_NEO4J_HOST:-bolt://localhost:7687}
java -jar -Dols.solr.host=$OLS_SOLR_HOST -Dols.neo4j.host=$OLS_NEO4J_HOST $OLS4_HOME/backend/target/ols4-backend-4.0.0-SNAPSHOT.jar
