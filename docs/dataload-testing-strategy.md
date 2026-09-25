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

## Later slices

Rust tests will exercise manifest creation and linking with small ontology
JSON inputs. PostgreSQL tests will load real `.pgbin` output into disposable
PostgreSQL 17 with pgvector and query the stored rows. A small Nextflow test
will run two successful ontologies alongside one intentionally failing
ontology and check that the successful outputs are unaffected.

The existing `test_dataload.sh` golden comparison and `test_api.sh` full run
continue to check the assembled system. The module tests give a faster, more
specific failure when a dataload transformation changes.
