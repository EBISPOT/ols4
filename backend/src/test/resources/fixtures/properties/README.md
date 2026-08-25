# Property integration fixture

This three-record synthetic supplement is loaded only by the `PropertyRepository` and
`V2PropertyController` PostgreSQL suites. It leaves the shared entity fixture unchanged while
adding the smallest data set needed to cover property-specific pagination, ranking, filtering,
ontology scoping, obsolete selection, and hierarchy behavior.

- `EFO_0101` is an active child of the shared `EFO_0100` property. Its `directParents` and
  `directAncestors` arrays exercise the production PostgreSQL hierarchy columns.
- `DUO_0100` provides a second ontology and a definition-weighted ranking case.
- `EFO_0199` is obsolete and is excluded by the controller default.

The wrapper fields map to production `ols_entities` columns and the nested `json` object is the
compressed API document returned by the repository. Update both views together, then run the Java
17 backend `verify` lifecycle with Docker available. The schema still comes exclusively from
`dataload/create_postgres_schema.py`.
