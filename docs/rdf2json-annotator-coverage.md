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
| `DirectParentsAnnotator` | `directParent` for classes, properties, and individuals | Named class chain in `RDF2JSONRelationsIT`; imported class parent in `RDF2JSONImportsIT` | P1: property `subPropertyOf`, individual `rdf:type`, and excluding `owl:NamedIndividual` as a parent. |
| `RelatedAnnotator` | `relatedTo` for selected anonymous subclass expressions/restrictions | Named `someValuesFrom` in `RDF2JSONRelationsIT`; `oneOf`, `intersectionOf`, and both as `someValuesFrom` fillers in `RelatedAnnotatorTest` | P1: self/unresolved fillers, duplicate relations, and packaged-JSON checks for complex expressions; see open question below for `hasValue`. |
| `HierarchicalParentsAnnotator` | `hierarchicalParent` and edge axiom `childRelationToParent`/`parentRelationToChild` | Named class edge and configured restriction edge in `RDF2JSONRelationsIT` | P1: direct individual hierarchical-property triples, inverse edge metadata, absent/unlisted parent, and property hierarchy. |
| `AncestorsAnnotator` | `directAncestor`, `hierarchicalAncestor` closure | Named class chain and restriction hierarchy in `RDF2JSONRelationsIT` | P1: individual hierarchical closure and multi-parent DAG; P2: cycles. |
| `HierarchyMetricsAnnotator` | `numDescendants`, `numHierarchicalDescendants` | Class chain and restriction counts in `RDF2JSONRelationsIT` | P1: property/individual counts and a multi-parent DAG; P2: cycle behavior. |
| `ShortFormAnnotator` | `shortForm`, `curie` | Base-URI/prefix example in `RDF2JSONAnnotationsIT`; custom regex with underscored prefix and multi-underscore numeric CURIE in `ShortFormAndLabelAnnotatorTest` | P2: URN, absent base URI, unmatched/invalid regex, and nonnumeric underscore suffix. |
| `DefinitionAnnotator` | `definition` collated from configured/default predicates | Configured output in `RDF2JSONAnnotationsIT`; configured/default/empty choices in `DefinitionAnnotatorTest` | P2: multiple predicates and language/typed literal retention in packaged output. |
| `SynonymAnnotator` | `synonym` and normalized per-predicate arrays | Custom and OBO exact values in `RDF2JSONAnnotationsIT` | P2: related/narrow/broad default predicates, multiple values, and single-value array normalization. |
| `ReifiedPropertyAnnotator` | Value object with `type: reification` and `axioms` evidence | Note and synonym-type axiom in `RDF2JSONAnnotationsIT` | P1: multiple axioms on one assertion and URI-valued annotated target; P2: malformed axiom input. |
| `OntologyMetadataAnnotator` | Entity `ontologyId`, `ontologyIri`, `ontologyPreferredPrefix` | Class identity fields in `RDF2JSONAnnotationsIT` | P2: absent preferred prefix and property/individual variants. |
| `HierarchyFlagsAnnotator` | `hasDirectParents/Children`, `hasHierarchicalParents/Children` | Class chain and restriction flags in `RDF2JSONRelationsIT` | P1: property/individual flags and excluded top nodes (`owl:Thing`, `owl:TopObjectProperty`). |
| `IsObsoleteAnnotator` | `isObsolete` boolean | `owl:deprecated true` and non-obsolete class in `RDF2JSONAnnotationsIT` | P2: `owl:deprecated "1"`, obsolete-class parent, and false/unset forms. |
| `LabelAnnotator` | `label` list with language tags and short-form fallback | English/French labels, French-only fallback, configured predicate in `RDF2JSONAnnotationsIT`; English suppression of fallback and configured override in `ShortFormAndLabelAnnotatorTest` | P2: nested source lists, multiple label predicates, language-less label, and empty configured predicate list. |
| `ConfigurablePropertyAnnotator` | `hierarchicalProperty`, `definitionProperty`, `synonymProperty` provenance | Definition/synonym predicates in `RDF2JSONAnnotationsIT` and definition choices in `DefinitionAnnotatorTest` | P1: `hierarchicalProperty` provenance and configured versus default hierarchy predicates. |
| `PreferredRootsAnnotator` | Ontology `preferredRoot`; entity `isPreferredRoot` | Configured root and non-root in `RDF2JSONAnnotationsIT` | P2: roots declared on the ontology via IAO or OLS predicates, duplicates, missing roots. |
| `DisjointWithAnnotator` | Pairwise `owl:disjointWith`, `owl:propertyDisjointWith`, `owl:differentFrom` | `owl:AllDisjointClasses` in `RDF2JSONRelationsIT` | P1: `AllDisjointProperties` and `AllDifferent` lists, including an unresolved member. |
| `HasIndividualsAnnotator` | `hasIndividuals: true` on class with instance | Class instance in `RDF2JSONAnnotationsIT` | P2: no instance, external/unresolved type, and punning. |
| `EquivalenceAnnotator` | Reverse `owl:equivalentClass` or `owl:equivalentProperty` | One-way class pair in `RDF2JSONRelationsIT` | P1: one-way property pair; P2: missing target and already-bidirectional assertions. |

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
- For `hasValue` with an individual filler, the implementation currently adds
  `relatedTo` **on the individual pointing to the class**. Other examined
  branches add it on the subclass. That direction is an observed difference,
  **not yet an agreed contract or a confirmed defect**. Ask the domain owner
  which direction dataload should expose before freezing it in a test.
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
