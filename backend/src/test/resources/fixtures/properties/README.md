# Property integration fixture

This three-record synthetic supplement is loaded by the `PropertyRepository`, `V1PropertyRepository`
(non-scoped and ontology-scoped), `V2PropertyController`, and `V1OntologyPropertyController`
PostgreSQL suites. It leaves the shared entity fixture unchanged while adding the smallest data set
needed to cover property-specific pagination, ranking, filtering, ontology scoping, obsolete
selection, and hierarchy behavior.

- `EFO_0101` is an active child of the shared `EFO_0100` property. Its `directParents` and
  `directAncestors` wrapper arrays populate the production `direct_parents`/`direct_ancestors`
  PostgreSQL columns used by `getParents`/`getChildren`/`getDescendants`/`getAncestors`. Its nested
  `json.directParent` array and `has_direct_parents`/`has_hierarchical_parents` columns (derived
  from `directParents` at load time) are the separate signals `getRoots` and the property js-tree
  builder read — the js-tree builder walks the stored JSON's `directParent` key, not the
  `direct_parents` column, so both must stay populated together for a non-root record.
- `DUO_0100` provides a second ontology and a definition-weighted ranking case.
- `EFO_0199` is obsolete and non-defining. It is excluded by the V2 controller default while its
  defining-ontology flag distinguishes the legacy V1 filtered and unfiltered routes without
  changing active-property search ranking.

The wrapper fields map to production `ols_entities` columns and the nested `json` object is the
compressed API document returned by the repository. Update both views together, then run the Java
17 backend `verify` lifecycle with Docker available. The schema still comes exclusively from
`dataload/create_postgres_schema.py`.
