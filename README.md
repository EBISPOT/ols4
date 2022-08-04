
<a href="https://github.com/EBISPOT/ols4/actions/workflows/test.yml"><img src="https://github.com/EBISPOT/ols4/actions/workflows/test.yml/badge.svg"/></a>

Work in progress

Version 4 of the EMBL-EBI Ontology Lookup Service (OLS), featuring:

* Much faster dataload (hours instead of days)
* Modular dataload pipeline with decoupled, individually testable stages
* A lossless data representation: everything in the OWL is preserved in the databases
* Support for the latest Neo4j and Solr (no embedded databases, no MongoDB)
* React frontend
* Backwards compatibility with the OLS3 API

This repository contains both the dataloader (`dataload` directory) and the API/webapp server (`server` directory).

# Updating `testcases_expected_output`

If you change something that results in the test output changing (e.g. adding new tests, changing what the output looks like), the CI on this repo will fail.

To fix this, you need to replace the `testcases_expected_output` folder with the new expected output. **You should do this in the same commit as your code/test changes because then we can track exactly what changed in the output.**

First make sure all the JARs are up to date:

    mvn clean package

Then run the script:

    ./test.sh

Remove the existing expected output:

    rm -rf testcases_expected_output

Copy your new output to `testcases_expected_output`:

    cp -r testcases_output testcases_expected_output

You can now add it to your commit:

    git add -A testcases_expected_output






    




