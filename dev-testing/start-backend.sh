#!/usr/bin/env bash
java -jar -Dols.solr.host=http://localhost:8983 $OLS4_HOME/backend/target/ols-web-4.0.0-SNAPSHOT.jar &