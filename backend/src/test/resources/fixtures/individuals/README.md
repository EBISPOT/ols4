# Individual integration fixture

This four-record synthetic supplement is loaded only by the V2 individual suites. It keeps the
shared entity fixture totals unchanged while covering global and ontology-scoped individual
search, obsolete filtering, pagination, dynamic properties, lookup by IRI, and class membership.

- Two active EFO individuals distinguish defining status, search fields, ranking, filters, and
  membership in different EFO classes.
- One active DUO individual supplies a second ontology and distinct policy/information filters.
- One obsolete EFO individual proves the default and opt-in obsolete contracts.

The wrapper fields map to production `ols_entities` columns. The RDF type URI is declared through
the production schema generator and stores class membership for the class-to-individual route.
The nested `json` object is the compressed API document returned by the repository. Update both
views together, then run the Java 17 backend `verify` lifecycle with Docker available.
