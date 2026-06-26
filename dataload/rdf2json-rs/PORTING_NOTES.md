# rdf2json (Rust) — design notes

`rdf2json` converts OWL/RDF ontologies into OLS's intermediate JSON. This crate
is a **faithful Rust port** of the Java `uk.ac.ebi.rdf2json.RDF2JSON` tool,
written for performance (the reason for the port) while preserving the exact
output contract the rest of the OLS dataload pipeline depends on.

## What it does

1. Reads each configured ontology's RDF (RDF/XML, Turtle, …) as **triples** via
   `oxrdfio` (`graph.rs`) and builds a node-per-subject graph (`model.rs`),
   resolving `owl:imports` and recording the defining vs imported origin.
2. Runs the ~22 **annotators** (`annotators.rs`) that derive OLS fields
   (`label`, `definition`, `synonym`, `directParent`, `hierarchicalParent`,
   ancestors, hierarchy flags/metrics, `shortForm`/`curie`, `relatedTo`,
   `isObsolete`, preferred roots, reification, …) — a 1:1 port of the Java
   annotators, run in the same order.
3. Streams the output JSON via `serde_json` (`writer.rs`): the
   classes/properties/individuals arrays are emitted one entity at a time so
   memory stays bounded even for very large ontologies.
4. Implements `--mergeOutputWith` (`merge.rs`): the incremental-reload safety net
   that carries forward a previous run's ontology when the current run fails to
   (re)index it, marking it `is_fallback` (streamed via `struson`).

The output is **byte-content-equivalent** to the Java tool: it matches 99/100
testcase fixtures (only `gitIssue502`, which needs network to fetch its source,
differs). It is, deliberately, a triple-level reader with no OWL axiom model —
no `horned-owl` dependency.

## Module map

| file | role |
|------|------|
| `main.rs` | CLI (`--config/--output/--ontologyIds/--basePath/--noDates/--loadLocalFiles/--downloadedPath/--mergeOutputWith`), per-ontology loop |
| `config.rs` | config JSON parsing + merge + `ConfigExt` helpers |
| `fetch.rs` | input acquisition (local / predownloaded / http / `file://`), gzip, RDF format detection |
| `graph.rs` | triple ingestion → node graph; imports; reification matching; RDF-list/ancestor helpers |
| `model.rs` | `OntologyNode` / `PropertySet` / `PropertyValue` / `NodeType` (port of the Java model) |
| `annotators.rs` | the derived-field annotators |
| `writer.rs` | streaming `serde_json` output |
| `merge.rs` | `--mergeOutputWith` streaming merge |
| `status.rs` | `*.status.json` (SUCCESS/FALLBACK/FAILED/SKIPPED) |
| `validate_language.rs` | BCP-47-ish language-tag validation (port) |

## Dependencies

`oxrdf`/`oxrdfio` (triple reader), `serde`/`serde_json` (output), `struson` +
`ols_shared` (streaming merge), `clap`, `flate2`, `ureq`, `regex`. No
`horned-owl` / OWL axiom model.

## Roadmap (later PRs)

This PR replaces the Java rdf2json with this Rust port. Planned follow-ups:

1. **owlmake normalisation step** — run `om` (EBISPOT/owlmake, horned-owl based)
   ahead of rdf2json in the pipeline: `om merge -c true` to resolve+merge the
   import closure (incl. non-RDF imports) and `om information-content` to inject
   IC scores. rdf2json then reads the normalised RDF faithfully.
2. **Simplify rdf2json** once owlmake handles imports: drop the in-crate import
   resolution and the per-entity `imported` flag.
3. **Remove Java/Maven from the dataload image** by porting the remaining Java
   utilities (`merge_configs`, `reporting`, `extras/json2sssom`) to Rust.
