# RDF2JSON annotator coverage map

This map covers the 22 annotators called, in order, by `OntologyGraph` after it
has parsed the ontology and its imports. It is a map of **observable RDF2JSON
output**, not a claim that calling every annotator once proves all of its
branches. The baseline below is the packaged-JAR contract suite in
`RDF2JSONAnnotationsIT`, `RDF2JSONRelationsIT`, `RDF2JSONFormatsIT`,
`RDF2JSONImportsIT`, and `RDF2JSONVariantsIT`, plus
`DefinitionAnnotatorTest`, `ShortFormAndLabelAnnotatorTest`,
`RelatedAnnotatorTest`, and `SearchableAnnotationValuesAnnotatorTest`.
`RDF2JSONContractIT` separately covers per-ontology
`SUCCESS`, `FALLBACK`, `FAILED_NO_FALLBACK`, and `SKIPPED` outcomes.

An "asserted" field has an explicit expected value in those tests. "Partial"
means that another branch of the same annotator remains unasserted; it does
not imply the current behavior is wrong. The priority column suggests the
next *small local fixture* or direct unit test, not a change to production
semantics. Use targeted JSON field assertions rather than full-file snapshots.

| Annotator (`OntologyGraph` order) | Observable output | Asserted baseline | Important unasserted branch; next fixture/test |
| --- | --- | --- | --- |
| `SearchableAnnotationValuesAnnotator` | `searchableAnnotationValues` literal list | Custom literal in `RDF2JSONAnnotationsIT`; Exact nonstandard literal set, standard namespace/URI exclusion in `RDF2JSONVariantsIT`; runtime timestamps and non-entity exclusion in unit tests | No priority gap in the listed branches; imported boolean is included by current predicate rules. |
| `InverseOfAnnotator` | Reverse `owl:inverseOf` on a property | One-way pair in `RDF2JSONRelationsIT`; Missing target and already-bidirectional pair without duplicates in `RDF2JSONVariantsIT` | No priority gap in the listed branches. |
| `NegativePropertyAssertionAnnotator` | `negativePropertyAssertion+<property IRI>` on source individual | `owl:targetIndividual` in `RDF2JSONAnnotationsIT`; Numeric/language-tagged targetValue; missing, non-URI, and unresolved source/property/target guards in `RDF2JSONVariantsIT` | Simultaneous targetIndividual/targetValue precedence remains unasserted (invalid OWL input). |
| `OboSynonymTypeNameAnnotator` | `oboSynonymTypeName` within reified synonym axiom evidence | Named synonym type in `RDF2JSONAnnotationsIT`; Unresolved and unlabelled type retains IRI evidence without a synthetic name in `RDF2JSONVariantsIT` | Multiple labels or nonliteral labels remain unasserted. |
| `DirectParentsAnnotator` | `directParent` for classes, properties, and individuals | Named class chain and imported class in earlier tests; property `subPropertyOf` and individual `rdf:type` in `RDF2JSONRelationsIT`; Unresolved parent/type, NamedIndividual exclusion, and cyclic closure in `RDF2JSONVariantsIT` | Direct subclass self-loop semantics remain unasserted. |
| `RelatedAnnotator` | `relatedTo` for selected anonymous subclass expressions/restrictions | Named `someValuesFrom` and packaged-JSON `intersectionOf` in `RDF2JSONRelationsIT`; `oneOf`, `intersectionOf`, and both as `someValuesFrom` fillers in `RelatedAnnotatorTest`; Self/unresolved named someValuesFrom filler exclusion and unlisted property relation in `RDF2JSONVariantsIT` | Duplicate anonymous restriction evidence and unresolved list members remain unasserted. Entire hasValue branch remains excluded. |
| `HierarchicalParentsAnnotator` | `hierarchicalParent` and edge axiom `childRelationToParent`/`parentRelationToChild` | Named class and configured restriction edges; configured individual assertion and inverse edge metadata in `RDF2JSONRelationsIT`; Self/unresolved/literal individual edges, absent inverse metadata, unlisted restriction property, and property hierarchy distinction in `RDF2JSONVariantsIT` | Direct subclass self-loop semantics remain unasserted. |
| `AncestorsAnnotator` | `directAncestor`, `hierarchicalAncestor` closure | Named class chain, multi-parent DAG, instance direct ancestry, and individual hierarchical ancestry in `RDF2JSONRelationsIT`; Finite cyclic reachability and unresolved parents in `RDF2JSONVariantsIT` | Direct subclass self-loop semantics remain unasserted. |
| `HierarchyMetricsAnnotator` | `numDescendants`, `numHierarchicalDescendants` | Class chain, multi-parent DAG, property and individual descendants in `RDF2JSONRelationsIT`; Cycle counts and mixed direct/restriction overlapping paths in `RDF2JSONVariantsIT` | No priority gap in the listed branches. |
| `ShortFormAnnotator` | `shortForm`, `curie` | Base-URI/prefix example in `RDF2JSONAnnotationsIT`; custom regex with underscored prefix and multi-underscore numeric CURIE in `ShortFormAndLabelAnnotatorTest`; URN, absent base URI, invalid/unmatched/groupless patterns, nonnumeric underscored name, and absent/empty prefix fallback in unit tests | An unmatched regex falling back to an underscored default identifier still needs a separate CURIE contract; overlapping base-URI precedence is unresolved. |
| `DefinitionAnnotator` | `definition` collated from configured/default predicates | Configured output in `RDF2JSONAnnotationsIT`; configured/default/empty choices in `DefinitionAnnotatorTest`; Multiple default predicates, language/custom datatype retention, empty configuration and empty literal in `RDF2JSONVariantsIT` | No priority gap in the listed branches; configured/default choices already have unit coverage. |
| `SynonymAnnotator` | `synonym` and normalized per-predicate arrays | Custom and OBO exact values in `RDF2JSONAnnotationsIT`; All five defaults, multiple/single arrays, empty list retaining defaults, and empty literal in `RDF2JSONVariantsIT` | RDF-list-valued synonyms remain unasserted. |
| `ReifiedPropertyAnnotator` | Value object with `type: reification` and `axioms` evidence | Literal and URI targets, synonym-type axiom, and multiple axioms on one assertion in `RDF2JSONAnnotationsIT`; Unresolved/unlabelled synonym-type evidence in `RDF2JSONVariantsIT` | Malformed axiom input and repeated identical axiom evidence remain unasserted; no failure semantics frozen. |
| `OntologyMetadataAnnotator` | Entity `ontologyId`, `ontologyIri`, `ontologyPreferredPrefix` | Class identity fields in `RDF2JSONAnnotationsIT`; Absent/configured/empty prefix across class/property/individual and punning collection representation in `RDF2JSONVariantsIT` | No priority gap in the listed branches. |
| `HierarchyFlagsAnnotator` | `hasDirectParents/Children`, `hasHierarchicalParents/Children` | Class chain, property direct flags, and individual hierarchical flags in `RDF2JSONRelationsIT`; Explicit owl:Thing/TopObjectProperty exclusions in `RDF2JSONVariantsIT` | No priority gap in the listed branches. |
| `IsObsoleteAnnotator` | `isObsolete` boolean | `owl:deprecated true` and non-obsolete class in `RDF2JSONAnnotationsIT`; "1", case-insensitive true, false/zero/unset, obsolete-class parent, property and individual flags in `RDF2JSONVariantsIT` | Nonliteral owl:deprecated is invalid input and remains unasserted. |
| `LabelAnnotator` | `label` list with language tags and short-form fallback | English/French labels, French-only fallback, configured predicate in `RDF2JSONAnnotationsIT`; English suppression of fallback and configured override in `ShortFormAndLabelAnnotatorTest`; Multiple default predicates, language-less label, explicit empty predicates and empty literal in `RDF2JSONVariantsIT`; list-valued sources and nonliteral member filtering in unit tests | Deeper recursively nested lists are outside current flattening contract and remain unasserted. |
| `ConfigurablePropertyAnnotator` | `hierarchicalProperty`, `definitionProperty`, `synonymProperty` provenance | Definition/synonym predicates in `RDF2JSONAnnotationsIT`, definition choices in `DefinitionAnnotatorTest`, configured individual `hierarchicalProperty` in `RDF2JSONRelationsIT`; Default subclass/individual part-of provenance and empty hierarchy/definition/synonym config in `RDF2JSONVariantsIT` | No priority gap in the listed branches. |
| `PreferredRootsAnnotator` | Ontology `preferredRoot`; entity `isPreferredRoot` | Configured root and non-root in `RDF2JSONAnnotationsIT`; IAO/OLS/config union, duplicate removal, missing roots, literal exclusion and property flags in `RDF2JSONVariantsIT` | No priority gap in the listed branches. |
| `DisjointWithAnnotator` | Pairwise `owl:disjointWith`, `owl:propertyDisjointWith`, `owl:differentFrom` | `AllDisjointClasses`, `AllDisjointProperties`, and `AllDifferent` in `RDF2JSONRelationsIT`; Unresolved AllDifferent member is ignored in `RDF2JSONVariantsIT` | Malformed class/property lists remain unasserted; intended recovery semantics need a decision. |
| `HasIndividualsAnnotator` | `hasIndividuals: true` on class with instance | Class instance in `RDF2JSONAnnotationsIT`; No instance, unresolved type, and class/individual punning in `RDF2JSONVariantsIT` | No priority gap in the listed branches. |
| `EquivalenceAnnotator` | Reverse `owl:equivalentClass` or `owl:equivalentProperty` | One-way class and property pairs in `RDF2JSONRelationsIT`; Missing targets and already-bidirectional class/property assertions in `RDF2JSONVariantsIT` | No priority gap in the listed branches. |

## Boundaries and contract decisions

- The annotators run **after imports are parsed**. `RDF2JSONImportsIT` uses two
  local Turtle files through the packaged JAR and checks `SUCCESS`,
  `imported=true` for an import-only class, `imported=false` for primary and
  shared classes, the imported class's label and derived direct parent, and
  the shared class's label supplied by the import.
  `RDF2JSONVariantsIT` additionally asserts nested imports with a shared
  dependency, labels supplied by imports, primary/imported provenance,
  cross-file ancestors, and descendant deduplication. Cyclic imports remain
  outside the passing contract; see the confirmed issue below.
- `RelatedAnnotator` deliberately does not materialize `relatedFrom` in its
  output; the source comments say this avoids large responses and that reverse
  links are available via a separate paginated endpoint. The direct unit test
  asserts its absence for a `oneOf` case; do not add a populated `relatedFrom`
  expectation to this component contract.
- The `RelatedAnnotator` `hasValue` branch is deliberately outside this test
  effort at the user's request. Its observed relationship direction is not
  treated here as either an agreed contract or a confirmed defect.
- Empty/absent config can have different semantics by field. The definition
  config behavior is directly tested: an explicit empty list disables
  collation, while omission uses defaults. Packaged tests now also assert empty
  label lists using short-form fallback,
  empty synonym lists retaining OBO defaults, and empty hierarchy lists
  disabling default part-of while retaining subclass edges.
- These tests stop at RDF2JSON JSON/status files. Existing whole-Nextflow and
  API golden tests remain the assembled-system safety net, but do not replace
  specific expected fields at this boundary. Current status tests invoke one
  ontology at a time; a planned small Nextflow fixture with both failing and
  successful ontologies will check same-run isolation, consistent with the
  agreed per-ontology release behavior.

## Remaining gaps and separate production observations

- **Confirmed cyclic-import nontermination (not fixed by this tests-only PR).**
  Two local Turtle ontology files importing each other were run through the
  packaged JAR on `origin/dev` baseline `d00c2ac7a`. After four seconds the
  process had attempted each import over 28,000 times and had produced no
  status file; the experiment was forcibly stopped. `OntologyGraph` enqueues
  each `owl:imports` triple again and drains `importUrls` without a visited-URL
  set. A passing assertion must not freeze that nontermination. Expected
  recovery/status behavior needs a separate production change and regression
  test. This concerns an import loop within one ontology, not the intentional
  Nextflow isolation of independent ontologies.
- **Cycle closure convention.** The asserted named class cycle has finite
  reachability including the start node, and metrics count each reachable
  source once. This documents the existing closure algorithm, not a proposal
  to reason over or repair OWL cycles. Direct subclass self-loop semantics have
  not been separately agreed or frozen.
- **Shared-import entity counts.** The shared dependency test asserts unique
  output entities and descendant counts. It deliberately does not assert
  `numberOfClasses`, which is incremented for each parsed class declaration,
  including repeated imports. Whether this metadata should count declarations
  or unique entities needs a separate decision.
- **Malformed OWL and evidence duplicates.** Missing/non-URI negative assertion
  fields have explicit ignore contracts. Malformed reified axioms and
  class/property disjoint lists do not have an agreed recovery contract.
  Repeated anonymous restriction/axiom evidence, unresolved list fillers,
  deeper nested label lists, overlapping base URI precedence, and the
  unmatched-pattern/underscored-default CURIE combination remain targeted gaps.
- **Scope and validation.** Entire `RelatedAnnotator.hasValue` remains excluded,
  including literal, individual, missing-target, direction and failure
  assertions. Status contracts remain unchanged. The mixed-success/failure
  Nextflow isolation fixture remains a workflow task; this PR adds no
  orchestration or production code. Run the full dataload Maven reactor and
  the existing full dataload/API CI check before declaring this change green.
