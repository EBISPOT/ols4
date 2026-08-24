# Ontology integration fixture

This four-record fixture is deliberately small and deterministic. It derives stable metadata from
the repository's EFO, localized-label, DUO, and obsolete-entity test material, but it is not a
production snapshot.

- `efo` is the representative active EFO ontology.
- `efo-atlas` is a deliberately synthetic EFO-derived record used for pagination and overlapping
  filters.
- `duo` provides distinct information-domain and data-use search terms.
- `legacy-efo` is deliberately obsolete.

The wrapper fields map directly to PostgreSQL search/filter columns. The nested `json` object is
the compressed API document returned by the repository. To update the fixture, edit both views of
a record together and run `mvn -pl backend verify`; schema creation continues to come from
`dataload/create_postgres_schema.py`.
