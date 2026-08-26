# Class integration fixture

This three-record synthetic supplement is loaded only by the V2 class suites. It reuses the shared
EFO liver-disease class as the hierarchy root while keeping the general entity fixture totals
unchanged.

- `EFO_1001` is an active class with direct and hierarchical parent/ancestor links to `EFO_0001`.
- `EFO_1999` has the same links but is obsolete, which makes hierarchy filtering observable.
- `EFO_I100` is an individual whose direct ancestor is `EFO_0001`, covering the class controller's
  individual-ancestor route without changing the general entity fixture.

The wrapper fields map to production `ols_entities` columns and the nested `json` object is the
compressed API document returned by the repository. Update both views together, then run the Java
17 backend `verify` lifecycle with Docker available. The schema continues to come only from
`dataload/create_postgres_schema.py`.
