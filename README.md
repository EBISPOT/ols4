Converts ontologies represented in OWL RDF/XML to a CSV format ready for use with `neo4j-admin import`. Designed to load large numbers of ontologies (e.g. all of OBO foundry) very quickly.

A work in progress reimplementation of the OLS API using the output of this tool as its backing database is located at [EBISPOT/ols4-web](https://github.com/EBISPOT/ols4-web).


# Implementation

The conversion has two stages:

1. Convert the OWL RDF/XML to an intermediate JSON representation, in which imports have been resolved and any reification (`owl:Axiom`) has been folded into the edges
2. Convert the JSON representation into five CSV files per ontology. If your ontology ID was `efo`, you would get:
      * A file called `efo_ontologies.csv` containing a single row for the ontology itself
      * A file called `efo_classes.csv` containing a row for each for each class in the ontology
      * A file called `efo_properties.csv` containing a row for each for each property in the ontology
      * A file called `efo_edges.csv` containing a row for each for each edge (property that points to another class)
      * A file called `efo_individuals.csv` containing a row for each for each individual in the ontology

# Usage

TODO

