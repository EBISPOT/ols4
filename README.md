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

Start with a config JSON file that lists the ontologies you want to load. You can get the OBO config into a file called `foundry.json` like so (make sure you have yq installed):

    curl "https://raw.githubusercontent.com/OBOFoundry/OBOFoundry.github.io/master/_config.yml" \
        | ./yq eval -j - > foundry.json
        
        
## Step 1: OWL to JSON

Use owl2json to download all the OWL files, resolve imports, and export JSON files:

     java -jar owl2json/target/owl2json-1.0-SNAPSHOT.jar --config file://$(pwd)/foundry.json --output foundry_out.json
     
Now (after about 15 min) you should have a huge file called `foundry_out.json` that contains not only the original config for each ontology loaded from `foundry.json`, but also the ontologies themselves represented in an intermediate JSON format! (Note: the intermediate JSON format is a non-standardised application format totally specific to this tool and is subject to change.)

## Step 2: JSON to CSV *for Neo4j*

You can now convert this huge JSON file to a CSV file ready for Neo4j, using json2neo:

    rm -rf output_csv && mkdir output_csv
    java -jar json2neo/target/json2neo-1.0-SNAPSHOT.jar --input foundry_out.json --outDir output_csv

## Step 3: CSV to Neo4j

Now (after 5-10 mins) you should have a directory full of CSV files. These files are formatted especially for Neo4j. You can load them using `neo4j-admin import`, but you'll need to provide the filename of every single CSV file on the command line, which is boring, so included in this repo is a script called `make_csv_import_cmd.sh` that generates the command line for you.

    neo4j-admin import \
	    --ignore-empty-strings=true \
	    --legacy-style-quoting=false \
	    --multiline-fields=true \
	    --array-delimiter="|" \
	    --database=neo4j \
	    $(./make_csv_import_cmd.sh)

Now you should have a Neo4j database ready to start!

## Step 4: JSON to JSON *for Solr*

Similar to how the Neo4j CSV was generated, you can also generate JSON files ready for uploading to SOLR using neo2solr.

    java -jar json2solr/target/json2solr-1.0-SNAPSHOT.jar --input foundry_out.json --outDir output_csv


<h1>Vocabulary</h1>

owl2neo maps the OWL RDF data model losslessly to Neo4j graph nodes. _**In addition to**_ this lossless mapping, it also defines some additional properties in its own namespace to aid querying.

Currently, these properties are as follows for `Ontology` nodes:

* `https://github.com/EBISPOT/owl2neo#numberOfClasses`
* `https://github.com/EBISPOT/owl2neo#numberOfProperties`
* `https://github.com/EBISPOT/owl2neo#numberOfIndividuals`

And as follows for `OwlClass`, `OwlProperty`, and `OwlIndividual` nodes:

* `https://github.com/EBISPOT/owl2neo#shortForm`
* `https://github.com/EBISPOT/owl2neo#relatedTo`
* `https://github.com/EBISPOT/owl2neo#definition`
* `https://github.com/EBISPOT/owl2neo#synonym`





