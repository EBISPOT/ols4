# OLS4 Backend Testing Strategy

**Status:** Approved baseline

**Date:** 2026-08-24

**Initial pilot:** `V2OntologyController`

## Purpose

OLS4 currently has only a small number of backend tests. This strategy establishes a repeatable way to protect the HTTP contracts of both API versions, support safe refactoring, and exercise PostgreSQL behaviour without connecting CI to an internal or production database.

V1 and V2 are equally important:

- V1 remains heavily used and must not receive breaking changes.
- V2 is the encouraged API and can evolve, but intentional contract changes must still be explicit and tested.

Coverage is a diagnostic. The primary measure of success is meaningful protection of routes, parameters, stable response fields, errors, and database behaviour.

## Testing vocabulary

| Name | Naming convention | Purpose |
|---|---|---|
| Unit test | `*Test` | Exercise one class directly with mocked collaborators. |
| Web Integration Test | `*WIT` | Exercise real Spring MVC routing, binding, defaults, exception handling, and serialization with backend collaborators mocked. |
| Integration Test | `*IT` | Exercise real repository/search behaviour against disposable PostgreSQL. |
| Full-stack controller integration | Controller `*IT` | Exercise a small number of real controller-to-database paths against disposable PostgreSQL. |
| System regression | Existing `test_api.sh` suite | Exercise the complete dataload and deployed API comparison against committed expected output. |

These layers complement rather than replace one another.

## Test responsibilities

### Unit tests

Controller unit tests cover decisions owned by the controller, including:

- Filters added or removed by controller logic.
- Dynamic-property filtering before repository delegation.
- Exact collaborator arguments.
- Response wrapping and status selection.
- Found and missing-resource branches.
- Selection of grouping fields such as `tags` and `domain`.

Unit tests do not try to reproduce Spring request binding or database behaviour.

### Web Integration Tests

WITs use the real Spring MVC layer and real controller while replacing repositories and other backend collaborators with mocks. They protect:

- Routes and HTTP methods.
- Query and path parameter binding.
- Optional-parameter defaults.
- Repeated and encoded query parameters.
- Pagination binding.
- Response status, media type, and JSON serialization.
- Global exception handling and stable error fields.
- The stable portion of each endpoint's JSON contract.

A WIT produces the actual controller HTTP response. The repository mock supplies deterministic input data; it does not replace the controller, Spring MVC, or serialization.

### Repository Integration Tests

Repository ITs use the real `OntologyRepository`, `OlsSearchClient`, PostgreSQL driver, jOOQ query construction, and a disposable pgvector PostgreSQL database. They protect:

- Full-text and exact search behaviour.
- `searchFields` and `boostFields` behaviour.
- Dynamic filters, including repeated and comma-separated values.
- Obsolete-record filtering.
- Pagination, deterministic ordering, and supported sorting.
- Tag and domain faceting/grouping.
- Language and identifier validation owned below the controller.
- Found and missing records.

### Full-stack controller Integration Tests

Controller ITs use the real Spring MVC controller path, real repository stack, and real disposable database. They provide a thin wiring proof for each controller route. Exhaustive parameter coverage remains in WITs, and exhaustive database semantics remain in repository ITs.

For the `V2OntologyController` pilot, the full-stack suite contains one representative happy path for each of its four routes.

### System regression tests

The existing `test_api.sh` suite continues to own full OWL-to-dataload-to-database-to-API regression coverage. The new Maven suites must not rerun the complete dataload for every controller test.

## Parameter testing standard

Every declared or intentionally supported parameter must have direct test evidence. This does not require testing every possible combination.

For a controller, the matrix includes:

1. One request proving defaults when optional parameters are omitted.
2. One focused valid-value test for every declared parameter.
3. Parameterized malformed-value tests for typed parameters.
4. Boundary tests for pagination and other bounded values.
5. Single, repeated, and comma-separated dynamic-property values where supported.
6. URI-based and encoded dynamic-property names where supported.
7. Proof that reserved parameters do not leak into dynamic filters.
8. A small number of meaningful interactions, such as `search + searchFields + exactMatch`.
9. Route-specific required path parameters, missing resources, and invalid identifiers.

For `V2OntologyController`, this includes:

- `page`
- `size`
- `sort`
- `search`
- `searchFields`
- `boostFields`
- `exactMatch`
- `includeObsoleteEntities`
- dynamic search properties
- `lang`
- `resolveReferences`
- `manchesterSyntax`
- the required `onto` path segment

`resolveReferences` and `manchesterSyntax` receive WIT binding and forwarding coverage in this pilot. Their transformation semantics belong to focused transformer/repository tests and are not duplicated in full-stack controller tests.

Malformed boolean and other controller-bound typed parameters return HTTP 400 with stable error fields. OLS4's existing pageable resolver deliberately treats non-numeric page/size values as omitted and clamps negative or oversized values; the WIT records that compatibility behaviour explicitly. Unsupported behaviour must be rejected or removed from the published contract rather than silently producing misleading results.

## Assertion policy

Tests assert stable contract fields, not complete JSON snapshots.

Representative stable fields include:

- HTTP status and content type.
- `page`, `numElements`, `totalPages`, and `totalElements`.
- Selected `ontologyId`, `title`, and grouping keys.
- Selected facet counts.
- Error `status` and `message`.

Full JSON comparison remains the responsibility of the existing system regression suite. Time-varying fields, implementation-only metadata, and unrelated linked content should not make focused controller tests brittle.

## Disposable database strategy

PR CI must never connect to an internal or production database. Even read-only production-backed tests would be non-deterministic, require sensitive network access and credentials, and risk coupling merge availability to production state.

Repository and controller ITs use Testcontainers with the same database image used by OLS4:

```text
pgvector/pgvector:0.8.0-pg17
```

The database lifecycle is:

1. Start a disposable pgvector PostgreSQL container.
2. Generate the schema through the production `dataload/create_postgres_schema.py` path.
3. Load a small committed, readable fixture.
4. Run integration tests.
5. Destroy the container.

The test suite must not maintain an independent handwritten schema. Dynamic filter columns required by the fixture, including `tags` and `domain`, must be declared through the production schema generator.

## Fixture policy

The controller pilot uses four ontology records:

1. An active EFO-derived ontology.
2. A second active EFO-derived ontology for pagination, sorting, searching, and overlapping groups.
3. An active DUO-derived ontology with a different domain.
4. An obsolete ontology.

Together they provide:

- More than one page at a deliberately small page size.
- Deterministic ordering and sorting.
- Search terms distributed across different fields.
- Overlapping and distinct tags.
- More than one domain.
- Active and obsolete records.
- Known and missing ontology identifiers.

Existing sources should be reused where practical:

- `testcases/hierarchical-properties/efo.*`
- `testcases/iri-labels/efo-iri-labels.*`
- `testcases/localized-labels/label.*`
- `testcases/duo.*`
- `testcases/defined-fields/IsObsoleteSimple.*`
- selected stable values from `testcases_expected_output_api/v2/ontologies.json`

The backend fixture lives under backend test resources rather than adding a new generic JSON config under `testcases/`, because `test_api.sh` automatically loads every testcase JSON configuration.

Existing `.pgbin` outputs can be used as reference material. The fast integration suite should not blindly combine independently generated `.pgbin` files whose dynamic column layouts may differ.

Fixture changes must be intentional, reviewable, and documented. Production snapshots are not test fixtures.

## Maven lifecycle and local development

Maven test discovery is explicit:

- Surefire runs standard `*Test` classes.
- Surefire is additionally configured to run `*WIT` classes.
- Failsafe runs `*IT` classes during `integration-test` and `verify`.

Expected developer commands:

```bash
# Unit tests and Web Integration Tests; Docker is not required.
mvn -B -ntp -pl backend -am test

# Complete backend suite, including disposable-database Integration Tests.
mvn -B -ntp -pl backend -am verify

# Database Integration Tests only; used by the dedicated CI gate.
mvn -B -ntp -pl backend -am -Pintegration-tests-only verify
```

Java 17 is required. Invoking the integration suite without an available Docker runtime must fail clearly rather than silently skipping database tests.

On Rancher Desktop for macOS, local execution may also require `DOCKER_HOST` to point at `~/.rd/docker.sock`, `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`, and `-Dapi.version=1.44`. GitHub-hosted Linux runners use their standard Docker socket and do not need those overrides.

## Continuous integration

GitHub Actions adds two required backend gates before the existing expensive dataload/API job:

```text
Unit + WIT ─────────┐
                    ├─→ existing Docker/dataload/API regression
Database IT ────────┘
```

The gates should run in parallel where possible. Maven dependency caching should be enabled. The existing backend Docker build may continue packaging with tests skipped because dedicated CI jobs have already executed them.

JaCoCo initially publishes coverage without a repository-wide failure threshold. After the pilot and at least one corresponding V1 controller, the team should use the measured baseline to choose meaningful thresholds.

## Defect workflow

The testing pilot must not silently codify behaviour that contradicts the intended contract.

When a test exposes a current defect:

1. Confirm the intended contract from documentation, known clients, existing production behaviour, and team decisions.
2. Create a separate branch and PR from current `dev`.
3. Put the smallest regression test and production fix in that bug-fix PR.
4. Keep unrelated production refactoring out of the testing-framework PR.
5. Merge the bug fix, then rebase the broader testing PR onto updated `dev`.

Two likely pilot findings require explicit validation:

- Spring `Pageable` exposes `sort`, but current repository ordering does not appear to consume it and the query parameter may leak into dynamic filters.
- Some malformed Spring-bound parameters may currently be handled as HTTP 500 instead of HTTP 400.

## Controller definition of done

A controller is covered when:

- Its direct unit-test class covers controller-owned decisions.
- Its WIT covers every route and every supported parameter.
- Stable success and error contract fields are asserted.
- Applicable repository behaviour has PostgreSQL IT coverage.
- A thin full-stack controller IT covers each route's representative happy path.
- Discovered defects have regression tests in separate bug-fix PRs.
- All tests run automatically in their CI gates.
- JaCoCo reports the resulting coverage.

## Rollout

1. Complete the `V2OntologyController` pilot.
2. Review usefulness, runtime, failure clarity, and fixture maintainability.
3. Apply the approach to `V1OntologyController`.
4. Continue by alternating corresponding V1 and V2 controller families, prioritized by traffic and contract risk.
5. Establish coverage thresholds from the resulting representative baseline.

Read-only production smoke monitoring is a separate future initiative for an internal or self-hosted environment. It is not part of the initial PR testing framework and must not become a merge-blocking production dependency.

## Out of scope for the pilot

- Connecting GitHub-hosted CI to production or internal databases.
- Running the complete dataload inside every Maven integration test.
- Full-JSON snapshot assertions in unit, WIT, or repository IT suites.
- Refactoring production controller or repository code solely to make the pilot aesthetically cleaner.
- Testing every Cartesian combination of query parameters.
- Introducing a repository-wide coverage threshold before a meaningful baseline exists.
- Building production smoke monitoring.
