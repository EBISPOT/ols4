#!/usr/bin/env bash

rm -rf $SOLR_HOME/server/solr/ols4/*
cp -r $OLS4_HOME/dataload/solr_config/ols4 $SOLR_HOME/server/solr/