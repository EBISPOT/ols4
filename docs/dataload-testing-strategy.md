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

## Later slices

Rust tests will exercise manifest creation and linking with small ontology
JSON inputs. PostgreSQL tests will load real `.pgbin` output into disposable
PostgreSQL 17 with pgvector and query the stored rows. A small Nextflow test
will run two successful ontologies alongside one intentionally failing
ontology and check that the successful outputs are unaffected.

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
