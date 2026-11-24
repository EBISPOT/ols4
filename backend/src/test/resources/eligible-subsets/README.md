# Eligible subset fixture

`generated.json` is unedited RDF2JSON output from the included TTL/config:

```sh
java -jar dataload/rdf2json/target/rdf2json-1.0-SNAPSHOT.jar \
  --config backend/src/test/resources/eligible-subsets/config.json \
  --ontologyIds eligible --output backend/src/test/resources/eligible-subsets/generated.json \
  --basePath backend/src/test/resources/eligible-subsets --loadLocalFiles --noDates
```

Preferred root A has only an obsolete native member and an imported active
member in S. Root B has one active native individual in both S and S2. Top is
an ordinary parent, so the same fixture exercises preferred roots and child
queries. With imports and obsolete members hidden, only B remains, counted once.

RDF2JSON precedes the linker and does not supply `isDefiningOntology`.
`EligibleSubsetMembersIT` explicitly models that later-stage flag as true for
this fixture's `https://example.org/eligible#` entities, false for the external
member. The local browser fixture uses that same explicit mapping, production
schema columns and gzip JSON. This is not execution of the linker, Rust writer,
or production loader; full pipeline verification is tracked separately.
