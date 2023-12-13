---
name: 'Add a new ontology to the EBI OLS instance '
about: Describes a new ontology to be added to the EBI instance of OLS
title: ''
labels: new ontology
assignees: ''

---

Please note the following:
1. Only biologic and/or bioinformatics related ontologies may be added to the EBI OLS instance. If your ontology is not biologic and/or bioinformatics related you can still [host](https://github.com/EBISPOT/ols4#deploying-ols4) your own instance of OLS with any ontologies you require. 
2. Currently OLS only supports ontologies in XML/RDF format.

**Steps to add your ontology**
   1. Download and fill out this [spreadsheet](https://github.com/EBISPOT/ols4/blob/dev/New%20OLS%20ontology%20request.xlsx).
   1. In filling out the spreadsheet the 2 most important fields are:
      - preferredPrefix: This should be a string of lowercase character at least 5 characters long. This string must be unique on OLS.
      - ontology_purl: This is the permanent publicly accessible URL from where OLS will load your. This is also the URL that OLS checks on a frequent basis to check for new releases of your ontology.
