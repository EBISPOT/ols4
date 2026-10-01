# Dataload testing strategy

The dataload is a sequence of executable modules connected by Nextflow. The
files passed between modules are their interfaces: config JSON, ontology JSON,
status JSON, linked JSON, PostgreSQL COPY files, and reports. Tests should
exercise those interfaces with small local inputs and check the data that a
downstream module can observe.

## First slice: config merge, RDF2JSON, and reporting

The first contract tests invoke the packaged Java executables with local files.
They use temporary output directories and need neither Nextflow nor a database.
The same Maven reactor that builds the release JARs runs the tests at `verify`.

With Java 21 selected, run the first slice locally from the repository root:

```sh
mvn -B -ntp -f ols-shared/pom.xml install
mvn -B -ntp -f dataload/pom.xml verify
```

CI runs these commands in the dedicated `dataload-java-contract` job and
uploads the Surefire and Failsafe reports. The tests named `*IT` run after the
JARs are packaged so they exercise the executables used by Nextflow.

| Input situation | RDF2JSON status | Ontology JSON | Downstream meaning |
| --- | --- | --- | --- |
| Valid local ontology | `SUCCESS` | Contains the newly parsed ontology | Its data can be linked and loaded |
| Invalid current ontology with a previous result for that ID | `FALLBACK` | Contains the previous ontology, marked as fallback | Previous data can be linked and loaded |
| Invalid current ontology without a previous result for that ID | `FAILED_NO_FALLBACK` | Does not contain that ontology | Other ontologies can continue |
| Ontology marked `is_obsolete: true` | `SKIPPED` | Does not contain that ontology | Other ontologies can continue |

These four outcomes are agreed behaviour. `--ontologyIds` selects which
ontologies RDF2JSON attempts; omitting an ID from that selection is not a
`SKIPPED` outcome for a configured ontology. A prior result that lacks the
failing ontology does not count as a fallback.

Config merge tests cover later-file overrides for the same ontology ID and the
default filter property needed by the PostgreSQL schema. Reporting tests feed
all four status types to the reporting executable and check its file output.
Notifications remain disabled in tests.

Each test has a small, explicit fixture and checks fields with a known expected
value. Dates, temporary paths, and logging are not part of the contract.
Generated outputs from a large dataload run are useful regression evidence,
but require review before any individual field becomes an asserted contract.

## RDF2JSON semantic contract

The next slice keeps the same executable boundary: a packaged RDF2JSON JAR
reads a local ontology and config, then writes ontology and status JSON. Small
fixtures make the intended transformation visible in the test itself. Assertions
select entities by IRI and check specific fields rather than freezing a whole
JSON document, so harmless ordering or unrelated metadata changes do not make
the tests fail.

| Contract group | Directly checked behaviour |
| --- | --- |
| Identity and serialization | Ontology ID/IRI/version, entity categories, short form/CURIE, typed values, counts, languages, and entity-to-ontology metadata |
| Annotations | Multilingual labels and short-form fallback, configured label and definition predicates, default/custom synonyms, searchable values, preferred roots, and obsolete flags |
| OWL evidence | Reified annotation evidence, OBO synonym-type label, negative property assertion, equivalent/disjoint classes, and inverse properties |
| Hierarchy | Direct versus transitive ancestry, restriction-based hierarchical parents, related links, child flags, and descendant counts |
| Input and punning | Turtle and RDF/XML local input; a shared class/individual IRI survives in both output collections |

These tests run in the existing `dataload-java-contract` CI job alongside the
first-slice status tests. They do not require Nextflow, Docker, or PostgreSQL.
Each test runs one ontology, matching the current Nextflow per-ontology
invocation. A failing ontology therefore does not block assertions about a
different ontology. Whole-pipeline golden comparisons remain a separate safety
net, not the source of truth for these field-level expectations.

This is broad coverage of the current RDF2JSON transformation stages, not a
claim that every RDF/OWL construct or combination is specified. In particular,
large anonymous class-expression graphs, multi-parent cycles, and unusual
import topologies still rely on the existing golden suite until each has an
agreed small semantic contract. New regressions should become a small fixture
and field assertion here before refreshing a broad golden output.

## Downstream slices

The contracts below exercise manifest creation and linking with tiny ontology
JSON inputs, real `.pgbin` loading into disposable PostgreSQL 17 with pgvector,
and per-ontology Nextflow failure isolation. Each boundary has an executable
fixture and explicit expected fields or outcomes.

The existing `test_dataload.sh` golden comparison and `test_api.sh` full run
continue to check the assembled system. The module tests give a faster, more
specific failure when a dataload transformation changes.

## Rust manifest and linker executable contracts

Run `cargo test --locked --manifest-path dataload/Cargo.toml -p ols_create_manifest -p ols_link`.
The dedicated CI job runs helper tests and Cargo integration tests invoking both
real binaries. Manifest assertions cover multiple files, repeated inputs, shared
entity provenance, canonical ownership/CURIE, import/export relationships,
class/property/individual inventories, multilingual fields and edge evidence.
Linker tests supply an independently authored manifest and assert known IRI and
CURIE resolution, property-name links, unresolved/self/OWL exclusions, canonical
metadata, copied evidence, and CLI failure for an invalid manifest.

Bioregistry is served by a loopback HTTP fixture; ORCID uses its existing local
name fixture. `OLS_TEST_DB_XREFS` supplies a local GO db-xrefs YAML file in tests;
normal runs retain the upstream default. External URL links do not become
ontology `linksTo` relationships. SSSOM curation and large-scale performance
remain outside this component slice. RDF2JSON cyclic imports and remaining
annotator gaps, including the excluded RelatedAnnotator.hasValue branch, remain
deferred as documented in `rdf2json-annotator-coverage.md`.

## PostgreSQL loading executable contracts

`docker build -f dataload/tests/Dockerfile.postgres-contract -t ols-dataload-pg-contract .`
then `docker run --rm --shm-size=512m ols-dataload-pg-contract` builds the real
JSON2Postgres binary from this checkout and runs the Python loader CLI inside
disposable PostgreSQL 17 with pgvector. Tests restart the packaged cluster and
query persisted rows, gzip JSON, text arrays, booleans, entity categories,
parent/ancestor/related arrays, dynamic filters, generated search, autosuggest
deduplication, pgvector dimensions and label/curation embedding rows, indexes,
and PCA/text-tagger artifacts. No-embedding and empty-collection loading is
also covered. Corrupt COPY input must fail without packaging and stop the
server; malformed JSON must fail conversion. This is distinct from the prior
schema SQL string assertions and Rust binary-writer helper tests.

The dedicated CI job precedes the assembled API safety check. Full-volume
loading, every embedding model combination and external PostgreSQL deployment
remain separate concerns; these fixtures specify the local loading boundary.

## Nextflow per-ontology isolation contract

With Nextflow 24.10.5 and a dataload image built from this checkout, run
`python3 dataload/tests/nextflow_isolation.py --image ols4-dataload:local`.
The small workflow imports the production `rdf2json` and `json2postgres`
processes. It compares A+B against A+B alongside a missing-source ontology
(`FAILED_NO_FALLBACK`) and an injected task exit 42. Trace assertions distinguish
a semantic outcome from a failed task; neither produces downstream COPY files.
A+B must remain `SUCCESS` with byte-identical JSON and binary COPY outputs.
This preserves production `errorStrategy 'ignore'` and release isolation.

CI runs the fixture in Build & Test API immediately after building its local
dataload image, then runs the existing assembled dataload/API safety checks.
The fixture checks process/channel isolation and conversion to loadable files;
the separate PostgreSQL contract checks actual database loading. It does not
claim full production workflow, linking, fallback orchestration or release
promotion coverage. No global failure gate is added for individual ontologies.
