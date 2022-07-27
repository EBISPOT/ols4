Work in progress

Version 4 of the EMBL-EBI Ontology Lookup Service (OLS), featuring:

* Much faster dataload (hours instead of days)
* Modular dataload pipeline with decoupled, individually testable stages
* The latest Neo4j and Solr (no embedded databases, no MongoDB)
* React frontend
* Backwards compatibility with the OLS3 API

This repository contains both the dataloader (`dataload` directory) and the API/webapp server (`server` directory).

