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
the compressed API document returned by the repository. It also carries the small stable V1
metadata surface required by `V1OntologyMapper`: language, load time, entity counts, preferred
prefix, and source location. The DUO counts and configuration values follow
`testcases_expected_output/testcases/duo/ontologies.json` and
`testcases_expected_output_api/ontologies.json`; the synthetic records use deliberately simple
values.

To update the fixture, edit both views of a record together and run `mvn -pl backend verify`;
schema creation continues to come from `dataload/create_postgres_schema.py`.
