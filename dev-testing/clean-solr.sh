#!/usr/bin/env bash

rm -rf $SOLR_HOME/server/solr/ols4_entities/*
cp -r $OLS4_HOME/dataload/solr_config/ols4_entities $SOLR_HOME/server/solr/

rm -rf $SOLR_HOME/server/solr/ols4_autocomplete/*
cp -r $OLS4_HOME/dataload/solr_config/ols4_autocomplete $SOLR_HOME/server/solr/

