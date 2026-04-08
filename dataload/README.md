Converts ontologies represented in OWL RDF/XML to a PostgreSQL database.

# Usage

Start with a config JSON file that lists the ontologies you want to load. You can get the OBO config into a file called `foundry.json` like so (make sure you have yq installed):

    curl "https://raw.githubusercontent.com/OBOFoundry/OBOFoundry.github.io/master/_config.yml" \
        | yq eval -j - > foundry.json
        
        
## Step 1: OWL to JSON

Use rdf2json to download all the OWL files, resolve imports, and export JSON files:

     java -jar rdf2json/target/rdf2json-1.0-SNAPSHOT.jar --config file://$(pwd)/foundry.json --output foundry_out.json
     
Now (after about 15 min) you should have a huge file called `foundry_out.json` that contains not only the original config for each ontology loaded from `foundry.json`, but also the ontologies themselves represented in an intermediate JSON format! (Note: the intermediate JSON format is a non-standardised application format totally specific to this tool and is subject to change.)

## Step 2: JSON to TSV *for PostgreSQL*

You can now convert this huge JSON file to TSV files ready for PostgreSQL, using ols_json2postgres:

    rm -rf output_tsv && mkdir output_tsv
    ols_json2postgres --input foundry_out_flat.json --outDir output_tsv --manifest linker_manifest.json

## Step 3: TSV to PostgreSQL

Now (after 5-10 mins) you should have a directory full of TSV files. These files are formatted for bulk loading into PostgreSQL via COPY. Use the `load_into_postgres.sh` script to initialize a PostgreSQL instance and load the data:

    ./load_into_postgres.sh ./postgres_out ./output_tsv

Now you should have a PostgreSQL database ready to start!

## Loading Reports

Each rdf2json process writes a `.status.json` file alongside its output JSON file. These status files can be collected and processed by the reporting service to generate a consolidated loading report and optionally send notifications.

See the [reporting module README](reporting/README.md) for more details on how the reporting system works.

