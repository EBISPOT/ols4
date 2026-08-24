---
status: accepted
date: 2026-08-24
---

# Adopt layered backend testing with disposable PostgreSQL

OLS4 backend controller coverage will combine direct unit tests (`*Test`), Spring MVC Web Integration Tests (`*WIT`), repository and thin controller Integration Tests (`*IT`) against disposable pgvector PostgreSQL, and the existing `test_api.sh` system regression suite. CI will not connect to an internal or production database: database tests generate their schema through the production schema generator and load a small committed fixture, trading some Docker runtime and fixture maintenance for deterministic, isolated tests that exercise the real PostgreSQL query path.

## Considered options

- **Internal or production database:** rejected because CI access would require sensitive credentials and network connectivity, and changing production data would make results non-deterministic.
- **Mocks only:** rejected because mocks cannot validate PostgreSQL search, filtering, sorting, faceting, or ranking semantics.
- **System regression only:** rejected because the complete dataload and API comparison is too slow and broad to provide focused controller feedback.

## Consequences

- Every controller should receive parameter and stable HTTP-contract coverage without requiring every possible parameter combination.
- PostgreSQL-specific behavior is tested with a small disposable database; full ontology loading remains the responsibility of the system regression suite.
- V1 and V2 remain equally important, while V1 tests must preserve backward-compatible behavior.
- JaCoCo initially records a baseline without enforcing a repository-wide threshold.

See [OLS4 Backend Testing Strategy](../backend-testing-strategy.md) for the detailed testing vocabulary, responsibilities, fixture policy, Maven lifecycle, and rollout plan.
