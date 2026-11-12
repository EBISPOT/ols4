# Mixed tree reachability fixture

`generated.json` is unedited RDF2JSON output from `mixed.ttl` and `config.json`.
Regenerate from the repository root with a built RDF2JSON jar (Java 21 or later):

```sh
java -jar dataload/rdf2json/target/rdf2json-1.0-SNAPSHOT.jar \
  --config backend/src/test/resources/tree-reachability/config.json \
  --ontologyIds mixed \
  --output backend/src/test/resources/tree-reachability/generated.json \
  --basePath backend/src/test/resources/tree-reachability \
  --loadLocalFiles --noDates
```

The fixture proves `P <-hierarchical- C <-type- I`, where I belongs to two
subsets and P is the preferred root. L extends this with repeated alternating
edges through K, J and I. X/Y form a cycle reached by Z. Subset annotation
properties allow the same generated ontology to be browsed in the real UI.

`MixedTreeReachabilityIT` loads the generated ancestry/subset values and gzip
entity JSON into the production PostgreSQL schema. It also adds a foreign copy
of C to catch ontology leakage. It does not exercise the Rust loader.
