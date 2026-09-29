# RDF2JSON annotator coverage map

This map covers the 22 annotators called, in order, by `OntologyGraph` after it
has parsed the ontology and its imports. It is a map of **observable RDF2JSON
output**, not a claim that calling every annotator once proves all of its
branches. The baseline below is the packaged-JAR contract suite in
`RDF2JSONAnnotationsIT`, `RDF2JSONRelationsIT`, `RDF2JSONFormatsIT`, and
`RDF2JSONImportsIT`, plus
`DefinitionAnnotatorTest`, `ShortFormAndLabelAnnotatorTest`, and
`RelatedAnnotatorTest`.
`RDF2JSONContractIT` separately covers per-ontology
`SUCCESS`, `FALLBACK`, `FAILED_NO_FALLBACK`, and `SKIPPED` outcomes.

An "asserted" field has an explicit expected value in those tests. "Partial"
means that another branch of the same annotator remains unasserted; it does
not imply the current behavior is wrong. The priority column suggests the
next *small local fixture* or direct unit test, not a change to production
semantics. Use targeted JSON field assertions rather than full-file snapshots.

| Annotator (`OntologyGraph` order) | Observable output | Asserted baseline | Important unasserted branch; next fixture/test |
| --- | --- | --- | --- |
| `SearchableAnnotationValuesAnnotator` | `searchableAnnotationValues` literal list | Custom literal in `RDF2JSONAnnotationsIT` | P2: excluded RDF/RDFS/OWL predicates and runtime timestamps; URI versus literal values. |
| `InverseOfAnnotator` | Reverse `owl:inverseOf` on a property | One-way pair in `RDF2JSONRelationsIT` | P2: absent target and already-bidirectional assertions. |
| `NegativePropertyAssertionAnnotator` | `negativePropertyAssertion+<property IRI>` on source individual | `owl:targetIndividual` in `RDF2JSONAnnotationsIT` | P2: literal `owl:targetValue`, malformed/missing source or assertion property. |
| `OboSynonymTypeNameAnnotator` | `oboSynonymTypeName` within reified synonym axiom evidence | Named synonym type in `RDF2JSONAnnotationsIT` | P2: unresolved synonym type or missing literal label. |
| `DirectParentsAnnotator` | `directParent` for classes, properties, and individuals | Named class chain and imported class in earlier tests; property `subPropertyOf` and individual `rdf:type` in `RDF2JSONRelationsIT` | P2: unresolved parent and exclusion of `owl:NamedIndividual` when another named type is present (the fixture asserts only the resulting parent). |
| `RelatedAnnotator` | `relatedTo` for selected anonymous subclass expressions/restrictions | Named `someValuesFrom` and packaged-JSON `intersectionOf` in `RDF2JSONRelationsIT`; `oneOf`, `intersectionOf`, and both as `someValuesFrom` fillers in `RelatedAnnotatorTest` | P2: self/unresolved fillers and duplicate relations. `hasValue` is excluded from this testing effort by decision. |
| `HierarchicalParentsAnnotator` | `hierarchicalParent` and edge axiom `childRelationToParent`/`parentRelationToChild` | Named class and configured restriction edges; configured individual assertion and inverse edge metadata in `RDF2JSONRelationsIT` | P2: absent/unlisted parent, self-edge exclusion, and property hierarchy. |
| `AncestorsAnnotator` | `directAncestor`, `hierarchicalAncestor` closure | Named class chain, multi-parent DAG, instance direct ancestry, and individual hierarchical ancestry in `RDF2JSONRelationsIT` | P2: cycles and unresolved parents. |
| `HierarchyMetricsAnnotator` | `numDescendants`, `numHierarchicalDescendants` | Class chain, multi-parent DAG, property and individual descendants in `RDF2JSONRelationsIT` | P2: cycles and overlapping hierarchical paths. |
| `ShortFormAnnotator` | `shortForm`, `curie` | Base-URI/prefix example in `RDF2JSONAnnotationsIT`; custom regex with underscored prefix and multi-underscore numeric CURIE in `ShortFormAndLabelAnnotatorTest` | P2: URN, absent base URI, unmatched/invalid regex, and nonnumeric underscore suffix. |
| `DefinitionAnnotator` | `definition` collated from configured/default predicates | Configured output in `RDF2JSONAnnotationsIT`; configured/default/empty choices in `DefinitionAnnotatorTest` | P2: multiple predicates and language/typed literal retention in packaged output. |
| `SynonymAnnotator` | `synonym` and normalized per-predicate arrays | Custom and OBO exact values in `RDF2JSONAnnotationsIT` | P2: related/narrow/broad default predicates, multiple values, and single-value array normalization. |
| `ReifiedPropertyAnnotator` | Value object with `type: reification` and `axioms` evidence | Literal and URI targets, synonym-type axiom, and multiple axioms on one assertion in `RDF2JSONAnnotationsIT` | P2: malformed axiom input and repeated identical evidence. |
| `OntologyMetadataAnnotator` | Entity `ontologyId`, `ontologyIri`, `ontologyPreferredPrefix` | Class identity fields in `RDF2JSONAnnotationsIT` | P2: absent preferred prefix and property/individual variants. |
| `HierarchyFlagsAnnotator` | `hasDirectParents/Children`, `hasHierarchicalParents/Children` | Class chain, property direct flags, and individual hierarchical flags in `RDF2JSONRelationsIT` | P2: excluded top nodes (`owl:Thing`, `owl:TopObjectProperty`). |
| `IsObsoleteAnnotator` | `isObsolete` boolean | `owl:deprecated true` and non-obsolete class in `RDF2JSONAnnotationsIT` | P2: `owl:deprecated "1"`, obsolete-class parent, and false/unset forms. |
| `LabelAnnotator` | `label` list with language tags and short-form fallback | English/French labels, French-only fallback, configured predicate in `RDF2JSONAnnotationsIT`; English suppression of fallback and configured override in `ShortFormAndLabelAnnotatorTest` | P2: nested source lists, multiple label predicates, language-less label, and empty configured predicate list. |
| `ConfigurablePropertyAnnotator` | `hierarchicalProperty`, `definitionProperty`, `synonymProperty` provenance | Definition/synonym predicates in `RDF2JSONAnnotationsIT`, definition choices in `DefinitionAnnotatorTest`, configured individual `hierarchicalProperty` in `RDF2JSONRelationsIT` | P2: default hierarchy predicate provenance. |
| `PreferredRootsAnnotator` | Ontology `preferredRoot`; entity `isPreferredRoot` | Configured root and non-root in `RDF2JSONAnnotationsIT` | P2: roots declared on the ontology via IAO or OLS predicates, duplicates, missing roots. |
| `DisjointWithAnnotator` | Pairwise `owl:disjointWith`, `owl:propertyDisjointWith`, `owl:differentFrom` | `AllDisjointClasses`, `AllDisjointProperties`, and `AllDifferent` in `RDF2JSONRelationsIT` | P2: unresolved member in `AllDifferent`; malformed lists in other forms. |
| `HasIndividualsAnnotator` | `hasIndividuals: true` on class with instance | Class instance in `RDF2JSONAnnotationsIT` | P2: no instance, external/unresolved type, and punning. |
| `EquivalenceAnnotator` | Reverse `owl:equivalentClass` or `owl:equivalentProperty` | One-way class and property pairs in `RDF2JSONRelationsIT` | P2: missing target and already-bidirectional assertions. |

## Boundaries and contract decisions

- The annotators run **after imports are parsed**. `RDF2JSONImportsIT` uses two
  local Turtle files through the packaged JAR and checks `SUCCESS`,
  `imported=true` for an import-only class, `imported=false` for primary and
  shared classes, the imported class's label and derived direct parent, and
  the shared class's label supplied by the import.
  This covers one direct import; nested or cyclic import topologies remain
  unasserted (P2).
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
  collation, while omission uses defaults. Other configuration rules in the
  table are implementation observations until individually agreed and tested.
- These tests stop at RDF2JSON JSON/status files. Existing whole-Nextflow and
  API golden tests remain the assembled-system safety net, but do not replace
  specific expected fields at this boundary. Current status tests invoke one
  ontology at a time; a planned small Nextflow fixture with both failing and
  successful ontologies will check same-run isolation, consistent with the
  agreed per-ontology release behavior.
