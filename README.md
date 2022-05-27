Converts ontologies represented in OWL RDF/XML to a CSV format ready for use with `neo4j-admin import`. Designed to load large numbers of ontologies (e.g. all of OBO foundry) very quickly.


# Implementation

The conversion has two stages:

1. Convert the OWL RDF/XML to an intermediate JSON representation, in which imports have been resolved and any reification (`owl:Axiom`) has been folded into the edges
2. Convert the JSON representation into three CSV files per ontology: one with a single row for the Ontology node, one for all of the OwlClass nodes, and one for all of the edges (created for properties where the value is the URI of another Class).

# Usage

TODO

