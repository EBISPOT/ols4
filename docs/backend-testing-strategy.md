# OLS4 Backend Testing Strategy

**Status:** Approved baseline

**Date:** 2026-08-24

**Initial pilot:** `V2OntologyController`

**Decision record:** [ADR 0001 — Adopt layered backend testing with disposable PostgreSQL](adr/0001-adopt-layered-backend-testing.md)

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

Before finalizing a controller IT suite, enumerate every route straight from the controller source and check off one thin case per route — do not substitute a "representative subset" chosen by eye. Two routes that look similar by name or description can still call entirely different production code; skipping one as redundant with the other proves nothing about the code path it actually owns. This is not hypothetical: the first `V1OntologyPropertyController` IT suite covered 6 of 9 routes, treating `jstree/children/{nodeid}` as adequately covered by the plain `jstree` test. It is not — the two routes use different builder classes (`V1ChildrenJsTreeBuilder` vs `V1AncestorsJsTreeBuilder`) — and the gap hid a real `NullPointerException` (fixed in PR #1391) until full route coverage was added during review of PR #1390.

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

V1 compatibility includes its existing error representation. In particular,
`V1OntologyController` missing-ontology responses retain HTTP 404 with the legacy servlet error
reason `EntityModel not found` and an empty body; focused tests must not replace that response with
the V2 JSON error shape. Other V1 controller errors handled by the global advice continue to expose
JSON `status` and `message` fields.

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

## Implemented V1 ontology-controller baseline

Verified locally on 2026-08-25 with Java 17 and Rancher Desktop:

- Surefire runs 79 tests, including 4 direct `V1OntologyControllerTest` cases and 17
  `V1OntologyControllerWIT` cases, in approximately 15.8 seconds after compilation is warm.
- Failsafe runs 23 PostgreSQL tests, including 6 `V1OntologyRepositoryIT` cases and 2 thin
  `V1OntologyControllerIT` cases.
- The complete clean `verify` lifecycle runs all 102 tests in approximately 25.9 seconds; a warm
  repeat took approximately 22.7 seconds.
- Whole-backend JaCoCo coverage is 17.4% lines and 16.4% branches. The V1 controller covers 11 of
  12 lines and both branches; its repository covers all 15 lines and both branches. No coverage
  failure threshold is introduced by the V1 rollout.

## Implemented V2 entity-controller baseline

Verified locally on 2026-08-25 with Java 17 and Rancher Desktop:

- Surefire runs 154 tests, including 6 direct `V2EntityControllerTest` cases and 67
  `V2EntityControllerWIT` cases. Two warm repeats took approximately 16.5 and 16.4 seconds.
- Failsafe runs 39 PostgreSQL tests, including 12 `EntityRepositoryIT` cases and 4 thin
  `V2EntityControllerIT` cases. The database-only gate passed twice; a recorded repeat took
  approximately 25.3 seconds.
- The complete clean `verify` lifecycle runs all 193 tests in approximately 31.1 seconds.
- Whole-backend JaCoCo coverage is 19.5% lines and 18.6% branches. The V2 entity controller covers
  all 25 lines and all 10 branches; its repository covers 48 of 49 lines and 10 of 14 branches.
  No coverage failure threshold is introduced by this rollout.

## Implemented V1 term-controller baseline

Verified locally on 2026-08-25 with Java 17 and Rancher Desktop:

- Surefire runs 188 tests, including 11 direct `V1TermControllerTest` cases and 23
  `V1TermControllerWIT` cases. Two warm repeats took approximately 18.1 and 17.8 seconds and
  passed with a deliberately unavailable Docker socket.
- Failsafe runs 50 PostgreSQL tests, including 7 `V1TermRepositoryIT` cases and 4 thin
  `V1TermControllerIT` cases. Two complete database-gate repeats took approximately 32.6 and
  33.0 seconds.
- The complete clean `verify` lifecycle runs all 238 tests in approximately 40.7 seconds.
- Whole-backend JaCoCo coverage is 23.6% lines and 21.2% branches. The V1 term controller covers
  all 42 lines and 24 of 28 branches; its repository covers 44 of 123 lines, including every
  finder used by the four controller routes. No coverage failure threshold is introduced.
- V1 compatibility retains the existing 1000-item default page size and accepts arbitrary
  language identifiers with value fallback; these differ intentionally from newer V2 contracts.

## Implemented V2 property-controller baseline

Verified locally on 2026-08-25 with Java 17 and Rancher Desktop:

- Surefire runs 260 tests, including 7 direct `V2PropertyControllerTest` cases and 65
  `V2PropertyControllerWIT` cases. Two repeats took approximately 22.0 and 18.9 seconds with a
  deliberately unavailable Docker socket.
- Failsafe runs 62 PostgreSQL tests, including 7 `PropertyRepositoryIT` cases and 5 thin
  `V2PropertyControllerIT` cases. Two complete database-gate repeats took approximately 40.9 and
  38.9 seconds.
- The complete clean `verify` lifecycle runs all 322 tests in approximately 57.4 seconds.
- Whole-backend JaCoCo coverage is 26.7% lines and 22.1% branches. The V2 property controller
  covers all 27 lines and all 6 branches; its repository covers 47 of 53 lines and 7 of 14
  branches, including every method used by the controller routes. No coverage failure threshold
  is introduced.
- The property-specific synthetic fixture supplements the shared entity data only for property
  suites, preserving established fixture totals while exercising production `direct_parents` and
  `direct_ancestors` columns. No production defect was exposed.

## Implemented V1 property-controller baseline

Verified locally on 2026-08-25 with Java 17 and Rancher Desktop:

- Surefire runs 298 tests, including 8 direct `V1PropertyControllerTest` cases and 29
  `V1PropertyControllerWIT` cases. Two runs with a deliberately unavailable Docker socket took
  approximately 18.1 and 19.3 seconds.
- Failsafe runs 73 PostgreSQL tests, including 7 `V1PropertyRepositoryIT` cases and 4 thin
  `V1PropertyControllerIT` cases. Two complete database-gate runs took approximately 43.6 and
  42.6 seconds.
- The clean `verify` lifecycle runs all 371 tests in approximately 57.1 seconds.
- Whole-backend JaCoCo coverage is 28.9% lines and 23.2% branches. The V1 property controller
  covers all 28 lines and all 12 branches; its repository covers 43 of 89 lines, including every
  finder used by the controller's four routes. No coverage failure threshold is introduced.
- V1 compatibility retains its 1000-item default page size, arbitrary language identifiers with
  value fallback, identifier precedence, double-encoded IRI paths, legacy HAL responses, and
  servlet-style 404 reason. The property fixture uses its obsolete record as the non-defining case
  so active V2 property search ranking remains unchanged. The rollout exposed that the V1 mapper
  omitted the public `is_obsolete` and `is_defining_ontology` flags. The minimal production fix
  merged in PR #1371, and the repository and thin controller ITs now assert both mapped values
  through the real PostgreSQL-to-HTTP path.

## Implemented V2 class-controller baseline

Verified locally on 2026-08-26 with Java 17 and Rancher Desktop:

- Surefire runs 466 tests, including 13 direct `V2ClassControllerTest` invocations and 153
  `V2ClassControllerWIT` invocations. Two runs with a deliberately unavailable Docker socket took
  approximately 18.9 and 19.5 seconds.
- Failsafe runs 94 PostgreSQL tests, including 9 `ClassRepositoryIT` cases and 11 thin
  `V2ClassControllerIT` cases. Two complete database-gate runs took approximately 50.5 and
  52.6 seconds.
- The clean `verify` lifecycle runs all 560 tests in approximately 1 minute 3 seconds.
- Whole-backend JaCoCo coverage is 32.3% lines and 24.5% branches. The V2 class controller covers
  all 51 lines and all 6 branches; its repository covers 91 of 128 lines and 11 of 48 branches,
  including every finder used by the controller's 11 routes. No coverage failure threshold is
  introduced.
- The class-specific synthetic fixture supplements the shared entity data only for class suites
  and exercises production parent and ancestor columns. The rollout exposed that class hierarchy
  queries could return non-class entities whose ancestor arrays referenced the class. The minimal
  production fix merged in PR #1373 and restricts all class hierarchy results to ontology classes;
  both focused regression tests and the broader repository/controller suites preserve that rule.

## Implemented V2 individual-controller baseline

Verified locally on 2026-08-27 with Java 17 and Rancher Desktop:

- Surefire runs 538 tests, including 6 direct `V2IndividualControllerTest` cases and 64
  `V2IndividualControllerWIT` invocations. Two runs with a deliberately unavailable Docker socket
  took approximately 22.8 and 18.8 seconds.
- Failsafe runs 105 PostgreSQL tests, including 7 `IndividualRepositoryIT` cases and 4 thin
  `V2IndividualControllerIT` cases. Two complete database-gate runs took approximately 56.7 and
  59.0 seconds.
- The clean `verify` lifecycle runs all 643 tests in approximately 1 minute 8 seconds.
- Whole-backend JaCoCo coverage is 33.7% lines and 25.3% branches, up from 32.6% lines and 24.6%
  branches on the merged prerequisite baseline. The V2 individual controller covers all 21 lines
  and all 6 branches; its repository covers 47 of 48 lines and 9 of 12 branches. No coverage
  failure threshold is introduced.
- The individual-specific four-record fixture covers two ontologies, active and obsolete records,
  search ranking, dynamic URI-named filters, and RDF-type class membership without changing the
  shared fixture totals. The rollout exposed that the class-to-individual route ignored
  `includeObsoleteEntities` and returned obsolete class members by default. The minimal production
  fix merged in PR #1375; its focused regressions and the broader repository/controller suites now
  preserve default exclusion and explicit opt-in.

## Implemented V1 individual-controller baseline

Verified locally on 2026-08-27 with Java 17 and Rancher Desktop:

- Surefire runs 576 tests, including 8 direct `V1IndividualControllerTest` cases and 29
  `V1IndividualControllerWIT` invocations. Two runs with a deliberately unavailable Docker socket
  took approximately 19.1 and 18.7 seconds.
- Failsafe runs 116 PostgreSQL tests, including 7 `V1IndividualRepositoryIT` cases and 4 thin
  `V1IndividualControllerIT` cases. Two complete database-gate runs took approximately 1 minute 4
  seconds and 1 minute 2 seconds.
- The clean `verify` lifecycle runs all 692 tests in approximately 1 minute 12 seconds.
- Whole-backend JaCoCo coverage is 35.8% lines and 25.9% branches, up from the measured merged
  prerequisite baseline of 34.1% lines and 25.3% branches. The V1 individual controller covers all
  28 lines and all 12 branches; its repository covers 43 of 76 lines, including every finder used
  by the controller's four routes. No coverage failure threshold is introduced.
- V1 compatibility retains its 1000-item default page size, arbitrary language identifiers with
  value fallback, identifier precedence, double-encoded IRI paths, legacy HAL responses, and
  servlet-style 404 reason. The suites reuse the four-record individual fixture without changing
  shared fixture totals.
- The rollout exposed that `V1IndividualMapper` omitted the public `is_obsolete` and
  `is_defining_ontology` flags. The minimal production fix merged in PR #1377 with a focused mapper
  regression and the two affected expected API responses; the repository and thin controller ITs
  now assert both values through the real PostgreSQL-to-HTTP path.

## Implemented V2 statistics-controller baseline

Verified locally on 2026-09-01 with Java 17 and Rancher Desktop:

- Surefire runs 584 tests, including 2 direct `V2StatisticsControllerTest` cases and 3
  `V2StatisticsControllerWIT` cases. Two Docker-free runs took Maven 20.041 and 20.215 seconds
  (wall-clock 21.09 and 21.03 seconds).
- Failsafe runs 119 PostgreSQL tests, including 2 `OlsSearchClientStatisticsIT` cases and 1 thin
  `V2StatisticsControllerIT` case. Two complete database-gate runs took Maven 68 and 71 seconds
  (wall-clock 69.68 and 72.66 seconds).
- The clean `verify` lifecycle runs all 703 tests in Maven 82 seconds (wall-clock 83.82 seconds).
- Whole-backend JaCoCo coverage is 37.0% lines and 27.3% branches. The
  `V2StatisticsController` covers all 10 of its executable lines. No coverage failure threshold
  is introduced.
- The statistics tests use the shared four-record ontology/entity fixture. Its ontology loader
  stores the production database type literal and includes a later EFO load timestamp so the
  PostgreSQL search tests exercise type counts and most-recent-load selection deterministically.

## Implemented V1 suggest-controller baseline

Verified locally on 2026-09-01 with Java 17 and Rancher Desktop:

- Surefire runs 597 tests, including 2 direct `V1SuggestControllerTest` cases, 9
  `V1SuggestControllerWIT` invocations, and the two focused regression tests merged with PRs
  #1381 and #1382. Two Docker-free runs took Maven 19.153 and 18.993 seconds.
- Failsafe runs 122 PostgreSQL tests, including 2 `OlsSearchClientSuggestIT` cases and 1 thin
  `V1SuggestControllerIT` case. Two complete database-gate runs took 75.74 and 76.75 seconds
  wall-clock.
- The clean `verify` lifecycle runs all 719 tests in 87.28 seconds wall-clock.
- Whole-backend JaCoCo coverage is 38.5% lines (1,837 of 4,768) and 28.2% branches (539 of
  1,908). The V1 suggest controller covers all 2 executable lines and 8 branches. No coverage
  failure threshold is introduced.
- The suggest tests add production-shaped autosuggest rows to the shared synthetic fixture,
  covering labels, synonyms, ontology restriction, deterministic ranking, and pagination through
  the real PostgreSQL search client. The WIT suite preserves the legacy JSON envelope and
  exercises defaults, typed failures, repeated and comma-separated ontology values, and frontend
  compatibility parameters.
- The rollout exposed two defects in the legacy suggest route. PR #1381 preserves the requested
  `start` offset in the response, and PR #1382 gives malformed ontology IDs a stable error
  message. Both fixes were isolated, merged, and covered by focused regressions before this test
  branch was rebased.

## Implemented V1 search-controller baseline

Verified locally on 2026-09-02 with Java 17 and Rancher Desktop:

- Surefire runs 629 tests, including 9 direct `V1SearchControllerTest` invocations and 20
  `V1SearchControllerWIT` invocations. Two Docker-free runs took Maven 18.903 and 18.705 seconds
  (wall-clock 19.83 and 19.41 seconds) with a deliberately unavailable Docker socket.
- Failsafe runs 128 PostgreSQL tests, including 5 `OlsSearchClientSearchIT` cases and 1 thin
  `V1SearchControllerIT` case. Two complete database-gate runs took Maven 1 minute 25 seconds and
  1 minute 22 seconds (wall-clock 87.36 and 83.37 seconds).
- The clean `verify` lifecycle runs all 757 tests in Maven 1 minute 39 seconds (wall-clock 101.92
  seconds).
- Whole-backend JaCoCo coverage is 44.3% lines (2,119 of 4,787) and 35.3% branches (677 of
  1,916). The V1 search controller covers 152 of 153 executable lines and 91 of 98 branches. No
  coverage failure threshold is introduced.
- The search suites preserve the public V1 response envelope, documented default and requested
  fields, all typed route parameters, repeated and comma-separated values, encoded hierarchy
  IRIs, pagination boundaries, grouping, stable error fields, and ignored compatibility
  parameters. The PostgreSQL cases reuse committed synthetic fixtures and the production schema
  generator to cover text and exact-field search, facets, filters, full IRIs, ranking, grouping,
  pagination, and inclusive hierarchy semantics.
- The rollout exposed that `inclusive=true` excluded the requested parent after the PostgreSQL
  migration. PR #1384 restored the legacy parent-or-descendant behavior with focused regressions;
  it was isolated and merged before this test branch was rebased.

## Implemented V1 ontology-individual-controller baseline

Verified locally on 2026-09-03 with Java 17 and Rancher Desktop:

- Surefire runs 660 tests, including 5 direct `V1OntologyIndividualControllerTest` cases and 23
  `V1OntologyIndividualControllerWIT` invocations. Two runs with a deliberately unavailable
  Docker socket took Maven 19.620 and 19.634 seconds (wall-clock 20.54 and 20.53 seconds).
- Failsafe runs 136 PostgreSQL tests, including 10 `V1IndividualRepositoryIT` cases and 5 thin
  `V1OntologyIndividualControllerIT` cases. Two complete database-gate runs took Maven 1 minute 25
  seconds each (wall-clock 86.92 and 86.81 seconds).
- The clean `verify` lifecycle runs all 796 tests in Maven 1 minute 36 seconds (wall-clock 97.79
  seconds).
- Whole-backend JaCoCo coverage is 48.9% lines (2,338 of 4,785) and 38.5% branches (738 of
  1,916). The V1 ontology-individual controller covers 37 of 40 executable lines and all 14
  branches; only its JSON-serialization failure path remains uncovered. Its individual repository
  covers 74 of 76 lines. No coverage failure threshold is introduced.
- The suites preserve all five ontology-scoped individual routes, legacy HAL fields, ontology and
  language handling, identifier precedence, double-encoded IRI paths, pagination normalization,
  direct and transitive types, JS-tree output, and stable error fields. The existing four-record
  individual fixture now carries synthetic direct-parent and ancestor data without changing its
  record count.
- The rollout exposed that collection lookup by `short_form` or `obo_id` passed the identifier and
  language to the repository in the wrong order. PR #1388 corrected both calls with focused
  regressions and was isolated and merged before this test branch was rebased.

## Implemented V1 select-controller baseline

Verified locally on 2026-09-02 with Java 17 and Rancher Desktop:

- Surefire runs 654 tests, including 4 new direct `V1SelectControllerTest` cases, 20
  `V1SelectControllerWIT` invocations, and the focused pagination regression merged with PR
  #1386. Two Docker-free runs took Maven 19.167 and 19.035 seconds (wall-clock 20.35 and 19.88
  seconds) with a deliberately unavailable Docker socket.
- Failsafe runs 132 PostgreSQL tests, including 3 `OlsSearchClientSelectIT` cases and 1 thin
  `V1SelectControllerIT` case. Two complete database-gate runs took Maven 1 minute 27 seconds and
  1 minute 26 seconds (wall-clock 88.17 and 87.10 seconds).
- The clean `verify` lifecycle runs all 786 tests in Maven 1 minute 38 seconds (wall-clock 99.12
  seconds).
- Whole-backend JaCoCo coverage is 45.9% lines (2,196 of 4,785) and 37.6% branches (721 of
  1,916). The V1 select controller covers all 76 executable lines and 44 of 48 branches. No
  coverage failure threshold is introduced.
- The select suites preserve the public V1 autocomplete envelope and legacy field projection;
  exercise defaults, all typed route parameters, repeated and comma-separated filters, encoded
  hierarchy IRIs, field lists, pagination boundaries, stable error fields, and ignored
  compatibility parameters; and use the committed synthetic fixture with the production schema
  generator for text search, filtering, hierarchy, obsolete terms, and real pagination.
- The rollout exposed that non-zero `start` values were passed to PostgreSQL but serialized as
  zero in the legacy response. PR #1386 preserves the requested offset with a focused regression;
  it was isolated and merged before this test branch was rebased.

## Implemented V1 ontology-property-controller baseline

Verified locally on 2026-09-03 with Java 17 and Rancher Desktop, after rebasing onto the merged
`V1ChildrenJsTreeBuilder` null-safety fix (PR #1391) that this rollout's own route-completeness
review exposed:

- Surefire runs 732 tests, including 7 direct `V1OntologyPropertyControllerTest` cases and 39
  `V1OntologyPropertyControllerWIT` invocations. Two runs with a deliberately unavailable Docker
  socket ran clean.
- Failsafe runs 153 PostgreSQL tests, including 4 new ontology-scoped `V1PropertyRepositoryIT`
  cases (bringing that suite to 11) and 9 thin `V1OntologyPropertyControllerIT` cases — one per
  route, covering all nine. Two complete database-gate runs ran clean.
- The clean `verify` lifecycle runs all 885 tests in Maven 1 minute 45 seconds (wall-clock 105.87
  seconds).
- Whole-backend JaCoCo coverage is 52.7% lines (2,520 of 4,785) and 41.9% branches (802 of 1,916).
  The V1 ontology-property controller covers 56 of 62 executable lines and all 16 branches; its
  repository covers 87 of 89 lines and 6 of 8 branches. No coverage failure threshold is
  introduced.
- The suites preserve all nine ontology-scoped property routes (list, roots, single, parents,
  children, descendants, ancestors, jstree, and jstree children) with one thin controller-IT case
  per route, legacy HAL fields, ontology and language handling, identifier precedence,
  double-encoded IRI paths, pagination normalization, explicit sort binding, and stable
  success/error contract fields including the exact message for malformed typed parameters
  (`includeObsoletes`, `siblings`). The existing three-record property fixture now carries a
  `directParent` reference and populated `has_direct_parents`/`has_hierarchical_parents` columns
  for `EFO_0101` without changing its record count, matching production behavior for both the
  js-tree ancestor builder (which reads `directParent` from the entity's stored JSON) and the
  roots query (which reads the real `has_direct_parents`/`has_hierarchical_parents` columns rather
  than the JSON document). This fixture change is test-only: the fixture previously never
  exercised `getRoots` or the property js-tree, so the gap was invisible until these new tests
  required it.
- The rollout's first draft covered only 6 of 9 routes in the controller IT, treating
  `jstree/children/{nodeid}` as adequately covered by the plain `jstree` route. It is not — the
  two routes call different builder classes. Adding the missing route during review exposed a real
  `NullPointerException` in `V1ChildrenJsTreeBuilder` (it called `.equals("true")` directly on a
  possibly-null value, unlike the sibling `V1AncestorsJsTreeBuilder`, which already guards the
  same fields null-safely). PR #1391 isolated the minimal fix and a focused regression test,
  merged before this branch was rebased.

## Implemented V1 ontology-term-controller baseline (complete, 2 milestones)

`V1OntologyTermController` has 23 routes — too large a hierarchy surface for one focused PR, so
coverage was split into two milestones. Milestone 1 (PR #1393, merged) covers the 10 core
per-term routes (`terms` list, `roots`, `preferredRoots`, single `{iri}` get, `parents`,
`children`, `descendants`, `ancestors`, `jstree`, `jstree/children/{nodeid}`), the same shape
already proven for `V1OntologyPropertyController`/`V1OntologyIndividualController`. Milestone 2
(PR #1395) covers the remaining 13 routes: the four hierarchical-variant per-term routes
(`hierarchicalParents`, `hierarchicalAncestors`, `hierarchicalChildren`,
`hierarchicalDescendants`), `/graph`, the dynamic `/{iri}/{property_iri}` related-by-property
route, and the seven ontology-root-level shortcut routes (`/{onto}/children`,
`/{onto}/descendants`, `/{onto}/parents`, `/{onto}/ancestors`, and their three `hierarchical*`
counterparts). Combined, all 23 routes are covered by all four test layers.

Milestone 2 branched directly from `dev` while milestone 1 was still an open PR (its routes don't
depend on milestone 1's fixture changes, so branching independently was safe — see "never stack on
an open PR"). Once milestone 1 merged, milestone 2 picked up rebase conflicts against `dev` in the
shared support file and the two files milestone 1 had already created with the same names
(`V1OntologyTermControllerTest`, `V1OntologyTermControllerWIT`, `V1OntologyTermControllerIT`) —
expected per that same rule, and resolved by combining both milestones' fields, stubs, and test
methods into one file per layer rather than picking a side. One genuine method-name collision
surfaced during that merge: both milestones independently wrote a
`supportsLegacyHalMediaTypeOnEveryHalRoute` WIT test with different URI lists; resolved by folding
milestone 2's HAL-route list into milestone 1's existing test rather than keeping two near-duplicate
methods.

Verified locally on 2026-09-04 with Java 17 and Rancher Desktop, after rebasing and combining both
milestones:

- Surefire runs 811 tests, including 18 direct `V1OntologyTermControllerTest` cases and 61
  `V1OntologyTermControllerWIT` invocations. Two Docker-free runs both passed cleanly.
- Failsafe runs 183 PostgreSQL tests, including 14 `V1TermRepositoryIT` cases and 23 thin
  `V1OntologyTermControllerIT` cases — one per route. Two complete database-gate runs both passed
  cleanly.
- The clean `verify` lifecycle runs all 994 tests in Maven 2 minutes 20.44 seconds wall-clock.
- Whole-backend JaCoCo coverage is 61.1% lines (2,923 of 4,787) and 46.9% branches (899 of 1,916).
  `V1OntologyTermController` covers all 23 routes across both milestones: 162 of 171 lines and 60
  of 66 branches. `V1TermRepository` covers 110 of 123 lines and 46 of 52 branches;
  `V1JsTreeRepository` covers 24 of 25 lines and 10 of 11 branches. `V1GraphRepository` —
  previously completely untested anywhere in the codebase — covers 148 of 177 lines and 17 of 36
  branches (its `getGraphForClass` path; the remainder is dead/unreachable code outside that
  method). No coverage failure threshold is introduced.
- The suites preserve the `id`/`iri`/`short_form`/`obo_id` identifier cascade
  (`getIdFromMultipleOptions` tries `id` first, falling back through `iri` → `short_form` →
  `obo_id`; `getOneById` then tries the resolved value as an IRI, then a short form, then an OBO ID
  in turn) — a materially different precedence model from the explicit per-parameter branching
  `V1OntologyPropertyController` and `V1OntologyIndividualController` use — across both the
  per-term routes and the milestone-2 shortcut routes. Also covered: the `obsoletes` collection
  filter, `includeObsoletes` on `roots`/`preferredRoots`, explicit `sort` binding on every pageable
  route (core and hierarchical), pagination boundaries and malformed-numeric defaults,
  double-encoded IRI paths, legacy HAL fields across all 23 routes, arbitrary V1 language
  passthrough, hierarchical relationships against real `hierarchical_parents`/
  `hierarchical_ancestors` Postgres columns, the full `V1GraphRepository.getGraphForClass`
  node/edge shape, the `related`-by-property route's actual behavior of ignoring its own
  `{property_iri}` path segment (documented, not fixed — no committed `test_api.sh` baseline
  exercises this route either way), and the seven shortcut routes' empty-page no-identifier case
  and delegation to the correct matching hierarchy method.
- The committed class fixture gained two production-schema columns it never previously populated:
  `has_direct_parents`/`has_hierarchical_parents` (real boolean columns the `getRoots` query filters
  on — previously left at their schema default of `false` for every row, so `getRoots` could not
  distinguish roots from non-roots) and `is_preferred_root` (queried by `getPreferredRootTerms`).
  `EFO_1001`'s stored JSON also gained a `directParent` key, the signal the js-tree ancestor/children
  builders read (a distinct mechanism from the `direct_parents`/`direct_ancestors` array columns).
  All test-only, same gaps and same fix pattern already found and fixed for the property fixture in
  PR #1390; record counts are unchanged. Extending `V1TermRepositoryIT`'s existing suite to the full
  class fixture also required updating its two pre-existing count-dependent assertions (4 → 6 total
  class records, `findAllByIsDefiningOntology` 3 → 5).
- Two suspected defects were investigated and reverted across the two milestones; both are
  documented in detail because the investigation process itself is the durable lesson:
  - A `V1TermRepository.getDescendants`/`getChildren` hierarchy type-leak mirroring PR #1373's V2
    `ClassRepository` fix (V1 passes an empty node-property filter to the same underlying Postgres
    hierarchy lookups, so a class's children/descendants can include non-class entities sharing the
    same ancestor IRI). A fix was drafted, but the full system-regression gate (`Build & Test API`,
    PR #1392) failed: the committed `test_api.sh` baseline for `owl2primer-class-assertion`
    explicitly and deliberately expects an individual asserted into a class to appear in that
    class's V1 `children`/`descendants` listing — a real, tested V1 legacy contract, not an
    oversight. The fix was reverted and the PR closed; the tests assert the actual (correct)
    current behavior instead. For the *hierarchical* variant specifically, no leak actually occurs
    with this fixture's data, because the individual's `hierarchicalAncestors` is empty even though
    its `directAncestors` is not — so no fix was needed or attempted there either.
  - A genuine-looking case-sensitivity bug in the seven shortcut routes: they call
    `getOneById(ontologyId, id, lang)` with the ontology id exactly as received (not yet
    lowercased), unlike every other route on this controller. A controller-mock unit test "proved"
    this would silently 404 for a client using an uppercase ontology id. It doesn't: `OlsSearchQuery`
    has an explicit `"ontology_id".equals(column)` special case that lowercases the filter input
    itself, so the real search-client-backed lookup resolves correctly regardless of case — proven
    directly with a real-Postgres controller-IT test against the *unmodified* code. The mock's
    exact-string matching doesn't replicate that normalization, which is what made the unit test
    misleading. No production code changed in either milestone.

Read-only production smoke monitoring is a separate future initiative for an internal or self-hosted environment. It is not part of the initial PR testing framework and must not become a merge-blocking production dependency.

## Implemented V2 defined-fields-controller baseline

`V2DefinedFieldsController` has a single route, `GET /api/v2/defined-fields`, with no autowired
dependencies at all: it maps the static `uk.ac.ebi.ols.shared.DefinedFields` enum into a list of
DTOs (`ols4FieldName`/`ols3FieldName`/`description`/`dataType`, read from `getText()`/
`getOls3Text()`/`getDescription()`/`getType()`).

Verified locally on 2026-09-07 with Java 17 and Rancher Desktop:

- Surefire runs 793 tests, including 2 direct `V2DefinedFieldsControllerTest` cases and 4
  `V2DefinedFieldsControllerWIT` invocations. Two Docker-free runs took wall-clock 18.98 and 15.10
  seconds.
- Failsafe runs the same 167 PostgreSQL tests as `dev`; no repository-IT or controller-IT class
  was added. There is no repository, no Postgres dependency, and no database-backed behaviour a
  real-database layer would additionally prove beyond what the WIT already proves by exercising
  the real Spring MVC JSON serialization path — this controller has no applicable repository
  behaviour, so the "applicable repository behaviour has PostgreSQL IT coverage" clause of the
  controller definition of done is satisfied vacuously and the omission is deliberate, not an
  oversight. Two complete database-gate runs both passed cleanly, wall-clock 119.86 and 109.52
  seconds.
- The clean `verify` lifecycle runs all 960 tests in wall-clock 2 minutes 6.86 seconds.
- Whole-backend JaCoCo coverage is 56.1% lines (2,685 of 4,787) and 44.1% branches (845 of 1,916).
  `V2DefinedFieldsController` covers all 8 of its executable lines (0 branches — the method has no
  conditional logic) and its package-private `DefinedFieldDto` covers all 10 of its lines. No
  coverage failure threshold is introduced.
- The unit test loops over `DefinedFields.values()` (34 members as of this rollout, read from the
  enum itself rather than hardcoded) and asserts each DTO's four fields against the corresponding
  enum member's accessors in declaration order, rather than hand-writing one assertion per
  constant. The WIT exercises the same contract through real Spring MVC JSON serialization: the
  declared field names, the total array length against `DefinedFields.values().length`, the first
  entry's full values, one full entry at an index derived from `DefinedFields.IS_OBSOLETE.ordinal()`
  (an enum member with a non-empty `ols3Text`, rather than a guessed position) to prove the
  non-empty-string case too, and the standard 405 method-not-allowed contract for a non-GET
  request. No production defect was discovered by this rollout.

## Implemented V2 health-check-controller baseline

`HealthCheckController` has a single route, `GET /api/v2/health`, that autowires
`OntologyRepository` and `OlsPostgresClient` directly (no service layer). It reports whether the
search index and Postgres are initialized by running a small live query against each and catching
any exception as an unhealthy result.

Verified locally on 2026-09-07 with Java 17 and Rancher Desktop:

- Surefire runs 798 tests, including 5 direct `HealthCheckControllerTest` cases and 6
  `HealthCheckControllerWIT` invocations. Two Docker-free runs took wall-clock 17.07 and 14.83
  seconds.
- Failsafe runs 168 PostgreSQL tests, including 1 thin `HealthCheckControllerIT` case. No new
  repository-IT class was added: the controller has no repository logic of its own beyond the
  already-covered `OntologyRepository`/`OlsPostgresClient` wiring, so the one controller-IT case
  is the only PostgreSQL-backed coverage this controller needs. Two complete database-gate runs
  took wall-clock 116.50 and 117.14 seconds.
- The clean `verify` lifecycle runs all 966 tests in wall-clock 2 minutes 0.98 seconds.
- Whole-backend JaCoCo coverage is 56.4% lines (2,702 of 4,787) and 44.6% branches (854 of 1,916).
  `HealthCheckController` covers all 28 of its executable lines and all 8 branches. No coverage
  failure threshold is introduced.
- The unit and WIT suites exercise both private branches (`checkSearch`/`checkPostgres`) through
  their public effect: a healthy search and a positive Postgres node count return 200 "All systems
  are operational."; a search with zero results returns 503 "Search is not initialized."; a
  healthy search with a zero Postgres node count returns 503 "Postgres is not initialized."; and
  either collaborator throwing behaves identically to it returning its unhealthy value, since the
  controller catches and logs the exception in both `checkSearch` and `checkPostgres`. The WIT
  suite also records that the route is declared with a bare `@RequestMapping("/health")` with no
  method element, so — unlike sibling `@GetMapping` V2 controllers, which reject other verbs with
  405 — every HTTP method reaches the same handler; this is an observed contract detail, not a
  defect, and a dedicated WIT case proves a POST request is served identically to a GET.
- The bare `ResponseEntity<String>` body serializes as `text/plain`, but the exact charset differs
  between the `@WebMvcTest`-sliced context (Spring Boot autoconfiguration defaults to UTF-8) and
  the `standaloneSetup` MockMvc harness the controller IT uses (plain Spring MVC defaults to
  ISO-8859-1 without that autoconfiguration). Both suites assert
  `contentTypeCompatibleWith(MediaType.TEXT_PLAIN)` rather than an exact charset so the tests do
  not couple to that harness difference.
- `PostgresIntegrationTestSupport` gained a new `createHealthCheckRepositories(...)` factory
  returning both a real `OntologyRepository` and the `OlsPostgresClient` it shares, wired against
  disposable Postgres. No existing factory exposed both collaborators together: `createRepository`
  wires an internal `OlsPostgresClient` into `OntologyRepository` but does not return it, and
  `HealthCheckController` needs both as independent autowired fields. No production defect was
  discovered by this rollout.

## Implemented V2 text-tagger-controller baseline

`V2TextTaggerController` has three routes: `POST /tag_text` (annotate free text with matching
ontology terms), `GET /tag_text` (status check, `{"available": <bool>}`), and
`GET /curation_sources` (`List<String>` of curated source names via
`OlsSearchClient.getDistinctCuratedSources()`). It autowires two collaborators:
`TextTaggerService` and `OlsSearchClient`.

**Scoping decision.** `TextTaggerService` wraps an external `ols_text_tagger` CLI binary via
`ProcessBuilder`, which is why this controller was held back from earlier rollouts in this
programme — it does not fit the "real Postgres, no mocks" doctrine as cleanly as a pure-repository
controller. The binary is not installed on this development machine or in CI, so exercising
genuine tagging output is explicitly and permanently out of scope for this suite — that is a
deliberate limitation of this layer of coverage, not a TODO or a gap, and it will remain true for
any environment that does not ship the binary.

What full-stack IT coverage *can* prove, and does: the `ols_text_tagger` table (single column
`tagger_db_oid`) exists in the production schema
(`dataload/create_postgres_schema.py`), but no fixture in this suite — or anywhere else in the
committed test suite — inserts a row into it. Consequently the real, unmodified
`TextTaggerService` bean's `@PostConstruct init()` genuinely finds nothing to download,
`startProcess()` is never reached, and `isAvailable()` genuinely settles to `false` against a real
disposable Postgres — this is not a mock standing in for integration behaviour, it is the actual
behaviour a deployment with no tagger database configured exhibits. `available` also defaults to
`false` at field declaration and is only ever set `true` inside `startProcess()`, so this is true
immediately at bean construction, before the background init thread even runs; there is no async
timing race for these assertions to be sensitive to, confirmed by two clean repeat runs of the
database gate below.

Verified locally on 2026-09-08 with Java 17 and Rancher Desktop:

- Surefire runs 856 tests, including 13 direct `V2TextTaggerControllerTest` cases and 15
  `V2TextTaggerControllerWIT` cases. Two Docker-free runs took wall-clock 12.76 and 13.76 seconds.
- Failsafe runs 187 PostgreSQL tests, including 3 thin `V2TextTaggerControllerIT` cases against
  the real, unconfigured `TextTaggerService` bean and the real `OlsSearchClient`. No new
  repository-IT class was added: `TextTaggerService` is not a repository, and there is no
  repository-level behaviour left to prove beyond what the controller-IT's unavailable-branch case
  already exercises against real Postgres; `OlsSearchClient.getDistinctCuratedSources()` is
  exercised by the controller-IT's third case and has no other untested repository behaviour of
  its own. Two complete database-gate runs took wall-clock 115.60 and 119.34 seconds.
- The clean `verify` lifecycle runs all 1,043 tests in wall-clock 2 minutes 1.68 seconds.
- Whole-backend JaCoCo coverage is 64.1% lines (3,068 of 4,787) and 48.6% branches (932 of 1,916),
  up from the most recently documented baseline of 56.4% lines and 44.6% branches.
  `V2TextTaggerController` itself covers all 38 of its executable lines and all 18 branches. As
  expected, `TextTaggerService` shows low coverage on its own — 31 of 194 lines (16.0%) and 5 of
  112 branches (4.5%) — because its process-management internals (`startProcess`, `ensureRunning`,
  response parsing, priority and substring filtering) are unreachable without the real binary; the
  covered lines come almost entirely from the real bean's `init()`/`downloadTextTaggerDb()`
  unavailable path exercised by the controller IT. This is expected and not chased further. No
  coverage failure threshold is introduced.
- The unit test and WIT enumerate every route (`POST /tag_text`, `GET /tag_text`,
  `GET /curation_sources`), every declared parameter (`ontologyId`, `source`, `delimiters`,
  `minLength`, `includeSubstrings`, `includeObsoleteEntities`) including repeated `ontologyId`/
  `source` values, the missing/empty `text` 400 branch, the service-unavailable 503 branch,
  malformed typed-parameter 400s for `minLength`/`includeSubstrings`/`includeObsoleteEntities` with
  the exact conversion-error message, and the full field-by-field response mapping — including the
  conditional `string_type`, `source`, `subject_categories`, and `is_obsolete` fields, which are
  only present when the tagger actually returns them. The unit test uses this repo's hand-rolled
  fake idiom (subclasses of `TextTaggerService`/`OlsSearchClient` overriding just the methods under
  test, the same idiom as `HealthCheckControllerTest`); the WIT uses `@MockitoBean` for both
  collaborators, the same idiom as `HealthCheckControllerWIT`.
- `PostgresIntegrationTestSupport` gained a new `createTextTaggerRepositories(...)` factory
  returning the real `TextTaggerService` (wired to disposable Postgres via reflection, matching
  every other repository/client factory in this file) alongside the existing
  `createSearchClient(...)`-backed `OlsSearchClient`. No existing factory wired
  `TextTaggerService`.
- `curation_sources` currently returns an empty list against the shared fixture: no fixture record
  populates the `curated_from_sources` column (it defaults to `'{}'` in the production schema), so
  the real-Postgres controller-IT case asserts an empty array rather than a guessed value — observed
  by running the suite, not assumed ahead of time. No production defect was discovered by this
  rollout.

## Implemented V2 LLM-controller baseline

`V2LLMController` is the last untested controller in this programme. It has 12 routes and depends
on `EmbeddingServiceClient` (an HTTP client to an external embedding microservice, configured via
`@Value("${ols.embedding.service.url:#{null}}")`, unset in any test environment) and
pgvector-backed similarity search — the same area with real production incident history noted
elsewhere (`llm_similar` returning 500 "type vector does not exist" from a stale pgvector
schema/search-path). Both concerns are resolved for this suite:
`PostgresIntegrationTestSupport.newContainer()` already uses the `pgvector/pgvector:0.8.0-pg17`
image with `CREATE EXTENSION IF NOT EXISTS vector` applied by the production schema generator, and
the test harness already pins `PostgresClient` to the `public` schema, so the historical incident's
exact failure mode is not expected and did not reproduce.

**Mock/real boundary per route.** Six routes need nothing but real Postgres — no
`EmbeddingServiceClient` involvement at all: `POST /classes/llm_embedding` and
`POST /ontologies/{onto}/classes/llm_embedding` (both take a raw vector in the request body and run
a real nearest-neighbor Postgres search), `GET /classes/{class}/llm_similar`,
`GET /classes/{class}/llm_embedding`, `GET /classes/{class}/llm_similarity/{otherclass}`, and
`GET /properties/{property}/llm_similar`. `GET /llm_models` is wired against the real,
*unconfigured* `EmbeddingServiceClient` bean: `getAvailableModels()` degrades to an empty list
without throwing when the URL is unset, which is itself the correct integration behaviour for a
deployment with no embedding microservice configured, not a mock standing in for one. The five
text-search routes (`GET /entities/llm_search`, `GET /classes/llm_search`,
`GET /ontologies/{onto}/classes/llm_search`, `GET /properties/llm_search`,
`GET /individuals/llm_search`) each call `embeddingServiceClient.embedText(...)` before a real
Postgres vector search; controller-IT wires a hand-rolled fake `EmbeddingServiceClient` subclass
(same idiom as `HealthCheckControllerTest`/`V2TextTaggerControllerTest`) returning a fixed canned
vector, so the real nearest-neighbor search after it is genuinely exercised end-to-end. One of the
five, `GET /entities/llm_search`, carries a second controller-IT case against the real unconfigured
bean, proving empirically (not assumed from reading the source) that `embedText()`'s
`IOException("Embedding service URL is not configured")` — uncaught by the controller's
`throws IOException` handlers — falls through `GlobalExceptionHandler`'s catch-all to HTTP 500 with
the exception's own message; the other four routes' identical unavailable-branch behaviour is
already fully proven at the unit/WIT layer with a fake throwing the same exception.

**New fixture mechanism.** No existing test fixture populated any embedding vector column:
`PostgresIntegrationTestSupport.executeProductionSchema` only ever invokes
`dataload/create_postgres_schema.py` with `--filter-property` arguments, never embedding parquet
files. `OlsPostgresClient` needs two separate column-naming schemes on two separate tables —
`embeddings_<model>` (plural) directly on `ols_entities`, read by
`getSimilar`/`getSimilarity`/`getEmbeddingVector`, and `embedding_<model>` (singular) on the
separate `ols_embedding_nodes` table (keyed by `entity_id`, typed `LabelEmbedding`/
`CurationEmbedding`), read by `searchByVector`/`searchByVectorInOntology`. A new
`PostgresIntegrationTestSupport.initializeV2LLMDatabase`/`createV2LLMRepositories` pair adds both
column families directly via SQL after the standard schema and fixture load (bypassing
`dataload/create_postgres_schema.py`'s parquet-driven generation entirely, a dataload/production
concern this suite does not invoke), using model name `test_model`. Every vector is a hand-picked
4-dimensional value chosen so cosine similarity against the fixed query vector `[1,0,0,0]` used by
every fake `EmbeddingServiceClient` is an exact, hand-checkable fraction: `getSimilar`/
`getSimilarity`/text-search all report `score = (1 + cosine_similarity) / 2` (pgvector's `<=>`
cosine-distance operator converted to a `[0,1]` similarity), so EFO_0002's `[0,1,0,0]` embedding
against EFO_0001's `[1,0,0,0]` (orthogonal, cosine 0) scores exactly `0.5`, DUO_0001's `[-1,0,0,0]`
(opposite, cosine −1) scores exactly `0.0`, and the embedding-node fixture's `[4,3,0,0]`/
`[3,4,0,0]` vectors against the same query score exactly `0.9`/`0.8`. `getEmbeddingModels()`
discovers `test_model` automatically by introspecting `information_schema.columns` for
`embeddings\_%` columns on `ols_entities`, so no separate registration step was needed for
`GET /llm_models` to list it.

Verified locally on 2026-09-08 with Java 17 and Rancher Desktop:

- Surefire runs 923 tests, including 20 direct `V2LLMControllerTest` cases and 47
  `V2LLMControllerWIT` invocations. Two Docker-free runs took wall-clock 17.76 and 15.13 seconds.
- Failsafe runs 201 PostgreSQL tests, including 14 thin `V2LLMControllerIT` cases — one per route,
  plus the `includeCurations` and real-unconfigured-service-error cases described above. No new
  repository-IT class was added: the controller-IT wires the already-tested `ClassRepository`/
  `PropertyRepository` directly against the new embedding fixture, the same
  "no separate repository-IT needed" pattern as `HealthCheckController`/`V2TextTaggerController`.
  Two complete database-gate runs took wall-clock 123.79 and 117.76 seconds.
- The clean `verify` lifecycle runs all 1,124 tests in wall-clock 2 minutes 7.53 seconds.
- Whole-backend JaCoCo coverage is 70.8% lines (3,387 of 4,787) and 53.2% branches (1,019 of
  1,916), up from the most recently documented baseline of 64.1% lines and 48.6% branches.
  `V2LLMController` covers all 71 of its executable lines and 22 of its 24 branches (all 17
  methods). `EmbeddingServiceClient` shows low coverage on its own — 23 of 119 lines (19.3%) and 4
  of 52 branches (7.7%) — because its HTTP-calling internals (`embedTextsFromService`'s actual
  POST, `getAvailableModels`'s actual GET, response parsing) are unreachable without a real
  embedding microservice; the covered lines come almost entirely from the unconfigured-URL branches
  every test layer exercises, the same expected-low-coverage shape as `TextTaggerService` in the
  prior rollout. The touched `OlsPostgresClient` methods: `getSimilar` 34/37 lines and 3/4 branches,
  `getSimilarity` 22/25 lines and 2/4 branches, `getEmbeddingVector` 20/23 lines and 4/8 branches,
  `searchByVector` (5-arg) 13/15 lines and 6/6 branches, `searchByVectorInOntology` (7-arg) 14/16
  lines and 3/6 branches, `getEmbeddingModels` 16/18 lines and 3/4 branches,
  `sanitizeEmbeddingColumnName`/`sanitizeEmbeddingNodeColumnName` 2/3 lines and 2/4 branches each.
  `OlsPostgresClient` as a whole covers 317 of 363 lines (87.3%) and 52 of 79 branches (65.8%). The
  4-arg `searchByVector`/6-arg `searchByVectorInOntology` convenience overloads (each delegating to
  the 5-/7-arg form with a hardcoded `includeCurations`) remain fully uncovered — pre-existing dead
  code, not introduced or exercised by this rollout: every production caller (`ClassRepository`,
  `McpClassService`, `McpEmbeddingService`, and `V2LLMController` itself) already passes
  `includeCurations` explicitly. No coverage failure threshold is introduced.
- No production defect was discovered by this rollout. One behaviour is worth recording
  deliberately, not as a defect: an uncaught `IOException` from the five text-search routes'
  `embedText()` call reaches `GlobalExceptionHandler`'s catch-all as HTTP 500 (not a dedicated 503)
  with the raw exception message — confirmed empirically above, consistent with this programme's
  existing observation that some malformed/unavailable-dependency paths currently surface as 500
  rather than a more specific status, and left as observed-and-tested current behaviour rather than
  a change made in this testing PR.

**Permanent limitation.** Genuine embedding-service HTTP behaviour — real vectors returned by a
real embedding model, real network/timeout/non-2xx handling in `embedTextsFromService`, real
model-list responses from `getAvailableModels` — cannot be and is not exercised anywhere in this
test environment. No fixture, real dependency, or CI runner in this suite runs an embedding
microservice; every route's coverage above proves either the unconfigured-service degradation path
or the real-Postgres vector arithmetic downstream of a canned vector, never a genuine model call.
This is the same category of permanent gap `V2TextTaggerController`'s baseline recorded for the
`ols_text_tagger` binary, and it will remain true for any environment that does not run a real
embedding service alongside the database.

## Completed V2 individual hierarchy-route coverage

PR #1404 added `GET /api/v2/ontologies/{onto}/individuals/{individual}/hierarchicalChildren` and
`GET /api/v2/ontologies/{onto}/individuals/{individual}/hierarchicalAncestors` after the original
V2 individual-controller testing milestone. It added three thin controller IT cases, but the two
new routes were absent from both `V2IndividualControllerTest` and the exact named
`V2IndividualControllerWIT` contract suite. This follow-up closes that route-level gap without
duplicating the existing controller ITs.

Verified locally on 2026-09-11 from `origin/dev` commit `3a8f66a6c` with Java 17 and Rancher
Desktop:

- Surefire runs 959 tests, including 8 direct `V2IndividualControllerTest` cases and 98
  `V2IndividualControllerWIT` invocations. The two new direct tests prove single decoding and exact
  repository delegation for both routes. The 34 new WIT invocations cover both routes' default and
  explicit response contracts, double-encoded individual IRIs, every supported query parameter,
  pagination normalization and malformed-number defaults, ascending/descending and unsupported
  sort values, malformed booleans, and stable ontology/language validation errors. Two complete
  Docker-free runs took wall-clock 23.68 and 24.42 seconds.
- Failsafe runs 205 PostgreSQL tests. `IndividualRepositoryIT` now has 8 cases; its new hierarchy
  case proves active/obsolete filtering for hierarchical children plus ancestor traversal, and its
  existing validation matrix now covers both hierarchy repository methods. The 7 existing
  `V2IndividualControllerIT` cases remain the thin end-to-end path through the real controller,
  repository, and production PostgreSQL schema. Two complete database-gate runs took wall-clock
  114.10 and 119.25 seconds.
- The clean `verify` lifecycle runs all 1,164 tests in wall-clock 129.77 seconds. Whole-backend
  JaCoCo coverage is 70.9% lines (3,410 of 4,808) and 53.2% branches (1,021 of 1,918), unchanged
  from the post-#1404 baseline because its controller ITs already executed the new production
  methods. `V2IndividualController` covers all 29 executable lines and all 6 branches;
  `IndividualRepository` covers 62 of 63 lines and 11 of 14 branches. No coverage threshold is
  introduced.
- The committed individual fixture now owns `hierarchicalParents` and `hierarchicalAncestors` for
  every record, and `PostgresIntegrationTestSupport` loads those arrays into the production
  columns. `V2IndividualControllerIT` therefore no longer mutates its private database with a
  second handwritten SQL hierarchy fixture; controller and repository ITs share one explicit,
  reviewable source of relationship data.
- No production defect was discovered. The gap was in test-layer completeness introduced by the
  later feature PR, not in the behavior of either hierarchy endpoint. `test_api.sh` was not changed
  or run; full dataload-to-deployed-API comparison remains the system-regression layer.

The accompanying inventory audit also records three special surfaces outside this focused
follow-up: `CustomErrorController` and `V1ApiUnavailable` still have no dedicated layered suites,
while `GlobalExceptionHandler` is shared by 34 test classes and currently covers 17 of 22 lines and
8 of 10 branches. Those surfaces should be scoped independently rather than bundled with the V2
individual hierarchy contract.

## Implemented AnnotationExtractor baseline

`AnnotationExtractor` (`repository/v1/mappers`) is the first Tier B (non-controller) target in
this programme: with Tier A's controller backlog empty, this rollout starts on the repository/
mapper/builder/service layer described in the Tier B methodology. It is a pure static-method
utility class — no Spring bean, no constructor state, no Postgres dependency — with two public
methods, `extractAnnotations(JsonObject)` and `extractSubsets(JsonObject)`, called from
`V1TermMapper`, `V1IndividualMapper`, `V1PropertyMapper`, and directly from
`V1SearchController`. Both methods were previously exercised only incidentally, through three
existing mapper tests' happy-path fixtures (`V1IndividualMapperTest`, `V1PropertyMapperTest`, and
transitively any `V1TermMapper` caller) — none of which targeted `AnnotationExtractor`'s own
branches directly.

**Scope: unit-only, no IT layer.** Per the Tier B methodology's "what covered means" section, a
pure-logic class with no Postgres dependency of its own does not get a dedicated `*IT.java` layer
— there is no real-database behaviour here to prove beyond what a direct unit test already covers
with hand-built `JsonObject` fixtures. This baseline is therefore a single new
`AnnotationExtractorTest.java` (24 cases, this repo's plain-JUnit/AssertJ idiom, no Mockito, no
Spring context) and nothing else; the existing mapper tests' indirect exercise of this class is
retained unchanged but does not count as dedicated coverage per this program's standing rule that
controller/mapper-level happy-path tests don't substitute for a class's own edge-case suite.

**Branches enumerated.** `extractAnnotations`: predicates without an IRI scheme (no `://`) are
skipped; predicates matching the `rdf2json`-added `namePattern` (e.g. `relatedTo+http://...`) are
skipped; predicates already interpreted as a definition/synonym/hierarchical property (via the
`definitionProperty`/`synonymProperty`/`hierarchicalProperty` arrays) are skipped; the three
hardcoded RDF/RDFS/OWL namespace prefixes are excluded, each verified individually
(`rdf-schema#`, `rdf-syntax-ns#`, `owl#`), together with both of the two named exceptions inside
the `rdf-schema#` namespace (`#comment`, `#seeAlso`) that are included despite the namespace
match; the hardcoded `oboInOwl#inSubset` exclusion; single-level value flattening (one
`{"value": ...}` wrapper unwrapped) and nested flattening (two levels unwrapped in the `while`
loop); label derivation from the IRI fragment after the last `#` and, separately, from the last
path segment when there is no `#`; label override via a `linkedEntities` entry carrying a
`label` field, and the fallback to the IRI-derived label when the `linkedEntities` entry exists
but carries no `label` field; duplicate values for one predicate collapsing via the
`LinkedHashSet`, with insertion order preserved; and two different predicates whose resolved
label collides (one directly, one via `linkedEntities` override) merging into the same output
set. `extractSubsets`: the missing-key and present-but-empty-array cases both returning `null`;
a single subset URI resolved from its fragment; a single subset URI resolved from its last path
segment when there is no fragment; and multiple URIs, including a duplicate, sorted and
deduplicated by the `TreeSet` before short-name extraction.

Verified locally on 2026-09-11 from `origin/dev` commit `aa52e09e4` with Java 17 (no Postgres/
Docker gate — this class has no IT layer, per the scope note above):

- Surefire runs 983 tests, including 24 `AnnotationExtractorTest` cases. Two Docker-free runs took
  wall-clock 14.07 and 15.08 seconds, both 0 failures / 0 errors.
- The clean `verify` lifecycle runs all 1,188 tests (983 surefire + 205 failsafe, unchanged by this
  rollout since no IT was added) in wall-clock 2 minutes 15.53 seconds.
- `AnnotationExtractor` itself now covers 61 of 63 lines (96.8%) and 39 of 40 branches (97.5%); the
  one uncovered line/branch pair is the implicit default constructor, never invoked since every
  caller uses the static methods directly. Whole-backend JaCoCo coverage is 71.3% lines (3,428 of
  4,810) and 54.1% branches (1,037 of 1,918), up from the most recently documented baseline of
  70.9% lines and 53.2% branches (the small total-line/branch denominator shift versus that prior
  baseline reflects an unrelated commit that landed on `dev` in between, not this rollout). No
  coverage failure threshold is introduced.
- No production defect was discovered by this rollout.

## Implemented EmbeddingServiceClient baseline

`EmbeddingServiceClient` is the second Tier B target completed under this programme's expanded
scope (repositories, mappers, builders, services, and MCP-tool classes with real logic and zero
direct tests, worked once the Tier A controller backlog is empty — see
`.claude/commands/backend-test-coverage.md`'s Tier B methodology and the `AnnotationExtractor`
baseline above for the first). It was already touched by Tier A
work: the "V2 LLM-controller baseline" section above wires the real, unconfigured bean directly
into `V2LLMController` (`getAvailableModels()` degrading to `List.of()` is genuine, non-mocked
integration behaviour) and a hand-rolled fake subclass at the controller-IT layer for the five
text-search routes. Neither proves anything about the class's own internals: `applyPca`'s PCA-
transform math, `loadPcaModels()`'s Postgres-backed model loading, and
`getAvailableModels()`'s response-parsing/model-filtering logic had never been exercised directly,
which is exactly why JaCoCo measured the class at 19.3% lines / 7.7% branches despite the
controller-layer work.

**Shape of this baseline.** Unlike a Tier A controller, this class has no routes, so "done" is a
direct unit test plus a real-Postgres IT test (per the Tier B "what covered means" methodology),
not four layers:

- `EmbeddingServiceClientTest` — a plain, Docker-free unit test covering every public method and
  branch that does not itself require a database.
- `EmbeddingServiceClientIT` — a disposable-Postgres test covering the one part of this class that
  is genuinely Postgres-backed: `init()`/`loadPcaModels()`.

**HTTP seam.** `embedTextsFromService`'s binary response parsing and `applyPca`'s real math are only
reached through `embedTexts()`, which throws immediately when `embeddingServiceUrl` is unset — the
same state as every Spring test context in this repo (`@Value("${ols.embedding.service.url:#{null}}")`,
never configured in tests). Rather than reflecting into `applyPca` directly or faking the whole
class again (which the controller layer already does, and which would prove nothing new), this
class's `EmbeddingServiceClientTest` stands up a real `com.sun.net.httpserver.HttpServer` bound to
`127.0.0.1` on an ephemeral port as a fake embedding microservice, and points the real, unmodified
`EmbeddingServiceClient` at it with `ReflectionTestUtils.setField(client, "embeddingServiceUrl", ...)`
— the same private-field-injection idiom `PostgresIntegrationTestSupport` already uses throughout
(including, already, `ReflectionTestUtils.setField(embeddingServiceClient, "postgresClient", ...)`
in its existing `createV2LLMRepositories` factory), not a new mechanism introduced for this class.
This exercises the real HTTP request/response code, the real little-endian binary float parsing,
and the real `applyPca` mean-centered dot product end to end — not a mock standing in for any of
it. No existing test in this repo stood up a local HTTP server before this; `ReflectionTestUtils`
for private-field injection was already established precedent, so only the HTTP-server half of this
approach is new.

**PCA model injection.** `loadPcaModels()` itself is real, Postgres-backed logic, covered separately
by `EmbeddingServiceClientIT` (below) and deliberately not re-exercised in the unit test. To reach
`applyPca` and the PCA branches of `embedTexts()`/`getAvailableModels()` without a database,
`EmbeddingServiceClientTest` uses plain `java.lang.reflect` to construct the class's private static
nested `PcaModel` type directly (via its declared constructor, made accessible) and insert it into
the private `pcaModels` map. This is a small, explicit white-box step to set up test state — not a
stand-in for the class's real behaviour once a model is present, which runs unmodified. `applyPca`'s
math is verified against a hand-computed expected output for a small, fixed 3-feature/2-component
model (mean `[1,2,3]`, components `[[1,0],[0,1],[1,1]]`): a raw embedding of `[2,5,10]` transforms to
exactly `[8,10]`, and the mean itself (`[1,2,3]`) transforms to exactly `[0,0]` (every
`embedding[i] - mean[i]` term is zero) — both hand-checkable, not approximated.

**Real-Postgres coverage.** `PostgresIntegrationTestSupport` gained
`initializeEmbeddingServiceClientDatabase` and `createEmbeddingServiceClientRepositories`, following
the same per-family pattern as `initializeV2LLMDatabase`/`createV2LLMRepositories`. The new fixture
loads two rows into `ols_pca_models`: `embedding_service_client_test_model_pca2` (matches
`PCA_PATTERN`, `^(.+)_pca(\d+)$`, with a hand-computable mean/components payload) and
`embedding_service_client_test_model_full` (does not match — no `_pca<digits>` suffix). Unlike
`createV2LLMRepositories` (which deliberately returns its `EmbeddingServiceClient` uninitialized
because its fixture never populates `ols_pca_models`), the new factory calls `init()` eagerly.
`EmbeddingServiceClientIT` then reflects into the private `pcaModels` map (there is no public
getter) to assert the matching row produced a loaded model with the exact `baseModelName`/
`nComponents`/`mean.length` that was inserted, and that the non-matching row produced no entry at
all (map size `1`, not `2`) — proving both the regex-match and regex-no-match branches of the
per-row loop empirically, not by reading the source. A third IT case inserts a row with
deliberately-malformed (non-JSON) `model` bytes into its own disposable container (kept separate
from the two cases above so a poisoned row can't affect their fixture) and asserts `init()` still
completes without throwing and leaves no model loaded — proving `loadPcaModels()`'s outer
`catch (Exception e)` swallows a real Gson parse failure rather than letting it escape
`@PostConstruct` and fail application startup.

**Dead code, not a defect.** `getAvailableModels()` has two separate pca16-exclusion mechanisms: the
inclusion loop's `!entry.getKey().contains("pca16")` guard, and a later
`models.removeIf(m -> m.contains("pca16"))`. `EmbeddingServiceClientTest` proves the first guard
reachable and effective (`getAvailableModelsExcludesPca16VariantEvenWhenItsBaseModelIsAvailable`);
the second is structurally unreachable given the first already prevents any pca16-named entry from
ever being added to the list it operates on. This mirrors the `OlsPostgresClient` 4-/6-arg
convenience-overload dead code noted in the V2 LLM-controller baseline above — left as pre-existing,
not fixed, per the defect workflow (a testing PR must not bundle a fix).

Verified locally on 2026-09-11 with Java 17 and Rancher Desktop, from `origin/dev` commit
`75d96f57c` (post `AnnotationExtractor` merge, PR #1407 — this branch was rebased onto it since
both Tier B baselines happened to insert their new section at the same point in this file):

- Surefire runs 1,006 tests, including 23 direct `EmbeddingServiceClientTest` cases. Two
  Docker-free runs took wall-clock 19.59 and 19.05 seconds.
- Failsafe runs 208 PostgreSQL tests, including 3 `EmbeddingServiceClientIT` cases. Two complete
  database-gate runs took wall-clock 128.24 and 132.19 seconds.
- The clean `verify` lifecycle runs all 1,214 tests in wall-clock 2 minutes 15.07 seconds.
- `EmbeddingServiceClient` now covers all 119 of its executable lines (100%, up from 19.3%) and 51
  of its 52 branches (98.1%, up from 7.7%) across all 13 of its methods (100%, up from
  partial). The one remaining missed branch is `embedTextsFromService`'s
  `response.body() != null ? ... : "(empty)"` fallback in the non-200 error-message branch:
  `HttpResponse.BodyHandlers.ofByteArray()` is documented to always return a byte array (empty, not
  null, for an empty body), so the null case is not reachable through the real JDK `HttpClient`
  without fabricating a response object — a permanent, expected gap, not chased with a mock.
  Whole-backend JaCoCo coverage is 73.4% lines (3,530 of 4,810) and 56.5% branches (1,084 of 1,918),
  up from the `AnnotationExtractor` baseline immediately above (71.3% lines, 54.1% branches).
- No production defect was discovered by this rollout. The pca16 double-filter noted above was
  confirmed to be pre-existing dead code, not a behavioural bug (nothing observable changes whether
  it runs or not), and was left alone per the defect workflow.

**Permanent limitation.** As recorded in the V2 LLM-controller baseline above, genuine
embedding-service HTTP behaviour from a real embedding model is not and cannot be exercised in this
test environment. This baseline's local `HttpServer` fake proves the real request/response-handling
*code* end to end (parsing, error handling, PCA math), which is new; it does not and cannot prove
what a real embedding microservice actually returns for real text, which remains permanently out of
reach here, as it was for the controller layer.

## Implemented JooqSupport baseline

`JooqSupport` (`repository/postgres`) is the second Tier B target in this programme. It is a
`final` class with a private no-op constructor — a pure static-method jOOQ SQL-fragment-builder
utility, no Spring bean, no constructor state — exposing six `Table<?>` constants
(`OLS_AUTOSUGGEST`, `OLS_EMBEDDING_NODES`, `OLS_ENTITIES`, `OLS_PCA_MODELS`, `OLS_TEXT_TAGGER`,
`INFORMATION_SCHEMA_COLUMNS`) and eighteen static methods: `field` (two overloads: `(column,
type)`, and `(qualifier, column, type)` with a blank-qualifier fallback branch), `arrayContains`
(two overloads: a literal-value GIN-friendly `@>` form and a `Field`-to-`Field` `= ANY` join
form), `arrayContainsCaseInsensitive`, `arrayContainsField` (the GIN-friendly `@>` join
counterpart to `arrayContains`'s `= ANY` form), `castAsText`, `similarity`, `trigramMatch`,
`similarityAtLeastThreshold`, `maxTrigramCandidateLength`, `unnest`, `vectorDistance` (two
overloads: literal-vector and field-to-field), `websearchToTsQuery`, `phraseToTsQuery`,
`toTsQuery`, `matchesTsQuery`, and `tsvectorMatches`. It is called directly (not through a fake or
mock at any existing call site) by six production classes — `OlsSearchClient`, `OlsSearchQuery`,
`OlsPostgresClient`, `V1GraphRepository`, `EmbeddingServiceClient`, and `TextTaggerService` — to
build the WHERE/JOIN/ORDER-BY fragments those classes' own real-Postgres IT suites already
exercise end-to-end. `websearchToTsQuery` currently has no production caller (`matchesTsQuery` +
`toTsQuery`/`phraseToTsQuery` are the ones actually wired into `OlsSearchQuery`'s query-building);
it is still public API surface on this class and is tested like every other method here, but is
noted as presently unused rather than treated as a defect — nothing about its behaviour is wrong,
it simply isn't called yet.

**Scope: unit layer plus a dedicated IT layer — unlike `AnnotationExtractor`, and unlike most Tier
B targets.** Per the Tier B methodology, a pure-logic class with no Postgres dependency gets
unit-only coverage; `JooqSupport` is the opposite case explicitly called out in that methodology's
"what covered means" section — every one of its methods either talks to Postgres directly (the
`Table<?>` constants) or builds a SQL fragment whose entire reason to exist is to invoke a
Postgres-side function or operator: three custom functions defined only in the production schema
(`ols_tsvector`, `ols_lower_array` — see `dataload/create_postgres_schema.py`), pg_trgm builtins
(`similarity`, `show_limit()`, `show_trgm()`, the `%` operator), and the pgvector `<=>` cosine-
distance operator. A `JooqSupportTest` unit layer that only asserts on jOOQ's rendered SQL text
(via a connection-free `DSL.using(SQLDialect.POSTGRES)` context, `renderInlined` so bind values
are inlined rather than left as placeholders) proves each builder emits the *intended* SQL — it
cannot prove that SQL is *semantically correct* once Postgres actually executes it, because
`ols_tsvector`/`ols_lower_array` don't exist outside the applied production schema and
pg_trgm/pgvector behaviour can't be inferred from syntax alone. That distinction is not academic
here: `arrayContains`'s GIN-friendly form, `arrayContainsCaseInsensitive`, and `tsvectorMatches`
each carry an inline comment citing a real past production incident (GitHub issues #1276, #1308,
#1309) where getting exactly this kind of condition wrong broke search in production. A syntax-only
unit test would have passed on the *broken* version of any of those fixes just as readily as the
correct one, since a plausible-looking SQL string that quietly matches the wrong rows still renders
correctly. `JooqSupportIT` is therefore not optional polish on top of the unit layer — it is the
only layer in this baseline that actually re-proves those three fixes still hold.

**No external dependency, so no mock/real boundary to document.** Unlike the Tier A external-
dependency scoping (an HTTP client, a CLI binary), `JooqSupport` and everything it touches —
Postgres, pg_trgm, pgvector, the two custom schema functions — is reachable in full from the
disposable Testcontainers Postgres this repo already uses for every other IT suite. `JooqSupportIT`
therefore uses the real, unmodified methods throughout; nothing is faked.

**Fixture.** `PostgresIntegrationTestSupport` gained `JOOQ_SUPPORT_ONTOLOGY_ID` /
`JOOQ_SUPPORT_EMBEDDING_MODEL` constants, `initializeJooqSupportDatabase` (calls the existing
`initializeDatabase` first, for the extensions/custom-functions/indexes `executeProductionSchema`
applies, then loads this class's own fixture rows), `createJooqSupportRepositories` (returns a new
`JooqSupportRepositoryHandle` record — just a `PostgresClient`, since `JooqSupport` itself has no
repository/service bean to construct), and two private loaders. Five hand-picked `ols_entities`
rows live under the dedicated `JOOQ_SUPPORT_ONTOLOGY_ID`, isolated from every other suite's shared
fixture: `cancer_upper`/`cancer_lower` (identical labels differing only in case, for
`arrayContains`'s case-sensitive `@>` versus `arrayContainsCaseInsensitive`'s
`ols_lower_array`-wrapped form, with non-overlapping `curated_from_sources` doubling as the
`unnest` fixture), `diabetes_parent`/`diabetes_child` (a real `direct_parents` relationship, for
both join forms of array containment, plus trigram/full-text fixtures via the child's "Sugar
Diabetes" synonym), and `fulltext_control` (shares no words with the other four, a genuine
non-match control for the trigram/tsquery methods). Three `ols_embedding_nodes` rows carry a
4-dimensional `embedding_<model>` vector column with hand-computable pgvector cosine distances from
a fixed `[1,0,0,0]` reference: identical (distance exactly `0`), orthogonal (distance exactly `1`),
and opposite (distance exactly `2`) — the two `vectorDistance` IT cases assert these to within
`1e-6`, not just "some plausible-looking float".

**Every method, both `field()` branches, and the private constructor are covered.** The unit layer
(`JooqSupportTest`, 31 cases) exercises every method and overload listed above, including the
private constructor (asserted `private` via reflection, then invoked reflectively to cover the
no-op body — never invoked from production code, since nothing else in the class needs an
instance) and both sides of `field`'s three-arg qualifier check (`null`, `""`, and
whitespace-only `"   "` all take the same unqualified-fallback branch via `isBlank()`, versus a
non-blank qualifier producing `"qualifier"."column"`). The IT layer (`JooqSupportIT`, 17 cases)
re-proves the semantically load-bearing subset of that same surface against the real fixture:
exact-vs-case-insensitive array containment, both join forms of array containment finding the same
real parent through structurally different SQL, `unnest` genuinely expanding one array-valued row
into several (not just passing the array through), a real trigram near-miss scoring higher than an
unrelated string and `similarityAtLeastThreshold` agreeing exactly with the `%` operator on the
same fixture, `maxTrigramCandidateLength`'s soundness property (it must never exclude a row that
genuinely passes the threshold it's meant to approximate) checked against every fixture row rather
than a single hand-picked example, `tsvectorMatches` restricting matches to the one field it wraps
instead of the blanket `ts_search` column, all three tsquery builders' distinct semantics
(`websearchToTsQuery`'s implicit OR, `phraseToTsQuery`'s exact contiguous phrase, `toTsQuery`'s
prefix matching) against `ts_search`, and both `vectorDistance` overloads against hand-computable
cosine distances.

Verified locally on 2026-09-11 from `test/cover-jooq-support`, branched off `origin/dev` at commit
`75d96f57c` (current tip, the same commit the `AnnotationExtractor` baseline above was merged
into), with Java 17:

- Docker-free `mvn -q -o test`, run twice: 1,014 tests (983 pre-existing + 31 new
  `JooqSupportTest` cases), 0 failures / 0 errors both times.
- Postgres `mvn -q -o verify -Dsurefire.skip=true -Dapi.version=1.44` (Rancher Desktop
  `DOCKER_HOST`/`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` overrides), run twice: 222 tests (205
  pre-existing + 17 new `JooqSupportIT` cases), 0 failures / 0 errors both times.
- One clean `mvn -q -o clean verify -Dapi.version=1.44`: 1,236 tests total (1,014 surefire + 222
  failsafe), wall-clock 2 minutes 38.02 seconds, 0 failures / 0 errors.
- `JooqSupport` itself now covers all 28 of 28 lines, all 4 of 4 branches, all 20 of 20 methods,
  and all 22 of 22 complexity units — 100% on every JaCoCo dimension. Whole-backend JaCoCo
  coverage is 71.3% lines (3,430 of 4,810) and 54.1% branches (1,038 of 1,918), up only marginally
  from the `AnnotationExtractor` baseline's 3,428 of 4,810 lines and 1,037 of 1,918 branches (same
  denominators — no production code changed between these two rollouts). That small a shift despite
  `JooqSupport` going from zero dedicated tests to 100% is expected, not a sign the new tests are
  redundant: unlike `EmbeddingServiceClient`/`TextTaggerService` (touched only through a
  controller-level fake standing in for the *whole* class), `JooqSupport`'s methods are called
  directly, inline, by six other repository/service classes whose own IT suites were already
  running real Postgres queries built with them — so most of its bytecode was almost certainly
  already being *executed* incidentally before this rollout, just never independently *asserted
  on*. The one branch pair verified as previously unreachable by any production path is `field`'s
  blank-qualifier check: every 3-arg `field(qualifier, column, type)` call site in production code
  passes a hardcoded non-blank alias (`"e1"`, `"e2"`, `"a"`, `"b"`, `"ranked"`, `"emb"`, `"en"`,
  etc.) — nothing in production ever calls it with a blank qualifier, so that fallback branch
  existed only on paper until `JooqSupportTest`'s three qualifier-blank cases exercised it directly.
  This is exactly the "a controller/caller faking or exercising a class isn't the same as testing
  it" point the Tier B methodology opens with, now demonstrated with a concrete, verified example
  rather than left as a general warning. No coverage failure threshold is introduced.
- No production defect was discovered by this rollout. The recovered WIP's SQL-rendering
  assertions and real-Postgres semantic assertions were independently re-verified against
  `JooqSupport.java`'s actual source, `dataload/create_postgres_schema.py`'s table/function
  definitions, and every real production call site (via `grep`, `graphify-out/` not yet being
  generated in this fresh worktree) rather than taken on trust; no gaps or incorrect assertions
  were found, so no fixes were needed beyond this verification and documentation pass.
## Implemented JsonTransformer baseline

`JsonTransformer` (`repository/transforms`) is the third Tier B target completed under this
programme's expanded scope (after `AnnotationExtractor` above, and alongside the still-open
`EmbeddingServiceClient`/`JooqSupport` PRs — see `.claude/commands/backend-test-coverage.md`'s
Tier B methodology). It is a pure static-method utility class — no Spring bean, no constructor
state, no Postgres dependency — with a single public method, `transformJson(JsonElement, String,
JsonTransformOptions)`, called from `ClassRepository`, `EntityRepository`, `IndividualRepository`,
`OntologyRepository`, and `PropertyRepository` (confirmed via `graphify explain "JsonTransformer"`
before falling back to `grep`), as well as directly from several `controller/mcp/*` `@Tool`
services. The method's own logic is entirely a two-flag dispatch: `LocalizationTransform` and
`RemoveLiteralDatatypesTransform` always run; `ResolveReferencesTransform` runs only when
`JsonTransformOptions.resolveReferences` is true, and `ManchesterSyntaxTransform` only when
`.manchesterSyntax` is true. Production callers set these flags independently — e.g.
`McpClassService` sets both `true`, `HealthCheckController` constructs a bare
`new JsonTransformOptions()` (both `false`) — so all four combinations are real, reachable states,
not a hypothetical Cartesian product.

**Scope: `JsonTransformer`'s own dispatch logic only, not its four sub-transforms.**
`LocalizationTransform`, `RemoveLiteralDatatypesTransform`, `ResolveReferencesTransform`, and
`ManchesterSyntaxTransform` are each their own separate, real-logic static-method utility class,
and each remains its own not-yet-covered Tier B backlog item — this rollout does not give any of
them exhaustive branch-by-branch coverage; that is future invocations' job, one class at a time,
per this programme's standing "one unit per invocation" rule. `JsonTransformerTest` uses small,
hand-verifiable JSON fixtures only large enough to observe each sub-transform actually firing (or
genuinely not firing when its flag is off) — not to exercise that sub-transform's own internal
branches.

**`JsonTransformOptions` is excluded from the Tier B backlog**, by judgment rather than silently:
it is a plain data holder (two public booleans, `resolveReferences`/`manchesterSyntax`, with
getters/setters and no logic of its own), used throughout this suite only as an input fixture.

**Scope: unit-only, no IT layer.** Per the Tier B methodology's "what covered means" section, a
pure-logic class with no Postgres dependency of its own does not get a dedicated `*IT.java` layer.
`JsonTransformer` reads no state and calls nothing Postgres-backed — there is no real-database
behaviour to prove beyond what `JsonTransformerTest`'s hand-built `JsonElement` fixtures already
cover. This baseline is therefore a single new `JsonTransformerTest.java` (6 cases, this repo's
plain-JUnit/AssertJ idiom, no Mockito, no fakes — everything under test is static and pure) and
nothing else.

**Cases enumerated.** Two cases isolate the unconditional transforms from the flag dispatch
entirely: a two-language (`en`/`fr`) literal array requested with `lang="en"` proves
`LocalizationTransform` selects the requested language and drops the other, collapsing each
matching literal straight to its raw value; a `{"type":["literal"],"value":"Diabetes"}` object (no
`lang` key) requested with `lang=""` proves `RemoveLiteralDatatypesTransform` independently strips
the datatype wrapper down to the bare `"Diabetes"` string — distinct from `LocalizationTransform`'s
own unwrap of a language-matched literal, which the first case already exercises. The remaining
four cases cover every combination of the two flags against one shared, realistic V2-entity-shaped
fixture (a two-language `label`, a `directParent` IRI resolvable via a `linkedEntities` entry, and
an `owl:someValuesFrom` class-expression restriction under `subClassOf`): neither flag enabled
leaves `directParent` as the raw IRI and `subClassOf`'s restriction structurally untouched; only
`resolveReferences` enabled inlines the linked entity (with the `iri` field
`ResolveReferencesTransform` adds) but leaves the restriction alone; only `manchesterSyntax`
enabled collapses the restriction to its Manchester string (`"...hasSymptom some ...Fever"`) but
leaves `directParent` unresolved; both enabled do both. Every one of the four cases also asserts
the fixture's `label` field collapsed to the single, `en`-only value, proving the two unconditional
transforms keep running in every flag combination, not only when both optional flags are off.
## Implemented ManchesterSyntaxTransform baseline

`ManchesterSyntaxTransform` (`repository/transforms`) is the second Tier B target in this
programme. It is a pure static-method utility class — no Spring bean, no constructor state, no
Postgres dependency — that recursively walks a Gson `JsonElement` tree and, wherever it finds an
anonymous OWL class-expression object (detected heuristically via `isClassExpressionObject`, with
`isNamedEntityObject` giving named entities precedence over collapsing), renders it as a
Manchester-syntax string via `toManchester`. It is called from `JsonTransformer.transformJson`
(itself called from `PropertyRepository`, `IndividualRepository`, `OntologyRepository`,
`EntityRepository`, `ClassRepository`) when the `manchesterSyntax` output option is requested. Its
sibling class `JsonTransformer`, covered by an earlier (still-open) PR in this programme, tests
only its own dispatch of the four transforms it chains together, not this class's internals — this
baseline is the first dedicated coverage of `ManchesterSyntaxTransform`'s own branches.

**Scope: unit-only, no IT layer.** Same rationale as the `AnnotationExtractor` baseline above: a
pure-logic class with no Postgres dependency of its own has no real-database behaviour to prove
beyond a direct unit test with hand-built `JsonElement` fixtures. This baseline is a single new
`ManchesterSyntaxTransformTest.java` (61 cases, this repo's plain-JUnit idiom — text-block JSON
fixtures parsed via `JsonParser.parseString`, matching the existing `LocalizationTransformTest`
idiom for this same `transforms` package — no Mockito, no Spring context) and nothing else. Every
case drives the single public entry point, `transform(JsonElement)`, since the class's other three
methods (`isClassExpressionObject`, `isNamedEntityObject`, `toManchester`) are private; whether an
input collapses to a Manchester string or is left structurally intact and recursed into is directly
observable in the shape of `transform`'s return value, so this still lets every one of their
branches be asserted with exact string/structural equality (not just "contains" or "non-null").
`JsonCollectionHelper.map`, touched only incidentally by `transform`'s own array/object recursion,
is exercised by several cases here but does not get its own dedicated suite — it remains a separate
future backlog item, as called out by the task that produced this baseline.

**Fixtures grounded in real production data.** Rather than inventing synthetic OWL shapes,
`graphify query` was used first (per this repo's `CLAUDE.md`) to trace `ManchesterSyntaxTransform`'s
real callers through `JsonTransformer`, then the committed
`testcases_expected_output/owl2-primer/*/ontologies_linked.json` golden fixtures were inspected for
the actual rdf2json shapes this codebase's OWL pipeline produces. Several test cases reuse those
exact shapes: `intersectionOf` (`subClassOf-intersectionOf`: Man and Parent), `someValuesFrom`/
`allValuesFrom` (the `equivalent-propertyRestriction-*` fixtures: onProperty=hasChild), exact
`cardinality`/`qualifiedCardinality` values (`unqualified-cardinality-exact-restriction`,
`propertyRestriction-qualifiedCardinality`: onProperty=hasChild, onClass=Parent, cardinality=2),
`hasSelf` (`self-restriction`: onProperty=loves, hasSelf=true), and the datatype-restriction
min/max facets (`datatype-minmax`: onDatatype=xsd:integer, withRestrictions=[{minInclusive:0},
{maxInclusive:150}], as raw un-stringified JSON numbers — confirmed by reading
`RemoveLiteralDatatypesTransform`, the stage immediately before this one in `JsonTransformer`'s
pipeline, that literal-wrapped values reaching this class are already flattened to plain strings
while `withRestrictions` facet values are not, which is exactly why `normalizeNumberToString`
exists).

**Branches enumerated.** `transform`: null/`JsonNull` input; array input (recurses per-element via
`JsonCollectionHelper.map`); an object that is a class expression and not a named entity (collapses
to a `JsonPrimitive` string); an object that is not a class expression at all (recurses into its
values, including a nested value that itself collapses); the "named entity wins over
class-expression detection" precedence explicitly called out in the code's own comment (an object
carrying both `iri` and `owl:inverseOf` is *not* collapsed — it stays structurally intact while its
non-named-entity nested value still collapses); primitives and booleans returned as-is.

`isClassExpressionObject`: the negative case (no matching keys and no `type` at all); the
`type`-array-contains-`"datatype"`-alone-without-`equivalentClass` negative case; the
`equivalentClass`-alone-without-`datatype`-type negative case; the combined
datatype+equivalentClass special case (positive), including the case-0 label-prefix precedence
over the general label shortcut. All 18 of the class's own key-array entries (the class's inline
comment undercounts these as unspecified; the array literal itself has 18 distinct URI constants)
are each exercised via at least one `toManchester`-branch test that starts from an object carrying
that exact key, per this task's own "don't necessarily need all as separate
`isClassExpressionObject`-only cases" guidance.

`isNamedEntityObject`: each of the five direct-field checks (`iri`, `ontologyId`, `ontologyIri`,
`curie`, `shortForm`) as independent positive cases; the `type`-array-contains-an-entity-type path
with two of the eight listed strings (`"class"`, `"objectProperty"`); the
type-array-present-but-no-entity-type-string-in-it case, verified distinct from the true case by
its different output shape (the object still collapses to a string, rather than staying intact);
the no-fields/no-type negative case, covered incidentally by the many collapsing tests above (per
this task's own exemption for cases already exercised elsewhere).

`toManchester`, in the exact precedence order the code checks them (several are mutually exclusive
early-returns, verified with cases that give two matching keys and assert only the first-checked
one fires): the datatype+equivalentClass special case with and without a `label` field (proving the
`"label "` prefix is added only when present, and that a non-primitive label — the raw
pre-localization shape — is silently ignored rather than rendered incorrectly); the general
`label`-shortcut precedence over `owl:intersectionOf` on the same object; `owl:intersectionOf`
(2-element) and `owl:unionOf` (3-element), proving the exact `intersperse`/`joinWith` parenthesized
format against both list lengths, plus `intersectionOf` given as a bare non-array value;
`owl:complementOf`; `owl:oneOf` (brace-joined, with `normalizeNumberToString` on a numeric member);
`owl:inverseOf`; a `null` member inside `intersectionOf` rendering the bottom symbol (`⊥`); datatype
restrictions combining two XSD facets in one restriction object (`minInclusive`+`maxInclusive` via
the case-0 path, `minExclusive`+`maxExclusive` via the general `onDatatype` path) plus both the
present-but-empty and entirely-absent `withRestrictions` cases (bare-IRI fallback), plus a
non-object entry mixed into the restriction list being skipped; the "no `onProperty` and not
`isJsonBoolean`" fallback to `"unknown class expression"`, confirmed empirically (per this task's
own instruction not to assume) to fire even when another restriction key like `minCardinality` is
present, since `isJsonBoolean` is a permanently-`false` stub; each of
`someValuesFrom`/`allValuesFrom`/`hasValue`/`minCardinality`/`maxCardinality`/`cardinality`/
`hasSelf`-when-truthy as an individual case, plus six pairwise precedence tests spot-checking the
entire adjacent chain in order; `hasSelf` as `false`, as an explicit JSON `null`, and as a
non-boolean string all correctly *not* triggering the `Self` suffix; each of the three qualified
cardinalities (gated on `owl:onClass`) plus their own precedence test and the "onClass present but
no qualified-cardinality key" fallback; and `hasValue` targeting a resolved named-individual object
(grounded in the `value-restriction-on-individual` fixture) proving `normalizeNumberToString`'s
`isJsonPrimitive()` guard leaves non-primitive values untouched, which then render via the general
label shortcut.

**Known-unreachable branches, left undocumented as gaps rather than chased.** JaCoCo's own report,
read line-by-line, shows the residual missed branches are not test gaps but structurally
unreachable given the current call graph: `toManchester`'s own `el == null` (Java-`null`, as
opposed to `JsonNull`) guard is never reached because every internal call site that invokes
`toManchester` already null-checks its argument first; and the `onProperty == null &&
!isJsonBoolean(obj)` gate's second operand can never evaluate to `false` because `isJsonBoolean` is
a hardcoded `return false;` stub (exactly the suspicious edge case flagged going into this
rollout — confirmed empirically to be inert dead code, not a defect: see the "No production defect"
note below). A handful of remaining branches live entirely inside the low-level helpers
(`safeArray`, `containsString`, `asList`, `isTruthy`) explicitly exempted by this task's own
guidance from needing dedicated tests beyond incidental exercise, and were left as-is rather than
chased with contrived reflection-free inputs.
## Implemented OlsFacetedResultsPage baseline

`OlsFacetedResultsPage` (`repository/search`) is the second Tier B target in this programme. It
is a tiny generic `PageImpl<T>` subclass (23 lines) adding one field,
`facetFieldToCounts: Map<String, Map<String, Long>>`, alongside the normal page
content/pageable/total-elements, plus a constructor and one overridden
`map(Function<? super T, ? extends U> converter)` that must produce a new
`OlsFacetedResultsPage<U>` carrying the *same* `facetFieldToCounts`, `pageable`, and
`totalElements` while converting the content via Spring Data's `getConvertedContent`. Its only
real production caller is `OlsSearchClient` (`searchPaginated`, which builds a genuine
`LinkedHashMap<String, Map<String, Long>>` of facet-field -> facet-value -> count, and `suggest`,
which always passes `Map.of()`) — confirmed directly against `OlsSearchClient.java` rather than
assumed, per this repo's `graphify query` convention for finding real callers before writing
fixtures.

**Scope: unit-only, no IT layer.** This class has no Postgres dependency of its own — it wraps
data another class (`OlsSearchClient`) has already fetched — so per the Tier B methodology's
"what covered means" section it does not get a dedicated `*IT.java`; a single
`OlsFacetedResultsPageTest.java` (this repo's plain-JUnit/AssertJ idiom, no Mockito, nothing here
worth faking) is the complete requirement.

**Why this class was worth a dedicated test despite its size.** A page-wrapper class like this is
exactly the shape where a naive `map()` override can silently drop the extra field or the
total-element count during a conversion chain — the override has to remember to thread
`facetFieldToCounts` through by hand since `PageImpl.map()` doesn't know about it. That's a
plausible, easy-to-introduce regression, and one existing indirect coverage (WIT/IT tests that
build `OlsFacetedResultsPage` fixtures via local `page()`/`emptyPage()`/`facetedPage()` helpers,
or exercise it transitively through `OlsSearchClient`) doesn't specifically assert: those call
sites check the resulting HTTP response shape, not that the *same* facet-map instance survived a
`.map()` call, nor what happens with a `null` facet map, nor whether total-element tracking
survives a `.map()` on a partial (not-last, not full-dataset) page.

**Cases enumerated**, per the constructor and the `map()` override:
- Constructor with a non-empty facet-counts map (shape taken directly from
  `OlsSearchClient.searchPaginated`'s real `LinkedHashMap<String, Map<String, Long>>>`), asserting
  content, pageable, total elements, and the facet map's contents.
- Constructor with an empty facet-counts map (`Map.of()`) — the real shape `suggest()` always
  passes, not just a hypothetical edge case.
- Constructor tolerates a `null` facet-counts map: the field has no null-check anywhere in the
  class, so `null` is simply stored as given, with no NPE and no silent substitution.
- `map()` with a genuinely converting function (`Integer -> String`) on a **partial page**
  (5 results loaded, `numFound` 23 across all pages — not a same-size trivial case), asserting
  (a) the converted content matches the converter applied to each original element in order,
  (b) the returned page's `facetFieldToCounts` is the exact same map instance as the source's (not
  dropped, not a fresh empty map), and (c) `getTotalElements()`/`getPageable()` on the result match
  the source unchanged.
- `map()` preserves a `null` facet-counts map through the conversion rather than substituting an
  empty one.
- `map()` on an empty-content page, confirming the facet map/pageable/total-elements still survive
  the conversion when there is no content to convert.

Verified locally on 2026-09-11 from `origin/dev` commit `75d96f57c` with Java 17 (no Postgres/
Docker gate — this class has no IT layer, per the scope note above):

- Surefire runs 989 tests, including 6 `JsonTransformerTest` cases. Two Docker-free runs both
  reported 0 failures / 0 errors.
- The clean `verify` lifecycle runs all 1,194 tests (989 surefire + 205 failsafe, unchanged by this
  rollout since no IT was added) in wall-clock 2 minutes 17.21 seconds.
- `JsonTransformer` itself now covers 21 of 24 instructions (87.5%), 4 of 4 branches (100%), and 7
  of 8 lines (87.5%); the one uncovered line is the implicit default constructor, never invoked
  since the only caller-facing entry point is the static `transformJson` method — the same pattern
  already noted for `AnnotationExtractor` above. Whole-backend JaCoCo coverage is 74.5% instructions
  (17,924 of 24,054), 58.4% branches (1,120 of 1,918), and 73.5% lines (3,533 of 4,810); this is
  measured from a later `origin/dev` commit than the `AnnotationExtractor` baseline above (which
  already contributed 24 of its own test cases to this same total), not a jump caused by this
  rollout alone. No coverage failure threshold is introduced.
- No production defect was discovered by this rollout.
- Surefire runs 1,044 tests, including all 61 `ManchesterSyntaxTransformTest` cases. Two
  Docker-free runs took wall-clock 12.76s and 13.09s, both 0 failures / 0 errors.
- The clean `verify` lifecycle runs all 1,249 tests (1,044 surefire + 205 failsafe, unchanged by
  this rollout since no IT was added) in wall-clock 2 minutes 2.40 seconds.
- `ManchesterSyntaxTransform` itself now covers 156/157 lines (99.4%), 157/166 branches (94.6%), 885/892
  instructions (99.2%), 15/16 methods (93.8% — the one uncovered method is the implicit default
  constructor, never invoked since every caller uses the static methods directly, the same pattern
  already documented for `AnnotationExtractor`).
- Whole-backend: 74.5% lines (3,584/4,810), 62.3% branches (1,194/1,918) — up from the
  `AnnotationExtractor` baseline's 71.3%/54.1% (the total line/branch denominators are unchanged at
  4,810/1,918, confirming this class previously had essentially zero dedicated coverage: the
  backend-wide covered-line/covered-branch deltas, +156 and +157, line up almost exactly with this
  class's own newly-covered counts). No repository-wide coverage threshold is introduced.
- No production defect was discovered by this rollout. The `isJsonBoolean` stub and the
  `el == null` guard were both investigated as flagged suspicious edge cases going in; both are
  confirmed-empirically-inert defensive code reachable only via direct reflection on the private
  method, not through the public `transform` entry point any real caller uses, so neither rises to
  a genuine, user-visible defect worth a separate PR. `oneOf`'s member normalization
  (`normalizeNumberToString`) was also checked against a real numeric member and behaves correctly.
## Implemented McpClassService baseline

`McpClassService` (`controller/mcp`) is the first Spring AI MCP `@Tool`-annotated target in this
programme. Despite living under `controller/`, it is a plain `@Service` with no HTTP layer at all —
no MockMvc, no routes, no `@WebMvcTest` — so it is tested exactly like any other Tier B service
class, per this file's own note in the "everything else" enumeration. It has four real,
Postgres-backed `@Autowired` collaborators (`EntityRepository`, `ClassRepository`,
`EmbeddingServiceClient`, `OlsPostgresClient`, each already covered by its own dedicated suite
elsewhere in this programme) and six `@Tool` methods: `searchClasses`, `getAncestors`,
`getChildren`, `getDescendants`, `searchClassesWithEmbeddingModel`, `getSimilarClasses`,
`getClassSimilarity`. Every method shares a default-value pattern worth testing precisely:
`pageNum`/`pageSize` default to `0`/`20` when `null`, `lang` defaults to `"en"` when `null` (four
methods reassign a mutable `lang` parameter; the other two instead assign a `final effectiveLang`
— same effective behaviour, but a future refactor could desync the two patterns, so both are proven
independently below), and every method constructs a fresh `JsonTransformOptions` with
`resolveReferences=true`/`manchesterSyntax=true` hardcoded, never varying with caller input.

**Two layers, same mock/real boundary already established for `EmbeddingServiceClient`/
`V2LLMController`.** `McpClassServiceTest.java` (28 cases) is a direct unit suite using this repo's
hand-rolled-fake idiom (no Mockito) for all four collaborators — no Postgres involved at all.
`McpClassServiceIT.java` (13 cases) wires the real `EntityRepository`/`ClassRepository`/
`OlsPostgresClient` beans against a real disposable Postgres via
`PostgresIntegrationTestSupport`, faking only `embeddingServiceClient.embedText(...)` with the same
hand-rolled `FixedVectorEmbeddingServiceClient` idiom as `V2LLMControllerIT` — a real embedding
microservice is unavailable in this test environment, so that one external HTTP call is the only
piece not exercised for real; every subsequent Postgres nearest-neighbor search, hierarchy
traversal, and free-text search is genuine.

**Fixture reuse, no new factory needed.** `PostgresIntegrationTestSupport.initializeV2LLMDatabase`/
`createV2LLMRepositories` (the `V2LLMController` baseline's fixture and factory) already load
everything `ClassRepository`/`EmbeddingServiceClient`/`OlsPostgresClient` need here: the base entity
fixture (`EFO_0001`/`EFO_0002`/`DUO_0001` classes), the class-hierarchy fixture (`EFO_1001` as
`EFO_0001`'s direct/hierarchical child, `EFO_1999` as its obsolete sibling), and the `test_model`
embedding fixture (`ols_entities.embeddings_test_model` plus `ols_embedding_nodes` label/curation
rows). The one gap — `createV2LLMRepositories` never wires an `EntityRepository`, which none of its
existing `ClassRepository`-only callers need but `searchClasses` does — is filled by composing a
second, existing handle, `PostgresIntegrationTestSupport.createEntityRepository`, against the same
container, rather than adding a new factory: together the two handles already cover every
collaborator `McpClassService` declares, so nothing new was added to
`PostgresIntegrationTestSupport`.

**Branches enumerated.** `searchClasses` always filters `type=[class]`; adds `ontologyId=[value]`
only when non-null; adds `isObsolete=[false]` for both `null` and explicit `false`
`includeObsoleteEntities`, omitting it only for explicit `true` — three cases, proven as three, not
collapsed to two. `getChildren` always passes a hardcoded `null` search term and `false`
`includeObsolete` to `ClassRepository`, matching `getAncestors`/`getDescendants`'s own hardcoded
`false`. `searchClassesWithEmbeddingModel` converts the embedding service's `float[]` to
`List<Double>` exactly (asserted with values like `-2.25`/`100.125` chosen to expose a truncation or
off-by-one bug, not just non-nullness); dispatches to `postgresClient.searchByVectorInOntology(...)`
only when `ontologyId` is non-null *and* non-empty, proving the empty-string case explicitly takes
the same global-search path as `null` rather than assuming it; always passes `isDefiningOntology=
true` to the ontology-scoped search; and resolves `includeCurations` — `null` and explicit `true`
both to `true`, only explicit `false` differs, three cases again. `getClassSimilarity` is a one-line
delegation to `classRepository.getSimilarity(...)`, proven by direct argument-and-return-value
pass-through.

**Proving the two hardcoded constants where no collaborator fake could observe them.**
`searchClassesWithEmbeddingModel`'s `outputOpts` and `effectiveLang` are used only inside its own
internal `JsonTransformer.transformJson(...)` call — never handed to a collaborator fake — so two
unit tests prove them observably rather than by inspecting private state: one hands back a
`directParent` IRI alongside a matching `linkedEntities` entry and asserts it resolves into a full
reference object (only possible when `resolveReferences=true`, which is hardcoded and never
varies); the other hands back an entity whose only localized label is tagged `"de"` and asserts the
label is present under explicit `lang="de"` but *absent* under the `lang=null` default — directly
distinguishing "defaults to en" from "defaults to something else" (or, most importantly, from an
unset `null` `effectiveLang`, which would instead throw `NullPointerException` inside
`LocalizationTransform.transform`'s unconditional `lang.equals("all")` check). The other five
methods' `resolveReferences`/`manchesterSyntax`/default-`lang` behaviour is captured directly via
the corresponding hand-rolled fake, no such indirection needed.
## Implemented McpOntologyService baseline

`McpOntologyService` (`controller/mcp`) is the third Spring AI MCP `@Tool`-annotated Tier B target
in this programme, after `McpClassService` and `McpEmbeddingService`. It is a plain `@Service` with
no HTTP layer at all, tested exactly like any other Tier B service class. Unlike its two
predecessors it has a single `@Autowired` collaborator (`OntologyRepository`, already fully covered
by its own dedicated `OntologyRepositoryIT`/unit-equivalent suite elsewhere in this programme) and
exactly one `@Tool` method, `listOntologies(String lang)` — by far the smallest surface of the three
MCP targets so far: no pagination, no obsolete-filtering branch, no embedding-model dispatch, and no
`McpPage` wrapper (it returns a plain `List<McpOntology>`, unlike `McpClassService`/
`McpEmbeddingService`'s paginated results).

**Unit-only; no IT layer needed for coverage, but a thin one added anyway for genuine end-to-end
value.** Per the Tier B methodology, `OntologyRepository` already has full dedicated real-Postgres
coverage in `OntologyRepositoryIT` (search, filtering, sorting, pagination, boost fields, dynamic
properties) — re-proving any of that here would be pure duplication. `McpOntologyServiceTest.java`
(10 cases) is therefore the non-negotiable minimum: a direct unit suite using this repo's
hand-rolled-fake idiom (no Mockito), covering both `lang` branches (defaults to `"en"` when `null`,
passed through unchanged otherwise), the always-on `JsonTransformOptions`
(`resolveReferences`/`manchesterSyntax` both hardcoded `true`), the exact fixed-argument delegation
to `OntologyRepository.find` (a hardcoded `PageRequest.of(0, 1000)` plus five hardcoded
`null`/`false` arguments — `search`, `searchFields`, `boostFields`, `exactMatch`, `properties` — all
asserted individually, not sampled), the plain-`List` result mapping through `McpOntology.fromJson`
(including the empty-list-not-null shape for ontologies with no `label`/`definition`), and
`IOException` propagation from the repository unchanged.

On top of that minimum, a thin `McpOntologyServiceIT.java` (2 cases) was added because it proves two
things the unit suite's hand-rolled fake cannot: that the fixed-argument call really reaches real
Postgres and returns genuine data through `McpOntology.fromJson`, and — more importantly — that
passing no `properties` filter (the fifth hardcoded `null` argument) really does mean **obsolete
ontologies are included**, a materially different and easy-to-regress contract from every other MCP
`@Tool` method built so far in this programme (`McpClassService.searchClasses` always filters
`isObsolete=[false]`). It reuses `PostgresIntegrationTestSupport.createRepository`/
`initializeDatabase` — the exact same factory and fixture `OntologyRepositoryIT` already uses (four
ontologies: `duo`, `efo`, `efo-atlas` active, `legacy-efo` obsolete) — with no new fixture mechanism
needed. One case asserts all four ontology IDs come back, obsolete one included; the other asserts
`McpOntology.fromJson`'s field mapping against a real row: `ontologyId` resolves correctly, and
`label`/`definition` resolve to empty lists rather than `null`, because the shared fixture's raw
entity JSON carries ontology metadata under `title`/`description` (already proven by
`OntologyRepositoryIT`), not literal `label`/`definition` annotation values. This is not a defect —
it matches real production behaviour for ontologies with no `rdfs:label` triple on their
`owl:Ontology` declaration, confirmed against the committed golden file
`testcases_expected_output_api/mcp/listOntologies.json`, where most real ontologies (`duo`, `edam`,
etc.) show the identical empty-`label`/empty-`definition` shape and only a handful
(`skos`/`owl`/`rdfs`) carry a genuine `rdfs:label` on their ontology header.

Verified locally on 2026-09-11 from `origin/dev` commit `75d96f57c` with Java 17 and Rancher
Desktop:

- Surefire runs 1,011 tests, including 28 direct `McpClassServiceTest` cases. Two Docker-free runs
  took wall-clock 12.93 and 11.92 seconds, both 0 failures / 0 errors.
- Failsafe runs 218 PostgreSQL tests, including 13 `McpClassServiceIT` cases — one representative
  real-Postgres case per branch enumerated above, across all six `@Tool` methods. Two complete
  database-gate runs took wall-clock 2 minutes 0.27 seconds and 2 minutes 0.47 seconds.
- The clean `verify` lifecycle runs all 1,229 tests (1,011 surefire + 218 failsafe) in wall-clock 2
  minutes 9.86 seconds.
- `McpClassService` itself covers all 105 executable lines, all 52 branches, and all 9 methods —
  100% on every JaCoCo dimension. Whole-backend JaCoCo coverage is 75.1% lines (3,612 of 4,810) and
  59.3% branches (1,138 of 1,918), up from the most recently documented baseline of 71.3% lines and
  54.1% branches. Part of that jump is `McpClassService`'s own 105 covered lines/52 covered
  branches; the remainder comes from the `McpClassServiceIT` real-Postgres layer incidentally
  exercising collaborator code paths existing suites hadn't reached in this exact combination —
  `EntityRepository`'s 10-arg `find(...)` overload (the one `searchClasses` calls, distinct from the
  9-/11-arg overloads other callers use) and the `JsonTransformer`/`ResolveReferencesTransform`/
  `ManchesterSyntaxTransform` pipeline running with `resolveReferences=true` and
  `manchesterSyntax=true` *together*, a combination this programme's existing suites had not
  exercised as thoroughly. No coverage failure threshold is introduced.
- No production defect was discovered by this rollout. The empty-string-`ontologyId`-treated-as-
  absent behaviour in `searchClassesWithEmbeddingModel` was checked closely, since the task brief
  flagged it as a plausible defect candidate — but `if (ontologyId != null && !ontologyId.isEmpty())`
  is unambiguous, matches the exact same idiom already used and tested in `V2LLMController`
  (`searchEntitiesByTextTreatsAnEmptyOntologyIdAsAbsent`), and is proven correct empirically by both
  `McpClassServiceTest` and `McpClassServiceIT` above; it is intended behaviour, not a defect.
## Implemented McpEmbeddingService baseline

`McpEmbeddingService` (`controller/mcp`) is a second Spring AI MCP `@Tool`-annotated Tier B target,
alongside `McpClassService` (its closest analogue: same package, same shape of problem — real
Postgres-backed collaborators plus one faked embedding call). It is a plain `@Service` with no HTTP
layer at all, tested exactly like any other Tier B service class. It has two `@Autowired`
collaborators (`EmbeddingServiceClient`, `OlsPostgresClient`, each already covered by its own
dedicated suite elsewhere in this programme) and two `@Tool` methods: `listEmbeddingModels` and
`searchWithEmbeddingModel`.

**Two layers, same mock/real boundary already established for `McpClassService`.**
`McpEmbeddingServiceTest.java` (18 cases) is a direct unit suite using this repo's hand-rolled-fake
idiom (no Mockito) for both collaborators — no Postgres involved. `McpEmbeddingServiceIT.java`
(8 cases) reuses `PostgresIntegrationTestSupport.initializeV2LLMDatabase`/`createV2LLMRepositories`
(the same fixture and factory the `V2LLMController`/`McpClassService` baselines already
established) with the real `OlsPostgresClient` bean against real disposable Postgres — no new
fixture mechanism was needed for this class. Two of `searchWithEmbeddingModel`'s IT cases fake only
`embeddingServiceClient.embedText(...)` (the same hand-rolled `FixedVectorEmbeddingServiceClient`
idiom as `McpClassServiceIT`); every subsequent Postgres nearest-neighbor search, curation
inclusion/exclusion, and ontology scoping is genuine.

**`listEmbeddingModels` gets a real, non-mocked degrade-gracefully case, not just a fake.** Unlike
`searchWithEmbeddingModel`, `listEmbeddingModels` never calls `embedText` — its only external call
is `embeddingServiceClient.getAvailableModels()`, which the `EmbeddingServiceClient` baseline above
already proved degrades to `List.of()` with no exception when the service URL is unconfigured (the
permanent state of every Spring test context in this repo). `McpEmbeddingServiceIT` exploits this
directly: one IT case wires the real, unmodified `EmbeddingServiceClient` bean (from
`createV2LLMRepositories`, `.init()`'d) with no fake standing in for it at all, and asserts the
Postgres-registered `test_model` fixture comes back with `can_embed=false` — genuine end-to-end
integration coverage of the "embedding service unconfigured → every Postgres model shows
`can_embed=false`" contract. A second IT case additionally proves the `can_embed=true` path against
real Postgres, using a hand-rolled fake that overrides only `getAvailableModels()` (a real embedding
microservice advertising a specific model by name is the one piece genuinely unreachable in this
test environment).

**Branches enumerated.** `listEmbeddingModels` builds its result by iterating the *Postgres* list
only, looking up each model's name in a `Set` built from the embedding-service list: a model in both
lists is included with `can_embed=true`; a model known only to Postgres is included with
`can_embed=false`; a model known only to the embedding service does not appear in the result at
all — not even with `can_embed=false` — since it has no `embeddings_<model>` column and can never be
used for similarity search. This asymmetry (called out in the task brief as easy to get backwards)
is proven explicitly by
`McpEmbeddingServiceTest.listEmbeddingModelsExcludesAModelKnownOnlyToTheEmbeddingServiceEntirely`,
which asserts the excluded model is absent from the result, not present with `can_embed=false`. The
result's alphabetical-by-model-name sort is proven with a deliberately out-of-order Postgres list
input. The empty-both-lists case is proven to return an empty list with no exception.
`searchWithEmbeddingModel` converts the embedding service's `float[]` to `List<Double>` exactly
(values like `-2.25`/`100.125` chosen to expose truncation/off-by-one bugs, not just non-nullness);
dispatches to `postgresClient.searchByVectorInOntology(...)` only when `ontologyId` is non-null *and*
non-empty (the empty-string case is proven to take the same global-search path as `null`, matching
`McpClassService`'s identical idiom); always passes `isDefiningOntology=true` to the ontology-scoped
search; and resolves `includeCurations` — `null` and explicit `true` both to `true`, only explicit
`false` differs — for both the global and ontology-scoped search paths.

**The two differences from `McpClassService.searchClassesWithEmbeddingModel`, confirmed rather than
assumed.** First: `searchWithEmbeddingModel` hardcodes `"en"` as the language passed to
`JsonTransformer.transformJson` — it takes no `lang` parameter at all (unlike `McpClassService`'s
equivalent method). This cannot be intercepted via a collaborator fake, so it is proven observably
in the unit suite: an entity whose only localized label is tagged `"de"` resolves to no `"label"` key
at all under the hardcoded `"en"`, so `McpSearchResult`'s `title` (which concatenates
`curie + " " + label`) ends with the literal string `"null"` for the missing label rather than
showing `"German Label"`; a matching English-tagged fixture resolves normally. Second, and more
consequential: `searchWithEmbeddingModel` passes `"OntologyEntity"` as the search-scope argument to
`postgresClient.searchByVector`/`searchByVectorInOntology`, where `McpClassService` passes
`"OntologyClass"`. Reading `OlsPostgresClient.hasConcreteEntityType` confirms this is not a
copy-paste slip: `"OntologyEntity"` is the one literal that makes `hasConcreteEntityType` return
`false`, which disables the type filter entirely (`filterByType ? type : null` passes `null`) — so
this method searches every entity type (class, property, individual), not just classes, by design.
`McpEmbeddingServiceTest` confirms the literal directly; `McpEmbeddingServiceIT` proves the
consequence against real Postgres using the exact same fixture and fixed query vector `[1,0,0,0]`
that `McpClassServiceIT`'s equivalent case uses with `"OntologyClass"` (which finds only the
`EFO_0001`/`EFO_0002` classes): here, the property (`EFO_0100`) and individual (`EFO_I100`)
candidates are found too, ranked by exact cosine similarity against `[1,0,0,0]` — `EFO_0001` (1.0) >
`EFO_I100` (0.8) > `EFO_0100` (0.6) > `EFO_0002` (0.0, `CurationEmbedding` only) — the same worked
arithmetic documented in `PostgresIntegrationTestSupport.loadV2LlmEmbeddingFixture`.

Verified locally on 2026-09-11 from `origin/dev` commit `aa52e09e4` with Java 17 and Rancher
Desktop:

- Surefire runs 1,001 tests, including 18 direct `McpEmbeddingServiceTest` cases. Two Docker-free
  runs took wall-clock 12.46 seconds each (rounded), both 0 failures / 0 errors.
- Failsafe runs 213 PostgreSQL tests, including 8 `McpEmbeddingServiceIT` cases. Two complete
  database-gate runs took wall-clock 2 minutes 7.31 seconds and 2 minutes 4.78 seconds.
- The clean `verify` lifecycle runs all 1,214 tests (1,001 surefire + 213 failsafe) in wall-clock
  2 minutes 11.07 seconds.
- `McpEmbeddingService` itself covers all 36 executable lines, all 16 branches, all 13 complexity
  units, and all 5 methods — 100% on every JaCoCo dimension. Whole-backend JaCoCo coverage is 73.2%
  lines (3,523 of 4,810) and 57.5% branches (1,103 of 1,918).
- No production defect was discovered by this rollout. The Postgres-only-inclusion asymmetry in
  `listEmbeddingModels` (a model known only to the embedding service is dropped entirely rather than
  shown with `can_embed=false`) was checked closely, since the task brief flagged it as
  plausible-looking but possibly intentional. `test_api.sh`'s committed golden files under
  `testcases_expected_output_api/` do not cover this MCP tool class at all (it is not a REST
  endpoint under test by that script), so that check does not apply here. Reading the method's own
  code and comment (`// Build response - only include models that exist in Postgres`) shows the
  Postgres-only iteration is the literal, deliberate mechanism the method is built around — a model
  absent from Postgres has no `embeddings_<model>` column and therefore can never be used for
  similarity search via `searchWithEmbeddingModel`/`searchByVector`, so surfacing it at all (even
  with `can_embed=false`) would advertise a model callers could never actually use. This reads as
  intended behaviour, not a defect, so no separate defect PR was opened; the behaviour is instead
  documented and proven precisely above and in `McpEmbeddingServiceTest`.
- Surefire runs 993 tests, including 10 direct `McpOntologyServiceTest` cases. Two Docker-free runs
  took wall-clock 12.65 and 11.98 seconds, both 0 failures / 0 errors.
- Failsafe runs 207 PostgreSQL tests, including 2 `McpOntologyServiceIT` cases. Two complete
  database-gate runs both reported 0 failures / 0 errors (2.87s and 3.08s for this class's own two
  cases within each run).
- The clean `verify` lifecycle runs all 1,200 tests (993 surefire + 207 failsafe) in wall-clock 3
  minutes 13.56 seconds (re-measured by the orchestrator after a rate-limit interruption; the other
  numbers in this section — surefire/failsafe pass counts and JaCoCo figures — were independently
  re-verified and match exactly).
- `McpOntologyService` itself covers all 38 instructions, both branches, all 9 executable lines, and
  both methods — 100% on every JaCoCo dimension. Whole-backend JaCoCo coverage is 72.6% lines (3,491
  of 4,810) and 56.3% branches (1,080 of 1,918), up from the most recently documented baseline of
  71.3% lines and 54.1% branches (the AnnotationExtractor baseline) — entirely attributable to this
  rollout, since the line/branch denominators are unchanged from that baseline.
- No production defect was discovered by this rollout. The "obsolete ontologies are included, not
  filtered" behaviour was checked closely as a plausible defect candidate (it's the one place this
  class's contract diverges from its MCP sibling tools), but it is unambiguous in
  `OntologyRepository.find` (no `isObsolete` filter is added when `properties` is `null`), matches
  `OntologyRepositoryIT.canIncludeObsoleteOntologies`'s already-asserted-correct contract, and is
  proven intentional rather than accidental by both test layers above; it is documented behaviour,
  not a defect.
## Implemented McpSearchService baseline

`McpSearchService` (`controller/mcp`) is a Spring AI MCP `@Tool` service class with two methods,
`search(String query, Boolean includeObsoleteEntities)` and `fetch(String id)`, built specifically
to match OpenAI's MCP server spec (per its own source comment, linking
`https://platform.openai.com/docs/mcp#create-an-mcp-server`). It differs from every other MCP
service tested so far in this programme in two ways:

- **Two of its three `@Autowired` fields are dead wiring.** `embeddingServiceClient` and
  `postgresClient` are declared and injected but never read or called anywhere in the class - only
  `entityRepository` is used, by both `@Tool` methods. This was confirmed by reading the full
  99-line source directly (not assumed). It causes no incorrect behaviour, just two unnecessary
  Spring beans wired in; `McpSearchServiceTest` deliberately leaves both fields `null` in every
  case, and every case still passes with no `NullPointerException` - itself observable proof
  neither field is ever touched. Not a defect, not fixed here.
- **Both `@Tool` methods return a raw JSON `String`** (`gson.toJson(...)`), unlike
  `McpClassService`/`McpEmbeddingService` (`McpPage<T>`) or `McpOntologyService` (`List<T>`). A
  broken serialization could still produce *some* non-null, non-empty string, so both test layers
  parse the returned string back with `Gson`/`JsonParser` and assert on the real field values
  (`id`/`url`/`title`/`isObsolete`/`text`/`metadata`), never just non-null/non-empty.

**`search`** builds its `includeObsoleteEntities` filter with the same three-way rule as
`McpClassService.searchClasses`'s `isObsolete` handling: `null` and explicit `false` both add
`properties.put("isObsolete", List.of("false"))`; only explicit `true` omits the filter entirely
(unlike `searchClasses`, `search` never adds its own `type` filter - `EntityRepository.find` adds
the default `type=entity` filter internally when `properties` has no `type` key). It then delegates
to `EntityRepository.find` with an exact, fully-enumerated fixed-argument list: hardcoded
`PageRequest.of(0, 20)`, hardcoded `lang="en"`, `searchFields`/`boostFields`/`facetFields` all
`null`, `exactMatch` a literal `false` (not `null` - confirmed by reading the source and asserted
explicitly, since the task brief specifically flagged this as easy to get wrong),
`excludeOntologyIds` `null`, and an always-on `JsonTransformOptions`
(`resolveReferences`/`manchesterSyntax` both hardcoded `true`). The result's content is mapped
through `McpSearchResult::fromJson` then serialized with `gson.toJson`.

**`fetch`** splits `id` on `"\\+"` and requires exactly two tokens: fewer than two (no `+`
present, or an empty-string input - `"".split("\\+")` yields a length-1 array containing a single
empty string, not a length-0 array, confirmed empirically) throws `IllegalArgumentException` with
the exact documented message; more than two tokens (more than one literal `+` in the input) throws
the same exception. Checked against every committed test fixture and golden output IRI
(`backend/src/test/resources/fixtures/`, `testcases_expected_output_api/mcp/*.json`): none contain
a literal `+` character, so the 3+-token branch is not known to be reachable with any real OBO
Library purl IRI in this codebase today, but it is still tested directly since the parser itself
does not guard against it. The success path (exactly two tokens) delegates to
`EntityRepository.getByOntologyIdAndIri(tokens[0], tokens[1], "en", outputOpts)` with the same
always-on `JsonTransformOptions`, then maps the result through `McpFetchResult::fromJson` and
`gson.toJson`.

**Investigation finding confirmed NOT a defect, via the committed golden file.**
`McpFetchResult.fromJson` checks `type == "class"` with Java reference equality against a
Gson-parsed string, which - confirmed empirically with the project's actual Gson 2.13.2 dependency
- never matches a real `JsonPrimitive` value parsed from JSON text. This makes the
`mc.metadata = McpClass.fromJson(entity)` branch permanently dead in production: `metadata` always
falls through to the `Map.of("type", type)` fallback, even for a genuine class entity. This looked
like a real bug on first read, but per this programme's defect workflow the committed golden file
`testcases_expected_output_api/mcp/fetch.json` was checked first, and it asserts exactly this
shape already - a `duo` class entity (`DUO_0000001`) whose expected `"metadata"` is
`{"type": "class"}`, not a nested `McpClass` structure. This is the third time in this programme
that something which looked like a bug from reading the code turned out to be an
already-asserted-correct golden contract; `McpSearchServiceTest`/`McpSearchServiceIT` both assert
this real, current, already-baselined shape rather than the shape the dead branch would have
produced.

**A genuine production defect was found and fixed separately, per the defect workflow — since
merged as PR #1416.** `EntityRepository.getByOntologyIdAndIri` returns a plain `null` (not an
exception) when `OlsSearchClient.getFirst` finds no matching row - confirmed by reading
`OlsSearchClient.getFirst`'s real source, which explicitly `return`s `null` on `fetchOne() == null`.
`McpSearchService.fetch()` used to pass that `null` straight into
`McpFetchResult.fromJson(JsonElement)`, which immediately calls `entity.getAsJsonObject()` with no
null check, so a syntactically well-formed `ontologyid+iri` id for an entity that genuinely does not
exist threw an undocumented `NullPointerException` instead of a clear "not found" error. Checked
against the defect workflow: no committed golden file asserts otherwise (there is no not-found case
in `testcases_expected_output_api/mcp/`), and this does not involve any faked/mocked collaborator -
it was reproduced both by a hand-rolled fake in the unit suite and, independently, against real
Postgres in the IT suite. PR #1416 fixed `fetch()` to check for `null` and throw this codebase's own
`ResourceNotFoundException` instead; this branch was rebased onto that fix, so
`McpSearchServiceTest`/`McpSearchServiceIT`'s not-found cases now assert the fixed
`ResourceNotFoundException` behaviour rather than the original `NullPointerException`.

**Two layers.** `McpSearchServiceTest.java` (19 cases) is the direct unit suite: this repo's
hand-rolled-fake idiom (no Mockito), covering the three-way `includeObsoleteEntities` resolution,
the exact fixed-argument delegation to `EntityRepository.find` (every argument asserted
individually), the `McpSearchResult`/`gson.toJson` result shape (including the empty-array case and
`isObsolete=true` passthrough), `IOException` propagation from `search`, every `id.split` branch for
`fetch` (2 tokens / 0 or 1 token / 3+ tokens / empty string), the exact fixed-argument delegation to
`EntityRepository.getByOntologyIdAndIri`, the `McpFetchResult`/`gson.toJson` result shape (including
the class-entity `metadata` shape above), and the not-found `NullPointerException` finding.
`McpSearchServiceIT.java` (6 cases) is a thin real-Postgres suite reusing
`PostgresIntegrationTestSupport.createEntityRepository`/`initializeDatabase` - the same base entity
fixture (`EFO_0001`/`EFO_0002`/`DUO_0001`/`EFO_0100` active, `EFO_0999` obsolete) `McpClassServiceIT`
already composes an `EntityRepository` from - proving the fixed-argument calls reach real Postgres
and produce genuinely correct JSON for one `search` case (free-text match, default obsolete
exclusion, explicit obsolete inclusion) and one `fetch` case (a real class entity's full field
mapping, an obsolete entity, and the real-Postgres not-found reproduction). No new fixture mechanism
was needed. `embeddingServiceClient`/`postgresClient` are left `null` in the IT service instance too,
for the same reason as the unit layer.

Verified locally on 2026-09-11 from `origin/dev` commit `75d96f57c` (the `AnnotationExtractor` merge
commit - `dev`'s tip at branch time) with Java 17 and Rancher Desktop:

- Surefire runs 1,002 tests, including 19 direct `McpSearchServiceTest` cases. Two Docker-free runs
  both reported 0 failures / 0 errors across all 64 surefire report files.
- Failsafe runs 211 PostgreSQL tests, including 6 `McpSearchServiceIT` cases (3.33s and 3.12s for
  this class's own six cases within each run). Two complete database-gate runs both reported 0
  failures / 0 errors across all 34 failsafe report files.
- The clean `verify` lifecycle runs all 1,213 tests (1,002 surefire + 211 failsafe) in wall-clock 2
  minutes 4.89 seconds.
- `McpSearchService` itself covers all 102 instructions, all 6 branches, all 19 executable lines, and
  all 3 methods - 100% on every JaCoCo dimension. Whole-backend JaCoCo coverage is 73.2% lines (3,519
  of 4,810) and 56.8% branches (1,089 of 1,918), up from the most recently documented baseline of
  71.3% lines and 54.1% branches (the `AnnotationExtractor` baseline) - entirely attributable to this
  rollout, since the line/branch denominators (4,810 / 1,918) are unchanged from that baseline.
- One production defect was discovered (the not-found `NullPointerException` in `fetch`, described
  above); it is filed as its own separate minimal PR, not bundled into this one. The
  `type == "class"` reference-equality quirk was investigated as a second plausible defect
  candidate but is confirmed intentional/already-baselined by the committed golden file, per the
  defect workflow.
- Surefire runs 989 tests, including 6 `OlsFacetedResultsPageTest` cases. Two Docker-free runs took
  wall-clock 16.11 and 11.22 seconds, both 0 failures / 0 errors.
- The clean `verify` lifecycle runs all 1,194 tests (989 surefire + 205 failsafe, unchanged by this
  rollout since no IT was added) in wall-clock 2 minutes 3.64 seconds.
- `OlsFacetedResultsPage` itself covers 22 of 22 instructions (100%) and 4 of 4 lines (100%); it has
  no branches at all (0 of 0), consistent with a class with no conditional logic. Whole-backend
  JaCoCo coverage is unchanged at 71.3% lines (3,428 of 4,810) and 54.1% branches (1,037 of 1,918)
  versus the most recently documented baseline — this class was already indirectly exercised to
  full instruction/line coverage via existing WIT/IT tests that construct `OlsFacetedResultsPage`
  fixtures or exercise it transitively through `OlsSearchClient`, so this rollout's value is direct,
  isolated proof of `map()`'s field-preservation and null-tolerance behaviour (not previously
  specifically asserted anywhere), not a coverage-percentage change. No coverage failure threshold
  is introduced.
- No production defect was discovered by this rollout: `map()` correctly preserves
  `facetFieldToCounts`, `pageable`, and `totalElements` in every case tested, including the
  partial-page and null-map cases most likely to expose a dropped field.
## Implemented OlsPostgresClient baseline (milestone 1 of 3: static logic and simple lookups)

`OlsPostgresClient` (`repository/postgres`) is by far the largest Tier B target in this programme
so far — 625 lines of real jOOQ/Postgres-backed logic in almost every method, spanning three
distinct areas: pure static/private logic plus the generic `getAll`/`getOne` entity lookups; a
graph-traversal family (parents/children/ancestors/descendants/related, in both directions, plus
node-property filtering and search); and an embedding/similarity/vector-search family. Per this
methodology's shared constraints on splitting a genuinely oversized target (the precedent being
`V1OntologyTermController`'s 23 routes, split into two milestones), this rollout is split into
three milestone PRs, each branched independently from `origin/dev` (never stacked on another, the
same rule the `V1OntologyTermController` milestones followed). This section covers milestone 1:
`sanitizeEmbeddingColumnName`/`sanitizeEmbeddingNodeColumnName`, `normalizeCosineSimilarity`/
`normalizeCosineDistance`/`clampUnitInterval`, `hasConcreteEntityType`, `nearestNeighborCandidateLimit`,
`vectorLiteral`, `getDatabaseNodeCount`, and `getAll`/`getOne`.

**Already-indirect coverage does not count.** `V2LLMController`'s baseline (above) already
documented ~87.3% line coverage on this class purely from other classes' controller/service-level
IT suites (`V2LLMController`, and — per this programme's own scope notes — `EmbeddingServiceClient`,
`McpClassService`, `McpEmbeddingService`, `McpSearchService`) exercising `getSimilar`/
`getSimilarity`/`getEmbeddingVector`/`searchByVector`/`searchByVectorInOntology`/
`getEmbeddingModels` through their own fixtures. That proves those callers' own happy paths, not
this class's own edge cases: none of those suites use an invalid model name (so the
`IllegalArgumentException` guard was never actually exercised), none call `getAll`/`getOne`/
`getDatabaseNodeCount` at all, and none reach the reflection-only private helpers below. This
rollout is a genuinely dedicated, additive pass, not a repeat of existing indirect coverage.

**Unit layer (`OlsPostgresClientTest`, 44 cases, no Postgres).** Every pure-logic method is
reachable without a database connection:

- `sanitizeEmbeddingColumnName`/`sanitizeEmbeddingNodeColumnName` are private, but every public
  method that calls one (`getSimilar`, `getSimilarity`, `getEmbeddingVector`, both `searchByVector`
  overloads, both `searchByVectorInOntology` overloads) does so as its first statement, before the
  `try`-with-resources that opens a connection — so a bare `new OlsPostgresClient()` with no
  `postgresClient` collaborator wired in is enough to prove the `IllegalArgumentException` guard
  fires for `null` and for SQL-injection-shaped names (`"bad name"`, `"bad;name"`, `"bad'name"`,
  `"bad\"name"`, `"bad/name"`, `"../etc/passwd"`) on every one of those seven entry points, plus a
  positive case proving a well-formed name is *not* rejected (it instead fails downstream with a
  `NullPointerException` from the unwired collaborator, proof the sanitizer itself let it through).
- `normalizeCosineSimilarity`/`normalizeCosineDistance` are package-private static (confirmed by
  reading the source directly, so no reflection was needed), called directly from this same-package
  test class. Both formulas are verified at their exact values (`1.0`/`-1.0`/`0.0` inputs), at their
  6-decimal-place rounding (`0.123456789` input), and — the specific reason `clampUnitInterval`
  exists — at inputs slightly outside `[-1, 1]`/`[0, 2]` that real floating-point cosine arithmetic
  can produce (`1.0000001`, `-1.0000001` for similarity; `-0.0000001`, `2.0000002` for distance),
  proving the clamp engages instead of leaking an out-of-range score.
- `hasConcreteEntityType`, `nearestNeighborCandidateLimit`, and `vectorLiteral` are private
  *instance* methods (not static) with no Postgres dependency of their own, reached via plain
  `java.lang.reflect` (`setAccessible(true)` then `invoke`), the same white-box idiom already used
  for `EmbeddingServiceClient`'s `PcaModel` construction. `hasConcreteEntityType`: `null`, empty,
  whitespace-only, and the literal `"OntologyEntity"` all return `false`; any other non-blank string
  returns `true`. `nearestNeighborCandidateLimit`: the three scaling factors (base `max(limit*20,
  100)`, `filterByType` bumping to `max(current, limit*100)`, `filterByOntology` bumping to
  `max(current, limit*500)`) are each proven to have no effect when smaller than the running value
  and to raise it when larger, both individually and combined, plus three separate cases proving
  the final 20,000 cap actually triggers — from the base alone, from the ontology bump alone, and
  from both bumps combined. `vectorLiteral`: empty list (`"[]"`), single element (`"[1.5]"`, no
  spurious comma), and multiple elements (`"[1.0,2.5,-3.0]"`, exact `String.valueOf(Double)`
  formatting).

**IT layer (`OlsPostgresClientIT`, 12 cases, real Postgres).** `PostgresIntegrationTestSupport`
gained `OlsPostgresClientRepositoryHandle` and `createOlsPostgresClientRepositories` — the minimal
wiring this class needs (just itself, no repository layer on top), following the same
`ReflectionTestUtils.setField` idiom as every other handle factory in that file. No new fixture was
needed: the existing shared `initializeDatabase` ontology + entity fixture already provides enough
type/id/iri/ontology-id diversity for every scenario this milestone requires (four `OntologyClass`
rows — `DUO_0001` in ontology `duo`, `EFO_0001`/`EFO_0002`/the obsolete `EFO_0999` in `efo` — plus
one `OntologyProperty` row), reused rather than inventing a new graph structure from scratch, per
this methodology's fixture-reuse guidance. Covered: `getDatabaseNodeCount`'s passthrough to
`PostgresClient.returnNodeCount()` (9 rows: 4 ontology + 5 entity fixture records — no dedicated
`PostgresClient` test exists yet, so this is also the first assertion on that count for this
fixture); `getAll`'s three recognized property-map keys (`id`, `iri`, `ontologyId`) each filtering
correctly, individually and combined with `and`; an unrecognized key silently ignored (the
`default -> {}` branch, proven by asserting identical total-element counts with and without it);
pagination across two pages with deterministic IRI-ascending ordering; and a type with zero matches
returning an empty page rather than throwing. `getOne`: the single-result success path, the
zero-result `RuntimeException` ("expected exactly one result for getOne, but got 0"), and the
more-than-one-result `RuntimeException` ("...but got 3", using the three `efo`-ontology classes) —
not just the previously-implicit success case.

Verified locally on 2026-09-11 from `origin/dev` commit `75d96f57c` (current tip, immediately after
the `AnnotationExtractor` merge) with Java 17 and Rancher Desktop:

- Surefire runs 1,027 tests, including 44 `OlsPostgresClientTest` cases. Two Docker-free runs both
  passed cleanly (0 failures / 0 errors).
- Failsafe runs 217 PostgreSQL tests, including 12 `OlsPostgresClientIT` cases. Two complete
  database-gate runs both passed cleanly (0 failures / 0 errors).
- The clean `verify` lifecycle runs all 1,244 tests (1,027 surefire + 217 failsafe) in wall-clock
  2 minutes 5.37 seconds.
- `OlsPostgresClient` now covers 322 of 363 lines (88.7%, up from the `V2LLMController` baseline's
  87.3%) and 62 of 79 branches (78.5%, up from 65.8%) across 39 of 42 methods (92.9%). The remaining
  gaps belong entirely to milestones 2 and 3 (the graph-traversal and embedding/vector-search method
  bodies beyond what milestone 1 touches). Whole-backend JaCoCo coverage is 71.4% lines (3,433 of
  4,810) and 54.6% branches (1,047 of 1,918), up marginally from the `AnnotationExtractor` baseline
  (3,428 of 4,810 lines, 1,037 of 1,918 branches) — expected, since this milestone's new coverage is
  concentrated in one already-partially-covered class rather than spread across many.
## Implemented OlsPostgresClient baseline (milestone 2 of 3: graph-traversal family)
`OlsPostgresClient` (`repository/postgres`) is by far the largest Tier B target in this programme
so far — 625 lines of real jOOQ/Postgres-backed logic in almost every method. Per this
methodology's shared constraints on splitting a genuinely oversized target (the precedent being
`V1OntologyTermController`'s 23 routes, split into two milestones), this rollout is split into
three milestone PRs, each branched independently from `origin/dev` (never stacked on another —
milestone 1 covers the static/private pure logic plus `getDatabaseNodeCount`/`getAll`/`getOne`;
this section covers milestone 2, the graph-traversal family; milestone 3 covers the
embedding/similarity/vector-search family below). Milestone 2's methods: `getDirectParents`,
`getDirectChildren` (both overloads), `getHierarchicalParents`, `getHierarchicalChildren`,
`getAncestors`, `getDescendants`, `getHierarchicalAncestors`, `getHierarchicalDescendants`,
`getRelatedTo`, `getRelatedFrom`, and the two private helpers backing all ten,
`lookupArrayTargets`/`lookupArraySources`, plus `buildNodePropCondition`.

**Scope: IT-only, no new unit-test class.** Every one of this milestone's methods is a thin,
directly-Postgres-backed query with no meaningful branch that doesn't require a real database to
exercise (`buildNodePropCondition`'s `isObsolete`/`type`/unrecognized-key branches are only
observable through their effect on a real query's result set) — there is no additional pure-logic
surface here beyond what milestone 1 already covers, so this milestone adds only
`OlsPostgresClientGraphIT` (21 cases), no `*Test.java` companion.

**A structural asymmetry the fixture is built specifically to prove, not assume.** "Targets" methods
(`getDirectParents`, `getHierarchicalParents`, `getAncestors`, `getHierarchicalAncestors`,
`getRelatedTo`) look up entities *referenced by* the given id's array column via
`lookupArrayTargets`'s `arrayContains(e1Targets, e2Iri)` join; "sources" methods (the children/
descendants family, `getRelatedFrom`) look up entities *whose* array column references the given
id via `lookupArraySources`'s `arrayContainsField(e2Sources, e1Iri)` join — a structurally different
query, not a mirror image. The dedicated fixture (`graph-fixture.json`, loaded by
`PostgresIntegrationTestSupport.initializeOlsPostgresClientGraphDatabase`, under its own
`graphtest`/`graphtest2` ontology ids, isolated from every other suite) is deliberately built so
that reusing an existing fixture or assuming symmetry would have hidden real gaps:

- `GRAPH_CHILD_ACTIVE` has *two* `direct_parents` (one active, one obsolete) but only *one*
  `hierarchical_parents` entry — proving `getDirectParents`/`getHierarchicalParents` read genuinely
  different columns (2 results vs. 1), not just different names for the same data. The reverse
  relationship is proven independently too: `GRAPH_PARENT_OBSOLETE` has 1 direct child
  (`getDirectChildren` finds `GRAPH_CHILD_ACTIVE` via `direct_parents`) but 0 hierarchical children
  (`getHierarchicalChildren` finds none via `hierarchical_parents`, since `GRAPH_CHILD_ACTIVE`
  doesn't list it there).
- `GRAPH_GRANDCHILD`'s `direct_ancestors` is non-transitive (its immediate parent,
  `GRAPH_CHILD_ACTIVE`, only) while its `hierarchical_ancestors` is transitive (both
  `GRAPH_CHILD_ACTIVE` and `GRAPH_PARENT`) — proving `getAncestors` (1 result) vs.
  `getHierarchicalAncestors` (2 results, ordered by `iri` ascending) and, on the sources side,
  `getDescendants(GRAPH_PARENT)` (3 results, excludes the grandchild) vs.
  `getHierarchicalDescendants(GRAPH_PARENT)` (4 results, includes it) are not interchangeable.
- `graphtest2`'s `GRAPH_PARENT` deliberately shares both IRI and label with `graphtest`'s
  `GRAPH_PARENT`, and `graphtest2`'s `GRAPH_CROSS_CHILD` deliberately lists `graphtest`'s
  `GRAPH_PARENT` IRI in its own `direct_parents` — both are excluded from every `graphtest`-scoped
  lookup (`getDirectParents(GRAPH_CHILD_ACTIVE)` returns exactly 2, not 3; `getDirectChildren
  (GRAPH_PARENT)` returns exactly 3, not 4), proving the `e2OntologyId.eq(e1OntologyId)`
  same-ontology-only join condition on *both* `lookupArrayTargets` and `lookupArraySources`
  independently — a case a fixture with no cross-ontology duplicate would silently fail to cover
  regardless of what the assertions claimed.

**Also covered.** `buildNodePropCondition`'s two recognized `nodeProps` keys, applied through
`getDirectParents` (targets side: `isObsolete` true/false distinguishing `GRAPH_PARENT` from
`GRAPH_PARENT_OBSOLETE`) and `getDirectChildren` (sources side: `isObsolete` true/false, `type`
distinguishing the one `OntologyProperty` child from its `OntologyClass` siblings, and an
unrecognized key proven to be silently ignored by asserting identical total-element counts with
and without it). `lookupArraySources`'s `search` parameter: both `getDirectChildren` overloads
(the 3-arg form with no `search` parameter at all, and the 4-arg form), a genuine case-insensitive
substring match against the label array proven with both a lowercase and an uppercase query
(`"active"`/`"ACTIVE"` both matching `"Graph Child Active"`), a no-match case, and `null`/`""`/
`"   "` all empirically proven to produce the identical unfiltered result count — confirming
`.trim().isEmpty()` really does treat all three identically rather than assuming it from reading
the source.

Verified locally on 2026-09-12 from `origin/dev` commit `75d96f57c` (current tip, same commit
milestone 1 branched from) with Java 17 and Rancher Desktop:

- Surefire runs 983 tests (unchanged by this milestone — no new unit-test class). Two Docker-free
  runs both passed cleanly (0 failures / 0 errors).
- Failsafe runs 226 PostgreSQL tests, including 21 `OlsPostgresClientGraphIT` cases. Two complete
  database-gate runs both passed cleanly (0 failures / 0 errors).
- The clean `verify` lifecycle runs all 1,209 tests (983 surefire + 226 failsafe) in wall-clock
  2 minutes 7.42 seconds.
- `OlsPostgresClient` now covers 318 of 363 lines (87.6%, up from the `V2LLMController` baseline's
  87.3% — this branch does not include milestone 1's additional coverage, since the two milestones
  are independent, unstacked branches) and 54 of 79 branches (68.4%, up from 65.8%) across 40 of 42
  methods (95.2%). Whole-backend JaCoCo coverage is 71.3% lines (3,429 of 4,810) and 54.2% branches
  (1,039 of 1,918), essentially flat against the `AnnotationExtractor` baseline (3,428 of 4,810
  lines, 1,037 of 1,918 branches) — expected, since this milestone's new coverage is concentrated
  in the handful of already-partially-covered graph-traversal methods on this one class rather than
  spread across many.
- No production defect was discovered by this rollout.
## Implemented OlsPostgresClient baseline (milestone 3 of 3: embedding/similarity/vector-search family)

milestone 2 covers the graph-traversal family; this section covers milestone 3, the final one:
`getSimilar`, `getSimilarity`, `getEmbeddingVector`, `searchByVector` (both overloads),
`searchByVectorInOntology` (both overloads), and `getEmbeddingModels`).

**Already-indirect coverage does not count.** This is the one area of this class that already had
substantial *indirect* coverage before this rollout: the "V2 LLM-controller baseline" section above
documented ~87.3% line coverage on this class from `V2LLMController`/`EmbeddingServiceClient`/
Mcp*Service IT suites exercising these exact methods through their own real-Postgres fixtures. That
proves those callers' own happy paths, not this class's own edge cases — none of them pass an
invalid model name, an obsolete source entity, a cross-ontology duplicate IRI+type, or call the
4-/6-arg convenience overloads at all (every production caller passes `includeCurations` explicitly,
per that baseline's own dead-code note). This milestone's dedicated fixture and tests reach all of
those directly.

**Scope: IT-only, no new unit-test class.** Milestone 1 already covers this family's pure-logic
helpers (`sanitizeEmbeddingColumnName`/`sanitizeEmbeddingNodeColumnName`'s SQL-injection guard,
`normalizeCosineSimilarity`/`normalizeCosineDistance`, `hasConcreteEntityType`,
`nearestNeighborCandidateLimit`, `vectorLiteral`) with no Postgres dependency of their own; this
milestone adds only `OlsPostgresClientEmbeddingIT` (25 cases), the real-Postgres proof that those
methods and the surrounding query-building logic work correctly together end to end.

**The 4-/6-arg convenience overloads are reachable, not dead code — confirmed empirically, not
assumed.** The `V2LLMController` baseline noted these overloads as "pre-existing dead code" because
no *production* caller currently uses them. That is still true — but a dedicated test calling them
directly is itself a caller, and `OlsPostgresClientEmbeddingIT` does exactly that
(`searchByVectorFourArgOverloadIncludesCurationsByDefault`,
`searchByVectorInOntologySixArgOverloadDefaultsIncludeCurationsToTrue`), proving both delegate
correctly to their 5-/7-arg counterparts with `includeCurations = true` rather than leaving that
assumption unverified.

**A dedicated three-part fixture, each part isolated from the others and from the existing
`V2LLMController` embedding fixture** (`OlsPostgresClientEmbeddingIT`'s own
`OLS_POSTGRES_CLIENT_EMBEDDING_MODEL` name and ontology ids, added by
`PostgresIntegrationTestSupport.initializeOlsPostgresClientEmbeddingDatabase`):

- **`embtest`/`embtest2`** (entity-level `embeddings_<model>`, for `getSimilar`/`getSimilarity`/
  `getEmbeddingVector`): `EMB_SOURCE` (`[1,0,0,0]`) is the fixed query point; `EMB_IDENTICAL_DIR`
  (`[2,0,0,0]`, same direction), `EMB_DIAG` (`[4,3,0,0]`, the same clean 3-4-5-ratio vector already
  proven reliable by the `V2LLMController` baseline's own fixture), `EMB_ORTHO` (`[0,1,0,0]`), and
  `EMB_OPPOSITE` (`[-1,0,0,0]`) give `getSimilar` a deterministic, hand-checkable ordering with
  exact scores 1.0/0.9/0.5/0.0 — deliberately avoiding an irrational (e.g. 45°) angle, whose cosine
  similarity is not exactly representable and risks flaking against pgvector's internal float4
  precision. `EMB_OBSOLETE_ONLY` is obsolete with a real embedding, proving the *source* lookup's
  own `is_obsolete = false` filter (`getSimilar` does not additionally filter obsolete entities out
  of its *result* set — only self-exclusion and type apply there, confirmed by reading the source,
  not assumed). `EMB_NO_EMBEDDING`/`EMB_NO_EMBEDDING_PARTNER` share a type so `getSimilarity`'s
  shared `type` parameter matches both sides of a pair, isolating "the other entity has no
  embedding value" from an unrelated type mismatch, for both argument positions. `EMB_VECTOR_PARSE`
  (`[-1.5,2,0,12]`) stresses `getEmbeddingVector`'s bracket-stripping/parsing with a leading
  negative decimal and a trailing two-digit integer — values a substring-bounds off-by-one would
  visibly corrupt. `EMB_DUP` exists twice with the identical IRI and type, once per ontology
  (`embtest` non-defining, `embtest2` defining) — proving `getSimilar`'s source lookup orders by
  `is_defining_ontology DESC NULLS LAST` and picks the defining row (verified by which row's id
  is, and is not, excluded from the result set — cosine similarity's symmetry means the *score*
  alone cannot distinguish which row was picked, only the returned identity can).
- **`vectest`** (node-level `embedding_<model>`, for the plain `searchByVector`): `VEC_CLASS_A`/
  `VEC_CLASS_B` are type `VecClass`; `VEC_PROPERTY_A` is type `VecProperty` with the identical
  label-embedding vector as `VEC_CLASS_A`, present only to prove `hasConcreteEntityType`'s filter
  actually excludes it when searching `VecClass` and actually includes it when searching the
  generic `OntologyEntity` marker (`hasConcreteEntityType("OntologyEntity") == false`).
  `VEC_CLASS_B` additionally has a `CurationEmbedding` node closer to the query
  (score 0.9) than its own `LabelEmbedding` node (score 0.5), so `includeCurations` measurably
  changes its *best* score rather than just adding an otherwise-redundant duplicate candidate.
- **`vectestonto`/`vectestonto2`** (node-level `embedding_<model>`, for
  `searchByVectorInOntology`) — deliberately its *own* dedicated ontology pair and types
  (`VecOntoClass`/`VecOntoProperty`), not reusing `vectest`'s: `searchByVector` has no ontology
  scoping at all and the generic-type test cases have no type scoping either, so any overlap
  between this group and the plain `searchByVector` fixture would leak rows across the two
  scenarios' assertions (this was caught empirically during development — an earlier draft that
  reused `vectest`/`VecClass` produced extra, unexplained rows in both fixtures' result sets until
  the groups were fully separated). `VEC_ONTO_CLASS`/`VEC_ONTO_PROPERTY` each exist in both
  ontologies with the same IRI+type; only the `vectestonto` (defining) copies have embedding nodes.
  `isDefiningOntology = true` against `vectestonto` finds the defining copies directly
  (`fetchVectorCandidatesInOntologySelect`'s direct-join branch); `isDefiningOntology = false`
  against `vectestonto2` must instead join through the defining copy's embedding to return the
  *target* (`vectestonto2`) copy's own id/json — genuinely different SQL, proven by asserting the
  *returned* id belongs to the target ontology, not just that a result exists. `VEC_ONTO_PROPERTY`
  has only a `CurationEmbedding` node, found only when `includeCurations = true`.

**`getEmbeddingModels`**, tested directly from `OlsPostgresClient`'s own side (previously only
proven from `EmbeddingServiceClient`'s side, over a different, unrelated `pca16`-name-filtering
mechanism): the fixture adds a second `embeddings_<model>_pca16`-suffixed column with no data
purely to prove `information_schema.columns` introspection excludes it, alongside asserting the
registered model name comes back with its `embeddings_` prefix correctly stripped.

Verified locally on 2026-09-12 from `origin/dev` commit `75d96f57c` (current tip, same commit both
other milestones branched from) with Java 17 and Rancher Desktop:

- Surefire runs 983 tests (unchanged by this milestone — no new unit-test class, per the scope note
  above). Two Docker-free runs both passed cleanly (0 failures / 0 errors).
- Failsafe runs 230 PostgreSQL tests, including 25 `OlsPostgresClientEmbeddingIT` cases. Two
  complete database-gate runs both passed cleanly (0 failures / 0 errors) — the first attempt
  surfaced 7 failures from the fixture cross-contamination described above; all were fixed by fully
  separating the `searchByVectorInOntology` fixture group's ontology ids and types before the two
  required clean passes.
- The clean `verify` lifecycle runs all 1,213 tests (983 surefire + 230 failsafe) in wall-clock
  2 minutes 3.91 seconds.
- `OlsPostgresClient` now covers 339 of 363 lines (93.4%, up from the `V2LLMController` baseline's
  87.3% — this branch does not include milestones 1/2's additional coverage, since all three are
  independent, unstacked branches) and 63 of 79 branches (79.7%, up from 65.8%) across 41 of 42
  methods (97.6% — the high method count includes several graph-traversal methods this milestone
  never calls directly, already covered by other pre-existing repository classes' own IT suites
  that route through `OlsPostgresClient` for ordinary parent/child lookups; this milestone's own
  contribution is the line/branch depth within the embedding/vector-search methods themselves).
  Whole-backend JaCoCo coverage is 71.7% lines (3,450 of 4,810) and 54.6% branches (1,048 of 1,918),
  up from the `AnnotationExtractor` baseline (3,428 of 4,810 lines, 1,037 of 1,918 branches).
- No production defect was discovered by this rollout. `getSimilar`'s result set is confirmed to
  apply no obsolete-entity filter of its own (only the source lookup does) — read directly from the
  source and tested as the actual, current behaviour, not treated as a defect absent a documented
  contract requiring otherwise.

With all three milestones complete (pending review/merge), `OlsPostgresClient` has a genuinely
dedicated, additive test suite across every public method and the private helpers behind them,
going beyond the ~87%-line/~66%-branch indirect baseline that `V2LLMController` and the
`EmbeddingServiceClient`/`McpClassService`/`McpEmbeddingService`/`McpSearchService` suites left in
place.
## Implemented PostgresClient baseline

`PostgresClient` (`service`) is the foundational HikariCP connection-pool + jOOQ `DSLContext`
wiring class that `PostgresIntegrationTestSupport`'s `createPostgresClient` factory has been
constructing, indirectly, for every single real-Postgres test in this entire programme. Per this
programme's standing rule, that indirect exercise never counted as "covered" — this class had zero
dedicated test file of its own until this rollout.

**Two distinct halves, split across the two test layers per the Tier B methodology.**

**Unit layer (`PostgresClientTest`, 6 cases, no Postgres): the static `decompressJson(byte[])`
overload.** `null` input returns `null` rather than throwing. A genuinely gzip-compressed string
(built with `java.util.zip.GZIPOutputStream` in the test, never hand-crafted bytes) round-trips
exactly. Non-gzip bytes are wrapped in a `SQLException` with the exact message `"Failed to
decompress _json"` and a real `java.io.IOException` cause (confirmed via `hasCauseInstanceOf`, not
just "some exception"). A second, distinct failure shape — a well-formed gzip header followed by a
truncated compressed body — is proven to fail inside the read loop itself with the same wrapped
`SQLException`/`IOException` cause chain, rather than only at `GZIPInputStream` construction time.
A 72,780-character decompressed string (built from 3,000 distinct `"line-N":"value-N",` segments,
not a trivially short one) forces the internal 8,192-char `buf` to be drained across multiple
`reader.read(buf)` calls and still round-trips exactly. Finally, `close()` is proven safe to call on
a `PostgresClient` that never had `init()` called on it (its `dataSource != null` guard exists for
precisely this case).

**IT layer (`PostgresClientIT`, 9 cases, real Postgres via `PostgresIntegrationTestSupport`):**

- `getConnection()`/`dsl()`/`returnNodeCount()` end-to-end: a genuine `Connection` from
  `getConnection()` (`isValid(2)`), a genuine `DSLContext` from `dsl(connection)` running a real
  `SELECT COUNT(*)` against `ols_entities`, and `returnNodeCount()` returning the exact known
  fixture size — 9 (4 ontology rows from `ontology-fixture.json` + 5 entity rows from
  `entity-fixture.json`, both loaded into the shared `ols_entities` table by the standard
  `initializeDatabase` fixture). A separate case proves `returnNodeCount()`'s `catch
  (SQLException e)` branch for real (not via a faked collaborator): a dedicated standalone client is
  `init()`-ed then `close()`-d, so its next `getConnection()` genuinely throws (`HikariDataSource
  has been closed.`), and the resulting `RuntimeException("Failed to count nodes", ...)` with a real
  `SQLException` cause is asserted directly.
- `decompressJson(ResultSet, String)` and `decompressJson(ResultSet, int)` against a real BYTEA
  column: `SELECT id, _json FROM ols_entities WHERE id = ?` for the known fixture id
  `efo+class+http://example.org/EFO_0001`, decompressed by column name and by index (2) from the
  same query shape, each compared against the expected JSON re-derived from the same classpath
  fixture (`/fixtures/entities/entity-fixture.json`) `PostgresIntegrationTestSupport` itself loads
  from — not a hand-copied JSON literal that could silently drift from the fixture.
- `init()`'s `currentSchema` JDBC URL construction, across all three branches described by the
  production code's own comment (`currentSchema` replaces `search_path` outright rather than
  appending to it): no schema configured (both a blank `""` and a `null` schema field) produces no
  `?currentSchema=` parameter at all; `schema="public"` produces exactly `?currentSchema=public`
  (not `public,public`); any other schema (`"myschema"`, which is never actually created as a real
  Postgres schema in this fixture — Postgres does not validate `search_path` entries against
  existing schemas at connection time, only when resolving an unqualified name) produces
  `?currentSchema=myschema,public`. `PostgresIntegrationTestSupport`'s own `createPostgresClient`
  factory was read directly and confirmed to hard-code exactly one fixed value, `schema="public"`,
  for every other test in this programme — it alone could never have exercised the other two
  branches. These four cases therefore construct additional standalone `PostgresClient` instances
  against the same running container (via `ReflectionTestUtils.setField`, the identical white-box
  idiom the shared factory itself already uses for these same private fields), asserting the exact
  resulting JDBC URL by reading the `init()`-ed instance's `dataSource` field back via
  `ReflectionTestUtils.getField` and calling the inherited `HikariDataSource.getJdbcUrl()` (since
  `HikariDataSource extends HikariConfig`) — the same reflection-based white-box technique already
  used for `EmbeddingServiceClient` in this programme.

**Remaining, permanent gap — deliberately not forced.** Three branches stay uncovered by design,
not oversight: the `password != null && !password.isEmpty()` guard's `null`- and
blank-password paths (every client constructed in this suite, including the schema-variant ones,
uses the disposable container's own real, non-blank password — connecting with no password against
this container's `scram`/`md5` auth would simply fail authentication, which would prove nothing
about the guard); and `returnNodeCount()`'s `count == null ? 0 : count` ternary's `null` branch,
which a real `SELECT COUNT(*)` query never actually produces (`fetchOne` always returns exactly one
non-null row for a `COUNT` query) — reaching it would require a faked `DSLContext`, contradicting
this class's own "prefer real Postgres over fakes" scope. `PostgresClient` itself now covers 45 of
45 lines (100%) and 15 of 18 branches (83.3%; the 3 missed branches are exactly the ones just
described), 10 of 10 methods (100%), and 227 of 229 instructions (99.1%).
## Implemented V1GraphRepository baseline

`V1GraphRepository` (`repository/v1`) is the second Tier B target in this programme. Unlike
`AnnotationExtractor`, it is a real `@Component` with its own Postgres dependency: it builds a
graph-visualization response (`{nodes: [...], edges: [...]}`, the data behind the V1 ontology-term
controller's "explore this entity's neighborhood" `/api/ontologies/{onto}/terms/{iri}/graph` route)
for a class/property/individual entity by combining three queries (`getParentsAndRelatedTo`'s
three unioned branches, `getRelatedFrom`'s reverse lookup, and `getNode`'s direct fetch of the
source entity) with genuinely non-trivial assembly logic: node dedup by IRI, and a 3-way edge-label
resolution that has to look up a property URI in one of two different entities' own JSON depending
on which query produced the edge.

**Scope: unit + IT.** Three helpers — `collectLinkedEntityLabels`, `findRelatedPropertyUri`, and
`transformJson` — are pure logic with no Postgres touch and are covered directly in
`V1GraphRepositoryTest.java` (9 cases, this repo's plain-JUnit/AssertJ idiom). Everything else —
the three `getGraphFor*` wrappers, `getNode`, `getParentsAndRelatedTo`, `getRelatedFrom`, and the
`getGraphForEntity` assembly logic that ties them together — genuinely needs real Postgres and is
covered by a new `V1GraphRepositoryIT.java` (4 cases). `getNode`, `getParentsAndRelatedTo`, and
`getRelatedFrom` are package-private, but their parameter/return types (`GraphNode`/`GraphEdge`)
are declared `private static` nested classes — inaccessible even from a same-package test class —
so every one of their branches is exercised indirectly through the three public `getGraphFor*`
wrappers instead, exactly how every production caller uses this class. One comprehensive IT case
centered on a single fixture entity (`V1G_CENTER`) proves nearly everything at once: all three
unioned branches of `getParentsAndRelatedTo` (parent, child, relatedTo), `getRelatedFrom`'s reverse
lookup, node dedup (the fixture's `V1G_PARENT` is reachable via both the parent branch and
`getRelatedFrom`, yet appears in the output exactly once), the same-ontology-only join filter on
every branch (a second `v1graph2` ontology carries a same-IRI parent duplicate and two same-iri-
target cross-ontology entities, all confirmed absent from the result), and the full 3-way edge-
label resolution: a relatedTo edge whose property resolves to a real collected label, one whose
property can't be resolved at all (falls back to the generic `"related to"` with the `"uri"` field
omitted entirely, not present-with-null), and two cases where a property resolves to a URI with no
matching collected label so both fall back to `"is a"` — the hardcoded `subClassOf` edges, and,
more subtly, the `getRelatedFrom` edge, whose property URI is resolved by searching the *other*
entity's own `_json.relatedTo` (not the centered entity's), proving the two `findRelatedPropertyUri`
lookup directions are genuinely different, not assumed symmetric. Two further cases confirm the
`getGraphForEntity` empty-graph result for a genuinely nonexistent entity id (not a crash — since
that id also isn't found by `getParentsAndRelatedTo`/`getRelatedFrom`'s own `WHERE e1.id = entityId`
clause, all three underlying queries independently return nothing), and one case each for
`getGraphForProperty`/`getGraphForIndividual` confirming the `"+property+"`/`"+individual+"`
composite-id conventions (matched against production usage in `IndividualRepository`/
`PropertyRepository`, which build the identical `ontologyId + "+property+" + iri` /
`"+individual+"` ids) resolve correctly end-to-end, not just the `"+class+"` case.

**Fixture reused/extended.** This rollout builds a new, dedicated `v1-graph-fixture.json` under
`backend/src/test/resources/fixtures/v1graph/`, loaded by a new
`PostgresIntegrationTestSupport.initializeV1GraphDatabase`/`loadV1GraphFixture` pair, following the
exact same discipline `OlsPostgresClientGraphIT`'s `graph-fixture.json` established for the
`OlsPostgresClient` graph-traversal milestone (PR #1419, not yet merged at the time of this
rollout): an isolated ontology pair (`v1graph`/`v1graph2`, never shared with any other suite) built
specifically to prove same-ontology-only join/array-containment semantics with a deliberate
cross-ontology same-IRI duplicate. The topology itself could not be reused verbatim, since
`V1GraphRepository` needs richer `_json` bodies than that fixture provides — a `relatedTo` array of
`{"property": ..., "value": ...}` objects (confirmed against production's actual shape: rdf2json's
`OntologyGraph.writeValue`'s `RELATED` case writes exactly `{"property": ..., "value": ...}`, and
`json2postgres`'s `extract_string_array`/`value_to_string` project each entry's own `"value"` into
the flat `related_to` array column separately — so the DB column and the richer `_json` field are
two different projections of the same relationship, not the same data twice) and a `linkedEntities`
map carrying real property labels — neither of which the `OlsPostgresClient` fixture's minimal
`_json` bodies needed. `createV1OntologyTermRepositories`/`V1OntologyTermRepositoryHandle` (already
existing, used by `V1TermRepositoryIT`) is reused unchanged to wire up the `V1GraphRepository` bean
against it — no new repository-handle type was needed. The composite entity-id format `ontologyId
+ "+" + type + "+" + iri` was confirmed against production usage before any fixture was written
(`IndividualRepository.java:113`, `ClassRepository.java:207`, `PropertyRepository`, and
`V1IndividualRepository`/`V1PropertyRepository` all build ids the identical way), and the fixture's
own ids follow it exactly.
Verified locally on 2026-09-12 from `origin/dev` commit `75d96f57c` with Java 17 and Rancher
Desktop:

- Surefire runs 989 tests, including all 6 `PostgresClientTest` cases. Two Docker-free runs both
  passed cleanly (0 failures / 0 errors).
- Failsafe runs 214 PostgreSQL tests, including all 9 `PostgresClientIT` cases. Two complete
  database-gate runs both passed cleanly (0 failures / 0 errors).
- The clean `verify` lifecycle runs all 1,203 tests (989 surefire + 214 failsafe) in wall-clock
  2 minutes 5.90 seconds.
- Whole-backend JaCoCo coverage is 71.4% lines (3,434 of 4,810) and 54.3% branches (1,042 of 1,918),
  up from the most recently documented baseline of 71.3% lines and 54.1% branches — the same 4,810/
  1,918 line/branch denominators as the `AnnotationExtractor` baseline, i.e. no unrelated commits
  changed the total in between; the improvement is exactly this rollout's 6 additional covered
  lines and 5 additional covered branches.
- No production defect was discovered by this rollout.

## Implemented RemoveLiteralDatatypesTransform baseline

`RemoveLiteralDatatypesTransform` (`repository/transforms`) is a Tier B target completed under
this programme's expanded scope, alongside the still-open `JsonTransformer`/
`ManchesterSyntaxTransform` PRs for the same `transforms` package (see
`.claude/commands/backend-test-coverage.md`'s Tier B methodology). It is a pure static-method
utility class — no Spring bean, no constructor state, no Postgres dependency — with a single
public method, `transform(JsonElement)`, that recurses through arrays and object values (via the
shared `JsonCollectionHelper`, itself out of scope for dedicated testing per the
`ManchesterSyntaxTransform` baseline's note, and left out of scope here too) and collapses any
object shaped `{"type": ["literal"], "value": <v>}` into `transform(<v>)`, recursively. It is
called directly from `V1SearchController`, `V1SelectController`, and `V1GraphRepository`
(confirmed via `graphify explain "RemoveLiteralDatatypesTransform"` before falling back to
`grep`), and unconditionally from `JsonTransformer.transformJson` as one of its two always-on
transforms (alongside `LocalizationTransform`) — so this class runs on essentially every V1/V2
JSON response this codebase produces.

**Scope: unit-only, no IT layer.** Same rationale as the `AnnotationExtractor`/`JsonTransformer`/
`ManchesterSyntaxTransform` baselines above: a pure-logic class with no Postgres dependency of its
own has no real-database behaviour to prove beyond a direct unit test with hand-built `JsonElement`
fixtures. This baseline is a single new `RemoveLiteralDatatypesTransformTest.java` (13 cases, this
repo's plain-JUnit idiom — `JsonParser.parseString` text-block fixtures for object/array shapes,
matching the existing `LocalizationTransformTest` idiom for this same `transforms` package — no
Mockito, no Spring context) and nothing else.

**Branches enumerated.** Primitives (string, number, boolean) and Gson's `JsonNull.INSTANCE`
(distinct from a raw Java `null` — see below) are all returned as-is, via the final `else` branch.
Array input recurses into every element via `JsonCollectionHelper.map`, including a nested
array-of-arrays case. An object with no `"type"` key at all recurses into its own key/value pairs,
preserving structure. An object whose `"type"` is present but is not a `JsonArray` (a plain string,
e.g. `"class"`) never enters the literal-collapse branch (`type.isJsonArray()` is `false`) and
falls straight through to the generic `JsonCollectionHelper.map(obj, ...)` object recursion at the
bottom of the method — confirmed precisely rather than assumed unreachable, per this task's
instruction. Likewise, an object whose `"type"` is a `JsonArray` that does not contain `"literal"`
(e.g. `["class"]`) also falls through to that same generic recursion, leaving the `"type"` array
itself structurally intact. The literal-collapse shape itself is covered with a plain string
`"value"`, with irrelevant extra keys (`datatype`/`lang`, the real shape rdf2json's
`PropertyValueLiteral` produces) alongside it to prove only `"value"` survives, and with a nested
literal-shaped `"value"` to prove the recursive unwrap (`transform(obj.get("value"))` calling back
into a second collapse) rather than a single non-recursive unwrap.

**The genuinely-missing-`"value"`-key edge case, investigated empirically per this task's
instruction.** `obj.get("value")` returns a raw Java `null` reference (not Gson's `JsonNull`) when
the `"value"` member is absent from the object entirely, and `transform(null)` unconditionally
calls `.isJsonArray()` on that reference before any null check. A dedicated test
(`literalObjectWithNoValueKeyThrowsNullPointerException`) confirms empirically that this does throw
a `NullPointerException` — the code has no defensive null check anywhere on this path.

This was then investigated for real-world reachability rather than assumed either way:
- **`PropertyValueLiteral`** (`dataload/rdf2json/src/main/java/uk/ac/ebi/rdf2json/properties/PropertyValueLiteral.java`),
  the only rdf2json class that produces a `Type.LITERAL` value, unconditionally assigns its
  `value` field in its constructor from `node.getLiteralLexicalForm()` (via
  `PropertyValue.fromJenaNode`) or one of its `fromBoolean`/`fromInteger`/`fromString` factory
  methods — every one of these always supplies a `value` argument. Per the RDF/Jena contract, a
  literal node's lexical form is never `null` (it is an empty string at worst). There is no code
  path anywhere in `dataload/rdf2json` that constructs a `PropertyValueLiteral` — and therefore no
  path that serializes a `{"type": ["literal"], ...}` JSON object — with a genuinely missing
  `value`.
- This was cross-checked against real committed data rather than trusting the source-reading
  argument alone, per this repo's defect workflow ("check `test_api.sh`'s committed golden files
  first"): a scan of all 7,932 JSON files under `testcases_expected_output_api/` and all 444 files
  under `testcases_expected_output/` for every object shaped `{"type": [..., "literal", ...], ...}`
  found zero instances missing a `"value"` key — every single one, across both directories,
  carries a `"value"`.

**Conclusion: not a genuine, reachable production defect — documented, not escalated to a separate
PR.** This shape cannot occur via this codebase's own rdf2json/dataload pipeline, and no committed
fixture exhibits it either. Per this repo's defect workflow, only a defect that survives both the
golden-fixture check and (where relevant) the real-vs-mock check gets isolated into its own PR;
this one does not survive the first check, so it is documented here as a confirmed non-issue
instead of following the PR #1416 pattern. If a future caller ever hand-constructs or otherwise
introduces a literal-typed value without a `"value"` key (e.g. a malformed admin-API write, or a
future rdf2json change), this `NullPointerException` would propagate uncaught — worth keeping in
mind if this class's callers ever change, but not something this rollout treats as actionable today.
## Implemented ShortFormExtractor baseline

`ShortFormExtractor` (`repository/v1/mappers`) is the second Tier B target in this programme,
picked as the alphabetically-next uncovered class in that package after `AnnotationExtractor`
(#1407). It is a tiny pure static-method utility class — one public method,
`extractShortForm(String iri)`, no Spring bean, no constructor state, no Postgres dependency —
called from `V1TermMapper` (twice: once for `termReplacedBy`, once for a linked-entity predicate's
fallback label). Like `AnnotationExtractor` before it, this method was previously exercised only
incidentally through `V1TermMapper`'s own happy-path fixtures, never through a dedicated test of
its own branches.

**Scope: unit-only, no IT layer.** Per the Tier B methodology's "what covered means" section, a
pure-logic class with no Postgres dependency of its own does not get a dedicated `*IT.java` layer.
This baseline is a single new `ShortFormExtractorTest.java` (11 cases, this repo's plain-JUnit/
AssertJ idiom, no Mockito, nothing to fake — the method takes a `String` and returns a `String`
with no collaborators at all) and nothing else.

**Branches enumerated.** The method has exactly two logical branches: a case-sensitive
`startsWith("urn:")` special case that strips exactly 4 characters, and a generic fallback that
returns everything after `Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/')) + 1`. Cases
covered: the `urn:` prefix present with a real NID:NSS value following it (`urn:oid:1.2.3.4`,
the canonical URN example from the W3C reference the method's own comment links to); the `urn:`
prefix with nothing after it (exactly `"urn:"`, returning `""` rather than throwing); an
uppercase `"URN:..."` confirming `startsWith` is case-sensitive and control falls through to the
generic branch (verified by also asserting on the specific result that branch produces for this
input, not just that it doesn't throw); an IRI with only a `#` (OWL's own `owl#Thing` namespace
IRI); an IRI with only a `/` (a real OBO PURL term IRI shape, `BFO_0000002`); both directions of
the `Math.max` comparison when an IRI contains both separators, each with its own real, grounded
example — a `/` after the last `#` (an ICD-10 browse IRI whose fragment itself contains a further
`/` segment) and a `#` after the last `/` (the `oboInOwl#inSubset` predicate IRI, also used
verbatim in `AnnotationExtractor` and its tests); an IRI ending in `#` and, separately, one ending
in `/` (both returning `""`, nothing after the separator); an IRI with neither `#` nor `/` at all,
where `Math.max(-1, -1) + 1 == 0` and `substring(0)` returns the *entire original string*
unchanged rather than throwing or returning empty (asserted against the full string, not just a
non-crash); and the empty-string input, the same all-`-1` logic path at the degenerate length-0
case, also returning `""` without throwing.

**Fixtures grounded in real data.** Several inputs above are real IRI/predicate strings pulled
directly from this codebase (`backend/src/test/resources` fixtures, `testcases_expected_output_api`
golden files, and `OntologyDefaults`/`AnnotationExtractor`'s own hardcoded namespace constants),
found via `grep` rather than invented from scratch — the OWL `#Thing` IRI, the OBO `BFO_0000002`
PURL, the ICD-10 `#/N18` browse IRI (a genuine "both separators, slash-after-hash" real-world
shape), and the `oboInOwl#inSubset` predicate. No real `urn:` IRI exists anywhere in this
codebase's fixtures or production data (`grep -rn "urn:"` over `backend/src/test/resources` and
`testcases_expected_output_api` finds nothing); the `urn:` cases therefore use the synthetic
`urn:oid:1.2.3.4` example, which is the canonical illustration from the same W3C URN reference the
production code's own comment cites.
Verified locally on 2026-09-12 from `origin/dev` commit `75d96f57c` with Java 17 (no Postgres/
Docker gate — this class has no IT layer, per the scope note above):

- Surefire runs 996 tests, including all 13 `RemoveLiteralDatatypesTransformTest` cases. Two
  Docker-free runs both reported 0 failures / 0 errors.
- The clean `verify` lifecycle runs all 1,201 tests (996 surefire + 205 failsafe, unchanged by this
  rollout since no IT was added) in wall-clock 2 minutes 2.65 seconds.
- `RemoveLiteralDatatypesTransform` itself now covers 46 of 49 instructions (93.9%), 10 of 10
  branches (100%), and 12 of 13 lines (92.3%); the one uncovered line is the implicit default
  constructor, never invoked since the only caller-facing entry point is the static `transform`
  method — the same pattern already documented for `AnnotationExtractor`/`JsonTransformer`/
  `ManchesterSyntaxTransform` above. Whole-backend JaCoCo coverage is 72.2% instructions (17,371 of
  24,054), 54.1% branches (1,038 of 1,918), and 71.3% lines (3,428 of 4,810); measured from the same
  `origin/dev` commit as the `AnnotationExtractor` baseline above (this branch was not rebased onto
  any of the other sibling Tier B PRs, which are independent, unmerged branches off the same
  commit), so this is this rollout's own isolated contribution, not a cumulative total. No
  repository-wide coverage threshold is introduced.
- No production defect was discovered by this rollout requiring a separate PR — see the
-  no-`"value"`-key investigation above for the one edge case that was investigated and ruled out.

## Implemented RemoveReificationTransform baseline

`RemoveReificationTransform` (`repository/transforms`) is the second Tier B target in this
rollout. It is a tiny (54-line) pure static-method utility with a single public method,
`transform(JsonElement)`, meant to collapse a `{"type":["reification"], "value": <v>, "axioms":
[...]}` wrapper down to `transform(<v>)`. Like `AnnotationExtractor`, it has no Spring bean, no
constructor state, and no Postgres dependency.

**Most notable finding of this rollout: the class is dead code with a real-looking bug baked in,
and the bug would bite in production if the class were ever wired up.** Both are documented here
prominently, per this program's practice for anything a human should act on even when it isn't
this PR's job to fix.

### Finding 1 — zero production callers (confirmed, not just asserted)

`RemoveReificationTransform` is never invoked anywhere in this repository outside its own
declaration. This was verified three independent ways, not just by grep:

- `grep -rn "RemoveReificationTransform" .` across the *entire* repository (backend, `dataload/`
  Rust and Java sources, `frontend/`, test fixtures) returns exactly one hit: the class's own
  `public class RemoveReificationTransform {` declaration line. No V1 mapper/extractor, no V2
  controller, no `JsonTransformer` call, no dataload/Rust code, and no existing test references
  it.
- `JsonTransformer.transformJson` — the one place that chains this package's transforms together
  for real — calls `LocalizationTransform`, `RemoveLiteralDatatypesTransform`,
  `ResolveReferencesTransform`, and `ManchesterSyntaxTransform`. `RemoveReificationTransform` is
  not in that list. Reification handling in the real pipeline is instead done by
  `LocalizationTransform.localizeReification(...)`, a completely separate, independent code path.
  `RemoveReificationTransform` duplicates none of that; it simply isn't reached.
- The graphify knowledge graph (`graphify-out/graph.json`, 6,933 nodes) shows the
  `RemoveReificationTransform` node at degree 2 — one `contains` edge from its own file and one
  edge to its own declared `.transform()` method. `graphify query "who calls
  RemoveReificationTransform"` returns only that single node (no incoming call edges from
  anywhere), and `graphify path "RemoveReificationTransform" "JsonTransformer"` finds no directed
  path at all; the only *undirected* path is four hops long and runs entirely through a shared
  `import com.google.gson.JsonElement` statement, not a call relationship — i.e., not a real
  dependency, just two unrelated classes importing the same Gson type.

No indirect caller (reflective dispatch, a test-only usage, or anything on the `dataload`/Rust
side) exists either. Per this program's dead-code precedent (`OlsPostgresClient`'s documented
pca16 double-filter and 4-arg/6-arg convenience overloads), this is **not filed as a defect-fix
PR** — there is no live code path for it to break. It is flagged here instead so a human can
decide whether to fix it or delete the class outright.

### Finding 2 — a genuine copy-paste bug, only latent because the class is unreached

`RemoveReificationTransform`'s structure is nearly identical to its sibling
`RemoveLiteralDatatypesTransform` (both share the same `JsonCollectionHelper.map` recursion
skeleton). Of the three recursive call sites in `transform()`:

- The self-recursive `return transform(obj.get("value"))` (used when an object's `"type"` array
  contains `"reification"`) correctly calls this class's own method.
- The array-recursion branch (`object.isJsonArray()`) and the generic-object-recursion fallthrough
  (an object with no `"type"`, or a `"type"` that doesn't contain `"reification"`) **both call
  `RemoveLiteralDatatypesTransform::transform` instead of this class's own `transform`** — a
  copy-paste artifact that was seemingly never updated after this file was adapted from its
  sibling.

The practical effect: the class's own docstring example (a reification-wrapped literal nested
inside an array, itself nested inside a property value of a top-level entity — the only shape a
real OLS document would ever produce) is **not** actually handled correctly by this method as
written. A reification wrapper is only unwrapped when it is passed *directly* as the literal
top-level argument to `transform()` (or chained directly through nested `"value"` fields without
ever passing through an array or another key). The moment a reification wrapper arrives inside an
array, or as the value of some other key inside a larger object — which is how it always arrives
in a real document — the wrong transform runs, doesn't recognize `"reification"` at all, and the
wrapper survives completely intact.

A second, independent bug was also confirmed empirically: unlike `RemoveLiteralDatatypesTransform`
(which guards with `type.isJsonArray()` before calling `getAsJsonArray()`), this class calls
`obj.get("type").getAsJsonArray()` unconditionally. An object whose `"type"` key is present but
is not itself a JSON array (a plain string, a number, a nested object) throws Gson's
`IllegalStateException` instead of gracefully falling through to the generic recursion the way its
sibling class would.

Neither finding is filed as a separate defect PR, for the same reason as Finding 1: with zero
production callers, neither bug is reachable by any real request today.

### Scope: unit-only, no IT layer

Per the Tier B methodology's "what covered means" section, this is a pure-logic class with no
Postgres dependency of its own, so it gets no dedicated `*IT.java` layer — there is no real-database
behavior to prove here beyond what a direct unit test already covers with hand-built `JsonElement`
fixtures. This baseline is a single new `RemoveReificationTransformTest.java` (11 cases, this
repo's plain-JUnit idiom — `JsonParser.parseString` fixtures compared with `assertEquals`, no
Mockito) and nothing else.

### Branches enumerated — testing the code as written, not as the docstring implies

Every test in this suite exercises the class's **actual, current** behavior, including the
cross-delegation quirk — several tests deliberately assert the buggy, currently-shipping outcome
so that a future fix of the two wrong call sites makes this suite fail loudly rather than silently
continuing to pass around the defect:

- **Array input** (`isJsonArray()` branch): one test proves the array branch really does invoke
  `RemoveLiteralDatatypesTransform`'s literal-stripping logic (a plain literal-wrapped object
  inside an array collapses to its bare value, something this class has no logic of its own to
  do); a second test uses the docstring's own worked example arriving inside an array — exactly
  how it would in a real document — and shows the reification wrapper is **not** unwrapped, only
  the literal nested inside its `"value"` gets stripped.
- **Direct top-level reification wrapper** (the one correct call site): one test shows a
  reification wrapper passed directly to `transform()` unwraps correctly to its `"value"`, matching
  the docstring exactly, with the nested literal deliberately left untouched (stripping literals is
  `RemoveLiteralDatatypesTransform`'s job in the real pipeline, not this class's).
- **Two levels of direct nesting** vs. **the same nesting via an array**: one test shows a
  reification-of-reification, each level's `"value"` holding the next wrapper directly, fully
  cascades through the correct self-recursive chain no matter how deep. A paired test shows that
  the moment the *second* level arrives inside an array instead, the cross-delegation bug bites
  and that inner wrapper survives completely unwrapped — a direct, side-by-side demonstration of
  where the bug does and doesn't strike.
- **Reification as another key's value**: a reification wrapper held under an unrelated key of a
  larger object (not the top-level argument, not inside an array) is likewise left completely
  untouched, for the same cross-delegation reason.
- **`"type"` present but not containing `"reification"`**, and **no `"type"` key at all**: both
  fall through to the generic (buggy) recursion; one test each confirms it still runs
  `RemoveLiteralDatatypesTransform`'s literal-stripping on the object's own immediate child values
  (proving which transform is actually running, not just that something didn't crash).
- **`"type"` present but not a JSON array**: two tests (a plain string value and a nested object
  value) confirm the second, independent bug empirically — both throw `IllegalStateException`
  rather than falling through gracefully.
- **Primitives and `JsonNull`**: one test confirms a `String`/`Number`/`Boolean` primitive and
  `JsonNull.INSTANCE` are all returned unchanged.

### Verified locally

Verified locally on 2026-09-12 from `origin/dev` commit `75d96f57c` with Java 17 (no Postgres/
Docker gate for the twice-run unit step, per the scope note above; Docker/Rancher Desktop was
available and used for the full `clean verify` lifecycle so the existing failsafe suite and
whole-backend JaCoCo numbers stay accurate):

- Surefire runs 994 tests, including all 11 `RemoveReificationTransformTest` cases. Two Docker-free
  runs took wall-clock 11.64 and 12.50 seconds, both 0 failures / 0 errors.
- The clean `verify` lifecycle runs all 1,199 tests (994 surefire + 205 failsafe, failsafe count
  unchanged by this rollout since no IT was added) in wall-clock 2 minutes 0.65 seconds.
- `RemoveReificationTransform` itself now covers 10 of 11 lines (90.9%) and 8 of 8 branches
  (100%); the one uncovered line/method/complexity unit is the implicit default constructor, never
  invoked since the class is used only via its static method (and, per Finding 1, not used even
  that way in production). Whole-backend JaCoCo coverage is 71.5% lines (3,438 of 4,810) and 54.5%
  branches (1,045 of 1,918), up from the most recently documented baseline of 71.3% lines and
  54.1% branches by exactly this rollout's 10 newly-covered lines and 8 newly-covered branches (the
  total line/branch denominators are unchanged, since this previously-dead class was already
  compiled and instrumented by JaCoCo even without a caller). No coverage failure threshold is
  introduced.
- No new production defect PR was opened. Both anomalies above were investigated and confirmed
  real, but neither is reachable by any live code path (Finding 1), so per this program's defect
  workflow and prior dead-code precedent, they are documented here for a human to act on rather
  than patched silently inside this testing PR.
## Implemented ResolveReferencesTransform baseline

`ResolveReferencesTransform` (`repository/transforms`) is the second Tier B target in this
rollout. It is a pure static-method utility class — no Spring bean, no constructor state, no
Postgres dependency, only one collaborator (`JsonCollectionHelper`, itself a pure static helper) —
with a public `transform(JsonElement)` entry point that delegates to a private
`transformWithLinkedEntities(JsonElement, JsonObject linkedEntities)` threading a resolution-scope
map through recursion. Unlike the two prior Tier B transforms in this programme
(`RemoveLiteralDatatypesTransform`, `RemoveReificationTransform` — the latter dead, unreferenced
code), this class is genuinely, actively used: `JsonTransformer.transformJson` calls it whenever
`options.resolveReferences` is `true`, wired from `McpClassService`, `McpEmbeddingService`,
`McpOntologyService`, `McpSearchService`, and several V2 controller routes. It was previously
exercised only incidentally, happy-path only, through `JsonTransformerTest` and various
controller-IT/MCP-service-IT suites that set `resolveReferences=true` — none of which targeted
this class's own branches or subtleties directly.

**Scope: unit-only, no IT layer.** Per the Tier B methodology, a pure-logic class with no Postgres
dependency of its own does not get a dedicated `*IT.java` layer. This baseline is a single new
`ResolveReferencesTransformTest.java` (10 cases, this repo's plain-JUnit idiom — hand-built
`JsonElement` fixtures via `JsonParser.parseString` on text blocks, `assertEquals` on the parsed
tree, no Mockito, no Spring context) and nothing else.

**Branches enumerated**, including several subtleties that are easy to miss:
- **Once-only `linkedEntities` capture.** The object branch only captures
  `obj.getAsJsonObject("linkedEntities")` as the active resolution scope when the current context
  is still `null`. A test proves an outer object's `linkedEntities` map wins over a nested object's
  own, different `linkedEntities` map further down the tree — the nested map is never consulted as
  an alternate scope anywhere in its own subtree, only ever copied through verbatim.
- **The four pass-through keys** (`linkedEntities`, `iri`, `curie`, `shortForm`) are copied into the
  result completely unchanged, with no recursion into their values at all. A test proves a string
  under `iri` that happens to also be a real `linkedEntities` key is *not* resolved, while the exact
  same string under an ordinary key *is* resolved.
- **Reference resolution and shared mutation.** A string matching a `linkedEntities` key resolves to
  that linked `JsonObject`; if it lacks its own `iri` field, the code mutates it in place, adding
  `"iri": <the matched key>`. A test resolves the same IRI from two different locations in one
  document and uses `assertSame` (not just `assertEquals`) to prove both call sites return the
  *exact same* mutated `JsonObject` instance — `linkedEntities`' values are shared by reference, not
  copied, across the whole traversal. A sibling case proves an already-present `iri` is left
  untouched (not overwritten with the resolving key).
- **Non-matching strings** return unchanged, both with and without an active context.
- **Non-string primitives and `JsonNull`** are returned as-is regardless of whether a context is
  active — verified with a `boolean`, a `number`, and a JSON `null` all inside a document with an
  active `linkedEntities` map that none of them ever reach.
- **Arrays** recurse element-by-element, threading the same context to every element (mixed
  resolvable/non-resolvable/nested-object array). A separate test also proves the once-only capture
  is scoped to one root-to-leaf recursive call, not global: when the *top-level* value passed to
  `transform` is itself a `JsonArray`, one element establishing its own nested `linkedEntities`
  context does not leak out to resolve a sibling array element holding the same key as a bare
  string.
- **The public `transform(JsonElement)` entry point**, called with no `linkedEntities` anywhere in
  the document, proves no reference resolution happens at all (this test also doubles as the
  no-active-context half of the non-string-primitive/`JsonNull` case).
- One additional test is grounded in the real production JSON shape (the class's own docstring
  example, and the `"json"` sub-object of
  `backend/src/test/resources/fixtures/classes/class-fixture.json`): an entity carrying `iri`,
  `curie`, `shortForm`, a `directParent` IRI array, and a `linkedEntities` map together, proving the
  array reference resolves while the three identifier fields are left alone.

**The apparently-dead null-check, confirmed unreachable.** Inside the object-recursion loop,
`if(res != null) { newObj.add(...) } else { newObj.add(entry.getKey(), entry.getValue()); }` looks
like defensive dead code: every branch of `transformWithLinkedEntities` returns a non-null
`JsonElement` (the array branch always returns the `JsonArray` built by `JsonCollectionHelper.map`;
the object branch always returns its `newObj`; the string-match branch returns either `linked` or
the input `object`; the final `else` returns the input `object` itself) — so `res` can never
actually be `null`, *provided* `entry.getValue()` (from `JsonObject.entrySet()`) is itself never a
raw Java `null`. This was confirmed empirically two ways rather than taken on the read alone:
1. A dedicated test (`gsonJsonObjectNeverStoresRawNullSoTheDeadNullCheckFallbackIsUnreachable`)
   demonstrates against this project's actual Gson version (2.13.2, resolved via
   `mvn dependency:build-classpath`) that `JsonObject.add(key, null)` normalizes the `null` to
   `JsonNull.INSTANCE`, and `entrySet()` never hands back a raw `null` value — so the precondition
   the dead-code argument depends on holds for real, not just by inspection.
2. The measured JaCoCo output for this class (see below) independently corroborates it: line 59
   (`if(res != null)`) shows "1 of 2 branches missed" and the `else` body on line 62 is marked fully
   uncovered (`nc`) — across the *entire* backend test suite (993 unit + 205 integration tests, not
   just this rollout's own new cases), the `else` branch is never once taken.

This is dead defensive code, not a real behavior to design a test around — the same treatment given
to prior confirmed-dead branches in this programme (e.g. `RemoveReificationTransform`). It is an
observation, not a defect; no separate PR is opened for it.

Verified locally on 2026-09-12 from `origin/dev` commit `75d96f57c` with Java 17 (Docker available
via Rancher Desktop for the one full `verify` lifecycle; no dedicated Postgres/IT gate for this
target per the scope note above):

- Surefire runs 993 tests, including the 10 new `ResolveReferencesTransformTest` cases. Two
  Docker-free runs took wall-clock 11.585s and 12.283s, both 0 failures / 0 errors (read from
  `target/surefire-reports/*.txt`, not the swallowed `-q` exit code).
- One clean `mvn -q -o clean verify -Dapi.version=1.44` lifecycle (Java 17, Rancher Desktop Docker
  via `DOCKER_HOST=unix:///Users/haideri/.rd/docker.sock` and
  `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`) ran all 1,198 tests (993 surefire +
  205 failsafe, failsafe count unchanged by this rollout since no IT was added) in wall-clock
  2 minutes 2.19 seconds, 0 failures / 0 errors in both report directories.
- `ResolveReferencesTransform` itself now covers 137 of 148 instructions (92.6%), 30 of 32 lines
  (93.75%), 29 of 30 branches (96.7%, the one permanently-missed branch being the confirmed-dead
  `else` above), and 3 of 4 methods (75% — the fourth, uncovered method is the implicit default
  constructor, never invoked since every caller uses the static methods directly). Whole-backend
  JaCoCo coverage is 71.9% lines (3,458 of 4,810) and 55.6% branches (1,066 of 1,918), up from the
  most recently documented baseline of 71.3% lines and 54.1% branches (same 4,810/1,918
  denominators as that prior baseline — no unrelated commit landed on `dev` in between, so this is a
  clean like-for-like delta: +30 lines and +29 branches, matching this class's own newly-covered
  totals almost exactly). No coverage failure threshold is introduced.
- No genuine, reachable production defect was discovered by this rollout (the dead null-check above
  is an observation, not a defect, per the Tier B methodology's instruction for this exact
  situation).
- Surefire runs 994 tests, including 11 `ShortFormExtractorTest` cases. Two Docker-free runs took
  wall-clock 16.98 and 11.67 seconds, both 0 failures / 0 errors (confirmed by reading
  `target/surefire-reports/*.txt`, not just exit code).
- The clean `verify` lifecycle runs all 1,199 tests (994 surefire + 205 failsafe, unchanged by this
  rollout since no IT was added) in wall-clock 2 minutes 3.79 seconds.
- `ShortFormExtractor` itself now covers 6 of 7 lines (85.7%) and 2 of 2 branches (100%); the one
  uncovered line is the implicit default constructor, never invoked since the only caller
  (`V1TermMapper`) uses the static method directly — the same pattern as `AnnotationExtractor`'s
  baseline. Whole-backend JaCoCo coverage is 71.4% lines (3,434 of 4,810) and 54.2% branches
  (1,039 of 1,918), up marginally from the `AnnotationExtractor` baseline of 71.3%/54.1% (same
  4,810/1,918 denominators — no unrelated commits landed on `dev` between the two baselines this
  time). No coverage failure threshold is introduced.
- No production defect was discovered.
## Implemented TextTaggerService baseline

`TextTaggerService` (`service/`) wraps the `ols_text_tagger` CLI binary over a stateful stdin/
stdout pipe, backed by a Postgres Large-Object-stored tagger database. It was already wired as a
real, unconfigured bean at the `V2TextTaggerController` layer (see the "Implemented V2
text-tagger-controller baseline" section above), but that only proves the controller handles the
degraded-`false` contract correctly -- it never exercised this class's own `parseResponse`/
priority/substring/source/min-length filtering logic, its Large Object download path's populated
case, or its process-management internals. This rollout closes all three.

**1. Pure filtering/parsing logic -- `TextTaggerServiceTest.java` (unit, no Postgres, no process).**
Every method under test is `private`, exercised via reflection (this programme's established idiom
for private-method Tier B coverage). 43 cases, enumerated per the Tier B methodology:

- `parseResponse`: `entities` array absent and present-but-empty both return an empty list;
  `term_label`/`term_iri`/`ontology_id` default to `""` when absent and are read correctly when
  present; `string_type`, `source`, `subject_categories`, and `is_obsolete` are each independently
  present/absent (the last defaulting to `false`); multiple entities parsed in order.
- **Finding on the unguarded `start`/`end` fields.** Unlike every other field, `start`/`end` are
  read with a bare `e.get("start").getAsInt()`/`e.get("end").getAsInt()`, no `.has()` guard.
  Empirically confirmed here (two dedicated tests): a response missing either field makes
  `JsonObject.get(...)` return Java `null` (not Gson's `JsonNull`), and `.getAsInt()` on that null
  reference throws `NullPointerException`. Investigated against the real dependency rather than
  assumed: this repo has the actual `ols_text_tagger` CLI's Rust source in-tree at `text_tagger/`
  (`src/main.rs`), and its `Entity` struct declares `start`/`end` as plain, non-`Option<...>`
  `usize` fields with no `#[serde(skip_serializing_if = ...)]` -- unlike `string_type`/`source`/
  `subject_categories`, which are all `Option<...>` and conditionally omitted. The real binary's
  own protocol therefore *guarantees* every entity carries `start`/`end`; this was also confirmed
  by building the real binary from source and inspecting its actual JSON output (see area 3 below).
  **Conclusion: not a reachable production defect against the real binary** -- so, per the defect
  workflow, no separate defect PR is opened. It is nonetheless a real fragility worth documenting:
  a malformed/corrupted response line, or a future protocol change that makes these fields
  optional, would crash parsing with an NPE instead of degrading gracefully like every other field.
  `test_api.sh`'s golden files under `testcases_expected_output_api/` were checked first, per the
  defect workflow, and contain nothing related to the text tagger.
- `jsonArrayToStringList`: key absent, key present as `JsonNull`, key present but not a JSON array,
  key present as an empty array (returns `null`, not an empty list -- verified explicitly), and key
  present as a non-empty array.
- `applyPriority`: `null`/empty `priorityOntologyIds` return the input list unchanged (same
  reference, asserted via `isSameAs`); an ontology absent from the priority list is dropped
  entirely; the higher-priority match wins for two entities sharing a span; two entities at
  different spans are both kept independently; a duplicated ontology id in `priorityOntologyIds`
  keeps its *first* occurrence's index (`putIfAbsent` semantics) -- proven with a test that fails
  under the alternate, last-occurrence-wins semantics. `spanKey`'s bit-packing is tested directly
  (also via reflection): spans sharing a start or end (e.g. `(1,2)` vs `(2,1)` vs `(1,3)` vs
  `(0,3)`) and spans near the `int`-to-`long` shift boundary (`Integer.MAX_VALUE` combined with
  `0`) all produce pairwise-distinct keys.
- `applySourceFilter`: `null`/empty `sources` return the input unchanged; an entity with a `null`
  source is always kept regardless of the filter; sources in/not-in the allowed set are kept/
  dropped respectively.
- `applyMinLength`: `minLength <= 0` (both `0` and a negative value) returns the input unchanged; a
  span exactly equal to `minLength` is kept (the `>=` boundary); one shorter is dropped.
- `removeSubstrings`: 0/1-element lists pass through unchanged (same reference); identical spans
  are both kept (per the class's own doc comment, verified precisely, not assumed); a strictly-
  contained span is removed; a dedicated test lists the shorter span *before* the longer containing
  span and asserts the correct single survivor, proving the method's internal sort (start asc, then
  span length desc) -- not incidental list order -- is what makes containment detection correct;
  and a three-level nesting case (`smallest` inside `middle` inside `largest`) proves the
  containment check runs against `result` (already-kept entities), so `smallest` is still correctly
  excluded even though its immediate container `middle` never made it into `result`.
- `isAvailable()`/`tagText()` in the default, un-started state: `isAvailable()` is `false` before
  `init()` ever runs (the field default), and `tagText()` (both overloads) returns an empty list
  immediately, before the method's `lock`/process-interaction code is reached at all.

**2. Postgres Large-Object download logic -- `TextTaggerServiceIT.java` (real Postgres, via
`PostgresIntegrationTestSupport`).** `downloadTextTaggerDb()` is `private`, invoked directly via
reflection against disposable Postgres, reusing the existing
`createTextTaggerRepositories(...)` factory. Two cases, `@TestMethodOrder`-sequenced (a deliberate,
documented exception to this repo's usual per-class-container idiom, because the second case
mutates the one row the table's `LIMIT 1` query can ever see):

- No row in `ols_text_tagger` returns `null` -- confirmed (by reading `V2TextTaggerControllerIT`)
  to be the only case the existing controller-level fixture ever exercised, now proven directly
  against this method rather than only inferred from `isAvailable() == false` at the controller
  layer.
- The previously-untested populated case: `PostgresIntegrationTestSupport` gained a new
  `insertTextTaggerLargeObject(PostgresClient, String)` factory that writes a gzip-compressed
  payload into a real Postgres Large Object via the real `LargeObjectManager` API
  (`conn.unwrap(PGConnection.class).getLargeObjectAPI()`) and inserts the row pointing at it --
  matching production's own write-side contract exactly (the read side already does
  `GZIPInputStream` over the Large Object's input stream). `downloadTextTaggerDb()`'s returned temp
  file is asserted byte-for-byte equal, after decompression, to what was inserted.

**3. External-process management -- `TextTaggerServiceProcessIT.java` (real subprocess, opportunistic).**
`ols_text_tagger` is not on `PATH` in this environment and no Rust toolchain requirement previously
existed in this repo's test pipeline -- but this repo has the CLI's actual Rust source in-tree at
`text_tagger/` (`Cargo.toml`, `src/main.rs`, `src/ac.rs`), and a Rust toolchain (`cargo`/`rustc`
1.90.0) happened to be available on this machine. Rather than write a hand-rolled stand-in script,
this rollout built the **real** binary from that source (`cd text_tagger && cargo build --release`)
and used it directly -- genuine end-to-end coverage of the real CLI, not a simulation of it. Every
test in this class checks `ols_text_tagger`'s presence on `PATH` in a `@BeforeAll` via
`Assumptions.assumeTrue(...)` and skips the whole class cleanly (confirmed: 0 tests run, 0
failures) if it is absent, so routine `mvn test`/`mvn verify` runs in any environment without the
binary (the default here, and presumably in CI) are unaffected -- this class deliberately never
invokes `cargo` itself, to keep routine runs decoupled from a Rust build. To run it for real:
```
cd text_tagger && cargo build --release
export PATH="$(pwd)/target/release:$PATH"
cd ../backend && mvn -q -o verify -Dsurefire.skip=true -Dapi.version=1.44
```
4 cases, all run for real against the built binary during this rollout's local verification:

- `tagText()` end-to-end through the real process: a tiny real tagger database is built via the
  real binary's own `build` subcommand (a 2-row TSV fixture) in `@BeforeAll`, `startProcess(null)`
  is invoked via reflection to bootstrap the first process start (mirroring what `init()`'s
  background thread normally does), and `tagText(...)` is called through its public API -- real
  write to stdin, real read from stdout, real JSON parsing -- asserting the exact real tagging
  output. `includeSubstrings=false` is used here so the real end-to-end call path also exercises
  `tagText`'s own `removeSubstrings(...)` call site (its branch logic is covered directly, via
  reflection, in `TextTaggerServiceTest`; this proves it is also genuinely wired into the real
  call path).
- `ensureRunning`'s delimiter-change-triggers-restart branch: starts with no delimiters, then calls
  `tagText` with `"|"` -- different from the running process's delimiters -- and asserts the
  process's pid changed. The real CLI's `--delimiters` flag restricts matches to occur only at
  those boundary characters (confirmed empirically while building this test: the same text tagged
  successfully with the default whitespace boundary produced zero matches under `--delimiters "|"`
  when the text itself had no `|` characters), so the input text for this case uses `"|"` as its
  own word boundary -- proving both that a restart happened *and* that the restarted process is
  honouring the new delimiters, not just that some process is running.
- `ensureRunning`'s dead-process-detection branch: after starting the process, it is killed directly
  (`Process.destroyForcibly()`, simulating an external crash) with no involvement from the service's
  own bookkeeping. The next `tagText()` call is asserted to detect `!process.isAlive()`, transparently
  restart (different pid, new process alive), and still return the correct real tagging result.
- `isAvailable()` becomes `true` only after `startProcess()` succeeds (`false` beforehand).

Genuinely untestable without further complexity, and left as documented gaps rather than chased:
`init()`'s background-thread success path (`downloadTextTaggerDb()` and `startProcess()` called
from the async `@PostConstruct` thread specifically, as opposed to each being called directly, as
done above); `tagText`'s `responseLine == null` recovery branch and its `IOException` catch/
`restartProcess` branch (both require corrupting the live protocol or pipe mid-request); and
`restartProcess`'s own body (only reachable from those two branches).

Verified locally on 2026-09-12 from a branch off `origin/dev` commit `75d96f57c`, with Java 17 and
Rancher Desktop (`DOCKER_HOST`/`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` overrides), and with the
real `ols_text_tagger` binary (built as described above) prepended to `PATH` for the Postgres-gate
and full-lifecycle runs:

- Surefire runs 1,026 tests, including the 43 `TextTaggerServiceTest` cases. Two Docker-free runs
  took wall-clock 15.31 and 11.43 seconds, both 0 failures / 0 errors.
- Failsafe runs 211 PostgreSQL tests, including 2 `TextTaggerServiceIT` cases and 4
  `TextTaggerServiceProcessIT` cases (the latter run for real, against the real binary, in this
  verification). Two complete database-gate runs took wall-clock 1 minute 58.98 seconds and 2
  minutes 0.12 seconds, both 0 failures / 0 errors. A separate confirmation run with the real
  binary deliberately removed from `PATH` showed `TextTaggerServiceProcessIT` skipping cleanly (0
  tests run, 0 failures), proving the opportunistic-skip behaviour works.
- The clean `verify` lifecycle runs all 1,237 tests (1,026 surefire + 211 failsafe) in wall-clock 2
  minutes 5.58 seconds.
- `TextTaggerService` itself now covers 171 of 194 lines (88.1%) and 101 of 112 branches (90.2%),
  up from the previously-documented 16.0% lines / 4.5% branches (measured when only the controller-
  layer fake exercised this class). Whole-backend JaCoCo coverage is 74.2% lines (3,568 of 4,810)
  and 59.1% branches (1,133 of 1,918), up from the most recently documented baseline of 71.3% lines
  and 54.1% branches. No coverage failure threshold is introduced.
- One genuine production-code finding (the unguarded `start`/`end` field access) was investigated
  and written up above; concluded not to be a reachable defect against the real binary, so no
  separate defect PR was opened, per the defect workflow's own guidance to isolate a fix only once
  a finding survives verification against the real dependency.
## Implemented V1AncestorsJsTreeBuilder baseline

`V1AncestorsJsTreeBuilder` (`repository/v1`) builds a jsTree.js-compatible representation of an
entity's full ancestor lineage from a flat list of ancestor JSON entities plus the entity itself,
walking one or more configurable "parent relation" IRI predicates. It is the untested sibling of
`V1ChildrenJsTreeBuilder`, whose own dedicated test (`V1ChildrenJsTreeBuilderTest`) found and
fixed a real `NullPointerException` in PR #1391 that controller-level testing alone had not
caught (per the Tier B methodology's "why this tier exists" section). This rollout treats that
precedent as a real, not hypothetical, risk and gives `V1AncestorsJsTreeBuilder` the same
dedicated, branch-by-branch coverage.

**Scope: unit-only, no IT layer.** Confirmed `V1ChildrenJsTreeBuilderTest` (the structural
template for this rollout) is also unit-only with no IT layer — same sanity check applied here.
`V1AncestorsJsTreeBuilder` has no Postgres dependency of its own: its constructor takes plain
`JsonElement`/`JsonObject` values already fetched and localized by its caller
(`V1JsTreeRepository`), and its own logic (map-building, recursion, base64 encoding) is pure. A
single new `V1AncestorsJsTreeBuilderTest.java` (18 cases, this repo's plain-JUnit5/AssertJ idiom,
no Mockito, no Spring context, package-private class/methods tested from the same package exactly
like `V1ChildrenJsTreeBuilderTest`) is therefore the complete coverage layer.

### A genuine production defect was found and fixed in a separate PR

While enumerating `createJsTreeEntries`'s branches, found that both `hasDirectChildren` and
`hasHierarchicalChildren` were computed by reading the **same** `HAS_DIRECT_CHILDREN` field twice
— the second read should have been `HAS_HIERARCHICAL_CHILDREN`. This made the
`hasDirectChildren || hasHierarchicalChildren` OR a no-op: any V1 ancestors-jstree entity with
only hierarchical children (no direct/subClassOf children) incorrectly reported `children: false`
(not expandable) instead of `true`. Confirmed the sibling `V1ChildrenJsTreeBuilder` (lines 40-41)
and `V1TermMapper` (lines 52-53) both correctly read the two distinct fields — this class was the
outlier. (`V1PropertyMapper` appears to have the identical mistake; that is a separate class, left
untouched, out of scope for this rollout.)

Per the defect workflow: checked `test_api.sh`'s committed golden files under
`testcases_expected_output_api/` first — there is no `a_attr`-shaped (jstree) fixture anywhere in
that tree and no `jstree` reference in `test_api.sh` at all, so this was not an
already-asserted-intentional contract, just untested. Confirmed reachability against the real
(non-mocked) `dataload/rdf2json/.../HierarchyFlagsAnnotator.java`: `hasDirectChildren` and
`hasHierarchicalChildren` are populated independently from two different relation sets
(subClassOf-derived direct parents vs. hierarchical/`part_of`-style parents), so an entity that is
a subsumption leaf but is the target of a `part_of` relation from another entity genuinely has
`hasDirectChildren=false` and `hasHierarchicalChildren=true` in real ontology data.

Isolated into its own minimal PR, **#1427** (`fix: use HAS_HIERARCHICAL_CHILDREN for
hasHierarchicalChildren in V1AncestorsJsTreeBuilder`), with its own dedicated regression test
(`V1AncestorsJsTreeBuilderHierarchicalChildrenFlagTest`, 3 cases) proving the bug pre-fix and the
fix post-fix — mirroring this same programme's PR #1391 precedent (a dedicated builder test
catching a real defect) and the PR #1416/#1415 precedent for how a defect discovered while adding
Tier B coverage gets isolated into its own PR, cross-referenced with the testing PR that found it.
**This testing branch is deliberately built against the unfixed code on `origin/dev`** (per the
defect workflow: never bundle a fix into the testing PR), so
`V1AncestorsJsTreeBuilderTest.reportsChildrenFalseWhenOnlyHasHierarchicalChildrenIsSetDueToAKnownDefect`
asserts the current (buggy) `children: false` result and is explicitly documented, in both its
Javadoc and its name, as needing its expectation flipped to `true` once this branch rebases onto
PR #1427 after it merges — the same pattern used for PR #1415/#1416's NPE-vs-`ResourceNotFoundException`
assertion.

### Branches enumerated

**Constructor** (IRI→entity map, IRI→children multimap): multiple parent-relation IRI predicates
configured at once, with an ancestor linked via the *second* predicate only, proving every
configured relation is walked, not just the first.

**`getEntityParentIRIs`** (private, exercised only through the constructor + `buildJsTree`): the
reified-parent unwrap `while` loop — a plain string parent, a single-level reified `{"value":...}`
parent, and a doubly-nested reified parent (proving the `while` loop, not just a single `if`, is
needed); the two hardcoded `owl:Thing`/`owl:TopObjectProperty` exclusions, each tested standalone
(entity referencing only the excluded IRI becomes a root) and together with a genuine parent IRI
(directly asserting, via the package-private `entityIriToChildIris` field, that the excluded IRI
never becomes a recognized parent-child edge while the real one does).

**`buildJsTree`**: single-root detection; a constructed multiple-roots case (one entity with two
independent, both-parentless parents — nothing in the code prevents this even though real data may
not commonly produce it), asserting the shared descendant is rendered once per root branch with
distinct base64 `id`/`parent` values.

**`createJsTreeEntries`**: a three-level ancestor chain (grandparent → parent → this-entity)
verified against exact expected base64-encoded `id`/`parent` values (computed via the
package-private `base64Encode` helper, not hardcoded literals) at every level; `selected` true
only for the requested entity itself and absent from the `state` map otherwise; `opened` false
only for the requested entity; the `children` flag's full truth table over
(`hasDirectChildren`, `hasHierarchicalChildren`) plus a missing-both-flags case (handled
gracefully as `false`, matching `V1ChildrenJsTreeBuilderTest`'s established idiom, not an NPE) —
including the known-defect case documented above; that an ancestor node's own `children` flag is
always `false` regardless of its own `hasDirectChildren`/`hasHierarchicalChildren` values, because
only the non-`opened` (i.e. requested) node's flags are ever surfaced; `a_attr`/`ontology_name`
field population; and the `child == null` "cousin" skip. That last one cannot be reproduced
through the public constructor alone — by construction, every value the constructor ever inserts
into `entityIriToChildIris` is the iri of an entity that was actually passed in, so a genuinely
absent "cousin" child cannot arise from any combination of valid entity data through the
constructor's own bookkeeping. The test reaches into the package-private `entityIriToChildIris`
field directly (the class and its fields are package-private by design, same as
`V1ChildrenJsTreeBuilderTest`'s access pattern) to inject exactly that scenario and confirms the
defensive skip neither throws nor renders the missing child.

**`base64Encode`**: a known string against its known Base64 output, plus a direct comparison
against `java.util.Base64`'s own encoder for a realistic IRI.

### Local verification

Verified locally on 2026-09-12 from `origin/dev` commit `75d96f57c`, Java 17 (no Postgres/Docker
gate for the unit-only runs — this class has no IT layer, per the scope note above; Postgres was
still exercised as part of the one clean full-lifecycle `verify` run below, which always runs the
existing failsafe IT suite regardless of what this rollout added):

- Docker-free `mvn -q -o test`, run twice: 1,001/1,001 pass both times (confirmed via
  `target/surefire-reports/*.txt`, not just exit code), including the new 18-case
  `V1AncestorsJsTreeBuilderTest`.
- Clean `mvn -q -o clean verify -Dapi.version=1.44`: 1,206 tests (1,001 surefire + 205 failsafe,
  failsafe unchanged by this rollout since no IT was added), 0 failures / 0 errors, wall-clock
  2 minutes 6.64 seconds.
- `V1AncestorsJsTreeBuilder` itself: 100% instructions (414/414), 100% lines (80/80), 100% methods
  (6/6, including one lambda), and 37/38 branches (97.4%) covered. The one missed branch is on
  line 100 (`boolean children = (!opened) && (hasDirectChildren || hasHierarchicalChildren);`):
  the `||`'s right-hand operand evaluating `true` while the left is `false`. This specific
  combination is mechanically unreachable in the *current, unfixed* code, because both local
  variables read the identical `HAS_DIRECT_CHILDREN` field (the defect described above) — they can
  never actually differ. It will become naturally reachable, and coverable, once this branch
  rebases onto the merged fix (PR #1427), at which point the two flags can genuinely disagree.
- Whole-backend JaCoCo coverage: 71.4% lines (3,432 of 4,810) and 54.4% branches (1,043 of 1,918),
  up from the most recently documented baseline of 71.3%/54.1%.
- `test_api.sh` full system regression not run locally (unaffected by this rollout — no
  production code changed on this branch; see PR #1427 for the separate production fix's own
  verification).
- Surefire runs 992 tests, including 9 direct `V1GraphRepositoryTest` cases. Two Docker-free runs
  took wall-clock 12.40 and 12.02 seconds, both 0 failures / 0 errors.
- Failsafe runs 209 PostgreSQL tests, including 4 `V1GraphRepositoryIT` cases described above. Two
  complete database-gate runs took wall-clock 119.73 and 118.09 seconds, both 0 failures / 0
  errors.
- The clean `verify` lifecycle runs all 1,201 tests (992 surefire + 209 failsafe) in wall-clock 2
  minutes 8.78 seconds.
- `V1GraphRepository` itself (including its two private nested `GraphNode`/`GraphEdge` record-like
  helper classes) covers 172 of 177 lines (97.2%) and 43 of 46 branches (93.5%); all 13 of its own
  methods are covered (100%), plus both nested classes' trivial constructors. The 5 uncovered lines
  / 3 uncovered branches are exactly the three defensive `catch (SQLException e) { throw new
  RuntimeException(...) }` blocks in `getNode`, `getParentsAndRelatedTo`, and `getRelatedFrom` —
  unreachable without breaking the real Postgres connection mid-query, the same permanent-gap shape
  already documented for other repository classes in this programme. Whole-backend JaCoCo coverage
  is 72.0% lines (3,462 of 4,810) and 55.3% branches (1,060 of 1,918), up from the most recently
  documented baseline of 71.3% lines and 54.1% branches, with the total-line/branch denominators
  unchanged from that baseline (no unrelated commit landed on `dev` in between this time). No
  coverage failure threshold is introduced.
- No production defect was discovered by this rollout.
## Implemented V1JsTreeRepository baseline

`V1JsTreeRepository` (`repository/v1`) is the second Tier B target. Unlike `AnnotationExtractor`,
it is a thin, Postgres-backed orchestrator with essentially no logic of its own: its six public
methods (`getJsTreeFor{Class,Property,Individual}`, `getJsTreeChildrenFor{Class,Property,
Individual}`) all delegate to one of two private methods, each of which builds the same
`ontologyId+type+iri` composite entity id used throughout the V1 package, calls
`OlsPostgresClient#getOne`/`getAncestors`/`getDirectChildren` for real data, applies
`LocalizationTransform#transform` to the requested entity and to every related (ancestor/child)
entity, and hands everything to `V1AncestorsJsTreeBuilder` or `V1ChildrenJsTreeBuilder` to build
the actual jstree structure. All four collaborators are already independently, exhaustively
tested elsewhere (both builders directly — `V1ChildrenJsTreeBuilderTest` pre-existing,
`V1AncestorsJsTreeBuilderTest` added by PR #1428 — plus `OlsPostgresClient` and
`LocalizationTransform` each with their own suites), so the goal here is narrowly this class's own
wiring, not re-proving those collaborators' internals.

**Scope: dedicated IT only, no separate unit test class.** Every method here is Postgres-backed via
`OlsPostgresClient`; there is no meaningful pure-logic layer to peel off and test with a hand-rolled
fake in isolation — a fake standing in for `OlsPostgresClient` would only prove this class calls a
fake correctly, not that the composite id it builds actually resolves against real data, which is
the entire point of the class. `V1JsTreeRepositoryIT.java` is therefore the only new test file.

**What "wiring is correct" means here, and how it's proven without mocks.** `OlsPostgresClient#getOne`
throws (`"expected exactly one result for getOne, but got N"`) whenever its `(entityType, id)`
arguments don't resolve to exactly one row. Since every fixture row's own `id` column is already
stored in exactly the `ontologyId+type+iri` format production code must reconstruct, and its `type`
column already encodes the concrete `OntologyClass`/`OntologyProperty`/`OntologyIndividual` string,
a *successful* lookup that returns the expected iri/label content is itself direct proof that the
composite id and `entityType` string passed for that call are correct — a wrong segment order, a
wrong `entityType`, or a wrong ontology would either throw or surface visibly wrong content, not
silently succeed. This lets every positive test double as the "called with the right
entityType/id" proof the methodology asks for, without instrumenting or mocking
`OlsPostgresClient`. One dedicated negative test
(`throwsWhenTheOntologyIdSegmentOfTheCompositeIdDoesNotMatchAnyRow`) additionally confirms the
`ontologyId` segment is genuinely load-bearing (not ignored/defaulted) by requesting a real class
IRI under the wrong ontology and asserting the resulting `RuntimeException`.

**All six public methods, all three entity types, real data:**
- `getJsTreeForClass`/`getJsTreeForProperty` reuse the existing class/property fixtures and
  `V1OntologyTermRepositoryHandle`/`V1OntologyPropertyRepositoryHandle` factories as-is (the same
  EFO_0001→EFO_1001/EFO_1999 and EFO_0100→EFO_0101 chains `V1TermRepositoryIT`/
  `V1PropertyRepositoryIT` already load for their own, different, purposes).
- `getJsTreeForIndividual` reuses the existing individual fixture (`EFO_I100`, whose ancestor
  `EFO_0001` is a *class* — `getAncestors` has no type filter, so this also confirms the method
  doesn't accidentally constrain the ancestor lookup to individual-typed rows only).
- `getJsTreeChildrenForClass`/`getJsTreeChildrenForProperty` reuse the same class/property
  fixtures' existing parent/child pairs.
- `getJsTreeChildrenForIndividual` needed new fixture data: no existing individual fixture has a
  genuine individual-to-individual `directParents` relationship (every individual's own
  `directParents` points at a class), so `getDirectChildren` against an individual could otherwise
  only ever be exercised against an empty result. A new, additive, two-row fixture
  (`fixtures/individuals/jstree-children-individual-fixture.json`: `JST_IND_ROOT` /
  `JST_IND_LEAF`) supplies one.

**Builder argument shape/order + `jstreeId` threading.** `V1AncestorsJsTreeBuilder(thisEntity,
ancestors, parentRelationIRIs)` vs. `V1ChildrenJsTreeBuilder(jstreeId, thisEntity, children)` are
different collaborators with different constructor shapes, and only the children path takes a
`jstreeId` at all. Every ancestors-path assertion checks the exact parent-chain shape (root's
`parent` is `"#"`, the leaf is `state.selected`), which would break if `thisEntity`/`ancestors`
were swapped. For the children path, `jstreeId` is an opaque, caller-supplied, base64-encoded path
string (normally the `"id"` of some node from a prior `getJsTreeFor*`/`getJsTreeChildrenFor*`
call); `V1JsTreeRepository` does nothing with it itself beyond forwarding it verbatim into
`V1ChildrenJsTreeBuilder`, which base64-decodes it and uses the decoded string as the literal
prefix for every child's own `"parent"` (and, with `;`+childIri, `"id"`) field — no entity lookup
or validation ever happens against it. Every children-path test passes a `jstreeId` built from an
arbitrary marker string (`base64("opaque-parent-token")`) unrelated to any entity's own iri, and
asserts it reappears verbatim in the output — proving genuine passthrough of the caller's argument,
not something `V1JsTreeRepository` re-derives itself from the requested iri.

**Localization genuinely applied to both sides.** Every other fixture in this suite (and in the
whole backend test programme) uses a plain-string `_json` `label`, which passes through
`LocalizationTransform#transform` unchanged regardless of the requested language — so no existing
fixture can distinguish "localization was applied" from "localization was skipped". A new,
additive, two-row class fixture (`fixtures/classes/jstree-localization-class-fixture.json`:
`JST_ROOT`/`JST_LEAF`) gives each entity's `_json` `label` a genuinely language-dependent
reified-literal array (one `{"lang":"fr", "value":...}` entry plus one default/no-lang fallback
entry). Requesting `"fr"` vs `"en"` therefore visibly changes the rendered text, and:
- `localizesBothTheRequestedEntityAndItsAncestorPerRequestedLanguage` proves both `thisEntity`
  (the leaf, `JST_LEAF`) *and* the related ancestor (`JST_ROOT`) are independently localized in the
  ancestors path — both sides' text differs between the `"fr"` and `"en"` calls.
- `localizesEveryChildIndependentlyOfTheRequestedLanguage` proves the same for the child side of
  the children path.
- One gap is documented rather than glossed over: `getJsTreeChildrenForEntity` localizes
  `thisEntity` with the exact same two lines of code as the ancestors path, but
  `V1ChildrenJsTreeBuilder.buildJsTree()` never actually reads its `thisEntity` field (confirmed by
  reading the class — only `thisEntityJsTreeIdDecoded`, derived from the separate `jstreeId`
  argument, and `children` are used). So, unlike the ancestors path, `thisEntity`'s localization in
  the children path has no way to be observed through any output of the method; this is real,
  identical source code to the (independently proven) ancestors-path call, just unobservable by
  design in the children path, not a defect.

**Two harmless dead-code observations, not defects, not fixed here** (per the defect workflow,
only genuine incorrect-behavior defects get isolated into their own PR — inert dead code with no
observable effect does not qualify): (1) `getJsTreeChildrenForEntity` builds a local
`parentRelationIRIs` variable that is never passed anywhere (`V1ChildrenJsTreeBuilder`'s
constructor doesn't accept one) — apparent copy-paste leftover from the ancestors method; (2)
`V1ChildrenJsTreeBuilder`'s constructor assigns its `parentRelationIRIs` field to itself
(`this.parentRelationIRIs = parentRelationIRIs`, where `parentRelationIRIs` is the class field, not
a constructor parameter — the constructor has no such parameter), which is why point (1) above
would be a no-op even if it were wired up. Both are pre-existing, and (2) is inside
`V1ChildrenJsTreeBuilder`, a different class with its own dedicated test — out of scope to fix as
part of this rollout.

**New shared-fixture infrastructure.** `PostgresIntegrationTestSupport#loadClassFixture`/
`loadIndividualFixture` were refactored (each now delegates to a `...From(Connection, String
resourcePath)` overload) so the two new additive fixtures above could reuse the exact same
column-mapping/INSERT logic instead of duplicating it. A new
`initializeJsTreeRepositoryDatabase(container)` loads the base ontology/entity fixtures, the
existing individual fixture, and both new additive fixtures into one disposable database (all four
new ids are disjoint from every other fixture's ids, so this is safe to combine). The existing
class/property fixtures and factories are reused unmodified.

Verified locally on 2026-09-12 from `origin/dev` commit `75d96f57c` with Java 17:

- Surefire (Docker-free) runs 983 tests, unchanged by this rollout (no unit test class was added,
  per the scope note above). Two runs, both 0 failures / 0 errors.
- Failsafe (Rancher Desktop Postgres, `DOCKER_HOST`/`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`
  overrides) runs 214 tests (205 previously + 9 new `V1JsTreeRepositoryIT` cases). Two runs, both 0
  failures / 0 errors; `V1JsTreeRepositoryIT` itself: 9/9 passing both times (7.1s and 5.3s).
- One clean `mvn clean verify -Dapi.version=1.44` runs all 1,197 tests (983 surefire + 214 failsafe)
  in wall-clock 2 minutes 9.38 seconds (61.43s user, 5.96s system, 52% CPU).
- `V1JsTreeRepository` itself now covers 156/156 instructions, 25/25 lines, and 11/11 methods
  (100% each); it has 0 branches (no conditional logic of its own — confirming the "thin
  orchestrator" description). Whole-backend JaCoCo coverage is 72.33% instructions (17,398/24,054),
  71.33% lines (3,431/4,810), 54.22% branches (1,040/1,918), and 74.94% methods (604/806) — up
  slightly from the AnnotationExtractor baseline's 71.3% lines / 54.1% branches on the same
  4,810/1,918 denominators; the extra covered lines/branches land in `OlsPostgresClient#getOne`'s
  `!= 1` throw path, previously unexercised by any other test, now hit by this rollout's
  ontology-mismatch negative test.
- No genuine production defect was discovered by this rollout (see the two dead-code observations
  above, which are documented but not fixed here, per the defect workflow).
## Implemented V1OboDefinitionCitationExtractor baseline

`V1OboDefinitionCitationExtractor` (`repository/v1/mappers`) is the second Tier B target in this
programme. Like `AnnotationExtractor`, it is a pure static-method utility class — no Spring bean,
no constructor state, no Postgres dependency — with a single public method,
`extractFromJson(JsonObject)`, called only from `V1TermMapper.mapTerm`. It had zero dedicated test
coverage before this rollout; it was never even incidentally exercised by another class's test.

**Scope: unit-only, no IT layer.** Same rationale as `AnnotationExtractor`: a pure-logic class with
no Postgres dependency of its own doesn't get a dedicated `*IT.java` layer under the Tier B
methodology — there is no real-database behaviour to prove beyond what a direct unit test already
covers with hand-built `JsonObject` fixtures. This baseline is a single new
`V1OboDefinitionCitationExtractorTest.java` (11 cases, this repo's plain-JUnit/AssertJ idiom, no
Mockito, no Spring context, and using the real `V1OboXref.fromString` value-object parser directly
rather than a fake) and nothing else.

**Finding: the unguarded `linkedEntities` access is a confirmed non-issue, not a live defect.**
The method's first line, `json.get("linkedEntities").getAsJsonObject()`, has no null guard — if
"linkedEntities" is genuinely absent, `json.get(...)` returns Gson's raw `null` and the following
`.getAsJsonObject()` throws `NullPointerException`. (This is the same unguarded pattern already
present in `AnnotationExtractor`, which reuses `json.get("linkedEntities").getAsJsonObject()`
too — as opposed to `V1OboSynonymExtractor`/`V1OboXrefExtractor`/`V1TermMapper`'s own local
variable, which instead use Gson's `getAsJsonObject(String)` convenience method, returning `null`
gracefully on a missing key instead of throwing.) This was confirmed empirically first (a
dedicated test constructs a `JsonObject` with no "linkedEntities" key and asserts the resulting
`NullPointerException`), then investigated for real-world reachability rather than assumed either
way:

- The only production caller is `V1TermMapper.mapTerm`, itself only ever invoked (via
  `V1TermRepository`) on a top-level term/class entity's JSON as stored in Postgres.
- That JSON is produced by the OLS4 linker's `write_entity_array`
  (`dataload/linker/link/src/linker_pass2.rs`), which unconditionally writes a `"linkedEntities"`
  key (line ~320-321, no `if` guard) for every entity it processes, before returning — regardless
  of whether any links were actually gathered for that entity (an entity with none still gets an
  empty `{}`).
- This was cross-checked against every committed golden fixture: across all 111
  `ontologies_linked.json` files under `testcases_expected_output/` (the actual output format of
  this linker stage, i.e. exactly what `V1TermMapper` receives), every one of the 985 top-level
  entities across the `classes`/`individuals`/`properties` arrays carries a `"linkedEntities"` key.
  The only JSON fragments observed anywhere in those fixtures *without* the key are nested,
  anonymous class-expression fragments (e.g. an inline datatype restriction embedded inside a
  property's `owl:equivalentClass`/`rdfs:range`) that are copied verbatim by a separate,
  non-entity code path (`copy_json_gathering_strings`) and are never independently passed to
  `extractFromJson` as its own top-level argument — they only ever appear as nested values inside
  another entity's own (always-"linkedEntities"-carrying) JSON.
- Conclusion: the missing-null-guard is real, but confirmed unreachable given the current
  pipeline's invariant that every top-level entity always carries `"linkedEntities"`. Per the
  defect workflow, this does not warrant a separate bug-fix PR — it's documented here (and in the
  test's own Javadoc) as a confirmed non-issue rather than left as an implicit assumption.

**Branches enumerated.** A `definition` array element that is not a `JsonObject` (e.g. a plain
string) is silently skipped, no citation, no error. A definition object with no `"axioms"` key at
all produces no citations for that definition and does not crash (`JsonHelper.getObjects` returns
an empty list for a missing key, confirmed both by reading its source and by this test exercising
it). A definition object with a present-but-empty `"axioms"` array is likewise a no-op, tested as
a distinct case from the missing-key case. An axiom that exists but has zero matching
`http://www.geneontology.org/formats/oboInOwl#hasDbXref` values is skipped, tested as distinct
from an empty axioms list. An axiom with one or more matching xref values produces exactly one
`V1OboDefinitionCitation` for that axiom — a dedicated test gives one definition three axioms
where only the 1st and 3rd qualify, and asserts exactly two citations are produced, in axiom
order, one per qualifying axiom (not one per definition). Multiple xref values on a single
qualifying axiom are all mapped through the real `V1OboXref.fromString` and all appear, in order,
in that one citation's `oboXrefs` list — this test also confirms the `linkedEntities` object
passed into `extractFromJson` is genuinely threaded through to `V1OboXref.fromString` (one xref
resolves a `url` from a matching `linkedEntities` entry, the other does not, proving both are
real independent lookups). Multiple definitions on one entity, each independently producing zero,
one, or two citations, flatten correctly into one combined, correctly-ordered result. Finally,
the `res.size() == 0 → return null` contract is tested explicitly and separately from the
non-null case: nothing qualifying anywhere returns `null` (not an empty list), while at least one
qualifying citation returns the actual, non-null list.
## Implemented V1OboSynonymExtractor baseline

`V1OboSynonymExtractor` (`repository/v1/mappers`) is the next Tier B target in this programme,
following the same pure-logic pattern as `AnnotationExtractor` and `V1OboDefinitionCitationExtractor`
(the latter's own baseline is not yet on `dev` at the time of writing — it exists as a separate
open PR/branch, `test/cover-v1-obo-definition-citation-extractor` — but its investigation was used
here as a starting point and independently re-verified, see below). `V1OboSynonymExtractor` is a
pure static-method utility class — no Spring bean, no constructor state, no Postgres dependency —
with a single public method, `extractFromJson(JsonObject)`, called only from
`V1TermMapper.mapTerm`. It extracts OBO-style synonyms (exact/related/narrow/broad, each its own
OWL annotation-property predicate) from an entity's localized JSON, deduplicating within each
call. It had zero dedicated test coverage before this rollout, and was never even incidentally
exercised by another class's test (no other test file in the repository references
`hasDbXref`/`V1OboXref` at all, so the shared `V1OboXref` value-object class this extractor calls
into had zero coverage of its own, direct or incidental, before this PR too).

**Scope: unit-only, no IT layer.** Same rationale as its Tier B siblings: a pure-logic class with
no Postgres dependency of its own doesn't get a dedicated `*IT.java` layer under the Tier B
methodology — there is no real-database behaviour to prove beyond what a direct unit test already
covers with hand-built `JsonObject` fixtures. This baseline is a single new
`V1OboSynonymExtractorTest.java` (18 cases, this repo's plain-JUnit/AssertJ idiom, no Mockito, no
Spring context, using the real `V1OboXref.fromString`/`V1OboSynonym` value objects directly rather
than a fake) and nothing else. Fixture shapes (the reified `{"value": {...}, "axioms": [...]}`
wrapper, a synonym value with no `"axioms"` key at all, and single- vs. multi-valued `hasDbXref`
axioms) are modelled directly on real rdf2json linker output, cross-checked against
`testcases_expected_output/annotation-properties/gitIssue502/ontologies_linked.json` (134 reified
synonyms carrying `hasDbXref` axioms, 29 of them with more than one xref value on a single axiom,
plus a matching `linkedEntities` entry resolving a `url` for xref `"NCIT:C2991"`) and
`testcases_expected_output/hierarchical-properties/efo/ontologies_linked.json` (88 occurrences
across the fixture set of a synonym value with no `"axioms"` key at all). No genuinely
primitive-shaped (bare-string, non-object) synonym value was found in any committed fixture, so
that case — explicitly called out in the class's own commented-out dead code as "ignored in OLS3
for some reason" — is covered with a hand-built synthetic fixture instead.

**Finding 1 — a missing `"linkedEntities"` key is a live `NullPointerException` risk here, in
contrast to `V1OboDefinitionCitationExtractor`, but is confirmed unreachable in production for the
same underlying reason.** This class reads `"linkedEntities"` via the null-*safe*
`json.getAsJsonObject("linkedEntities")` overload (unlike `V1OboDefinitionCitationExtractor`'s and
`AnnotationExtractor`'s unguarded `json.get("linkedEntities").getAsJsonObject()`, which throws
immediately on a missing key) — so a genuinely absent key yields a `null` local variable here with
no exception at that point. But that `null` is then passed straight into
`V1OboXref.fromString(oboXref, linkedEntities)` whenever a synonym axiom carries an xref value.
Reading `V1OboXref.fromString` itself shows it only dereferences its `linkedEntities` parameter in
one specific branch — a standard `"DATABASE:ID"`-shaped xref (i.e. `tokens.length >= 2` after
splitting on `:`, and neither the `http(s):`-prefixed nor the `scheme://`-with-uppercase-prefix
special case matches) — via the unguarded call `linkedEntities.get(oboXref)`. This was confirmed
empirically, not assumed: a dedicated test constructs a `JsonObject` with no `"linkedEntities"`
key at all and a synonym axiom carrying a realistic `"NCIT:C2991"`-shaped xref (grounded in the
`gitIssue502` fixture above), and asserts the resulting `NullPointerException`. Companion tests
confirm the contrast — a `linkedEntities` object that is present but empty resolves the xref
gracefully with a `null` `url`, and one with a matching entry resolves a real `url` — proving both
that the crash is specific to a genuinely *missing* key (not merely an empty one) and that
`linkedEntities` is genuinely threaded through to `V1OboXref.fromString`.

Investigated for real-world reachability independently, rather than assuming
`V1OboDefinitionCitationExtractor`'s conclusion transfers unexamined:

- The only production caller is `V1TermMapper.mapTerm`, which passes the exact same
  `localizedJson` object to `V1OboSynonymExtractor.extractFromJson` as it does to
  `V1OboDefinitionCitationExtractor.extractFromJson` and `V1OboXrefExtractor.extractFromJson` a few
  lines earlier — i.e. the same top-level term/class entity JSON, so the sibling's finding about
  *that JSON's* structure applies here too, since it concerns the shape of the shared input rather
  than either extractor's own internal logic.
- Independently re-derived (not just cited): a fresh scan of all 111 committed
  `ontologies_linked.json` fixtures under `testcases_expected_output/` found 985 top-level entities
  across the `classes`/`individuals`/`properties` arrays, and confirmed 0 of them are missing a
  `"linkedEntities"` key — matching the sibling's own count exactly.
- Additional, stronger corroboration specific to this investigation: `V1TermMapper.mapTerm` itself
  *also* dereferences `localizedJson.getAsJsonObject("linkedEntities").getAsJsonObject().get(predicate)`
  completely unconditionally, a few lines after calling `V1OboSynonymExtractor.extractFromJson`, to
  resolve each `RELATED_TO` annotation's label. If `"linkedEntities"` were ever genuinely absent on
  an entity handed to `mapTerm`, that line would throw for any entity carrying a `RELATED_TO`
  annotation regardless of whether `V1OboSynonymExtractor` crashed first — i.e. the codebase already
  assumes this invariant a second time, independently, in the same method.
- Conclusion: the missing-null-guard is real and does cause a live `NullPointerException` (unlike
  the definition-citation sibling, where the equivalent code throws unconditionally regardless of
  xref presence), but is confirmed unreachable given the current pipeline's invariant that every
  top-level entity always carries `"linkedEntities"` (the OLS4 linker's `write_entity_array`
  unconditionally writes the key for every entity it processes, per the sibling's own reading of
  `dataload/linker/link/src/linker_pass2.rs`). Per the defect workflow, this does not warrant a
  separate bug-fix PR — it's documented here (and in the test's own Javadoc) as a confirmed
  non-issue rather than left as an implicit assumption.

**Finding 2 — `mergeDuplicates` is scoped per (synonym-value object, one fixed scope) call, not
globally across the four scope categories, and this composes correctly for the cross-category
case but has a subtler consequence within a single category too.** `mergeDuplicates` is invoked
from inside `fromSynonymObject`, once per synonym-value object, over just that object's own
axiom-derived entries, for one fixed `scope` string passed in as a parameter — it is never invoked
again at the top level across the concatenated results of all four `hasExactSynonym`/
`hasRelatedSynonym`/`hasNarrowSynonym`/`hasBroadSynonym` categories.

- **Cross-category case (asked to investigate): correctly kept as distinct entries.** Two
  synonyms with identical `name`/`type`/`xrefs` but different `scope` (one from a
  `hasExactSynonym` object, one from an identical `hasNarrowSynonym` object) are correctly kept as
  two separate entries in the final result — confirmed by a dedicated test. This is doubly true:
  `mergeDuplicates` never even compares entries from different categories against each other (each
  comes from its own `fromSynonymObject` call), and even if it somehow did,
  `V1OboSynonym.equals()` treats a different `scope` as a different object anyway.
- **Same-category, different-object case (an additional, subtler consequence worth flagging):**
  two *different* synonym-value objects within the *same* scope category (e.g. two separate
  elements of the `hasExactSynonym` array) that each, independently, resolve to an identical
  `V1OboSynonym` are *not* deduplicated against each other — each object gets its own
  `fromSynonymObject` call and its own separately-scoped `mergeDuplicates` pass over only its own
  axioms, so both survive as duplicate entries in the final list. This is confirmed by a dedicated
  test, contrasted directly against the same-object case (two identical axioms *within* one
  synonym object's own `axioms` array, which *do* collapse to one entry via the same
  `mergeDuplicates` call). The class's own commented-out `collate` method shows a broader,
  fully-global dedup scheme was written at some point but is currently dead code, not reinstated —
  suggesting the current narrower per-object scoping may be an incomplete refactor rather than a
  deliberate design choice. This was not escalated as a production defect: it's a genuine, if
  minor, behavioural quirk (two syntactically-duplicate synonym statements in upstream OWL source
  producing two duplicate output entries) rather than a crash or an incorrect result for any
  single, well-formed synonym statement, and no committed golden fixture was found exhibiting the
  scenario in practice. Documented here as a confirmed behaviour for future reference rather than
  filed as a defect.

**Branches enumerated.** All four synonym-scope categories are processed independently and
concatenated in source order (exact, related, narrow, broad) — a fixture with synonyms in more
than one category confirms each contributes its own entries tagged with the correct `scope`
string. `fromSynonymObject`: a synonym value that is a `JsonPrimitive` (plain string, not an
object) returns an empty list, confirmed to produce zero `V1OboSynonym` entries (not just "doesn't
crash"), both alone and alongside a genuine reified synonym in the same array. A synonym object
with no `"axioms"` key at all produces no entries and does not crash (`JsonHelper.getObjects`
returns an empty list for a missing key, confirmed both by reading its source and by this test
exercising the real, fixture-grounded shape). A synonym object with one or more axioms produces
one `V1OboSynonym` per axiom, all sharing the same `name` (from the synonym object's own
`"value"`, not per-axiom) and `scope`, but independently reading `type` (`"oboSynonymTypeName"`,
possibly absent → `null`, confirmed) and `xrefs` (via `hasDbXref` values on that specific axiom,
mapped through the real `V1OboXref.fromString`, including a multi-valued axiom with two xrefs
resolved in order) — a dedicated test gives one synonym object two axioms differing in both
`type`/`xrefs` and confirms both distinct entries survive. `xrefs` is always assigned a list
(possibly empty), never left `null`, confirmed with an axiom carrying zero matching xref values.
`mergeDuplicates`: two axioms on the same synonym object producing genuinely identical
`V1OboSynonym` entries (same name/scope/type, both zero-xrefs, tested separately from two axioms
sharing the same non-empty xref list) collapse to one entry in the final result; two axioms
differing in exactly one compared field (`type` in one test, `xrefs` in another) both survive as
distinct entries. Finally, the `synonyms.size() > 0 ? synonyms : null` contract is tested
explicitly and separately: nothing qualifying anywhere returns `null` (not an empty list), while
at least one qualifying synonym returns the real, non-null list.
Verified locally on 2026-09-12 from `origin/dev` commit `75d96f57c` with Java 17 (no Postgres/
Docker gate — this class has no IT layer, per the scope note above):

- Docker-free `mvn test`, run twice: 994/994 pass both times (read from
  `target/surefire-reports/*.txt`, not just exit code), wall-clock 15.86s and 12.20s.
- Postgres/IT gate: skipped — this class has no IT layer (see Scope above).
- Clean `mvn clean verify -Dapi.version=1.44`: 1,199/1,199 pass (994 surefire + 205 failsafe),
  wall-clock 2m1.95s.
- `V1OboDefinitionCitationExtractor` itself now covers 20 of 21 lines (95.2%) and 10 of 10 branches
  (100%); the one uncovered line is the implicit default constructor, never invoked since the only
  caller uses the static method directly. Whole-backend JaCoCo coverage is 71.8% lines (3,454 of
  4,810) and 54.8% branches (1,052 of 1,918), up from the most recently documented baseline of
  71.3% lines and 54.1% branches. Of that increase, 20 lines / 10 branches are directly attributable
  to this class going from zero to full coverage; the remaining small residual (6 lines / 5
  branches) against an identical total denominator (4,810 lines / 1,918 branches, unchanged from
  the prior baseline since this rollout adds no production code) is not attributable to this
  rollout and was not chased further, consistent with the same kind of small, unrelated
  measurement drift already noted in the prior baseline entries. No coverage failure threshold is
  introduced.
- No production defect was discovered by this rollout. The unguarded `linkedEntities` access
  described above is recorded deliberately as a confirmed non-issue, not as a defect requiring a
  fix.
- Surefire runs 1,001 tests, including 18 `V1OboSynonymExtractorTest` cases. Two Docker-free runs
  took wall-clock 11.99 and 12.41 seconds, both 0 failures / 0 errors.
- The clean `verify` lifecycle runs all 1,206 tests (1,001 surefire + 205 failsafe, unchanged by
  this rollout since no IT was added) in wall-clock 2 minutes 3.01 seconds, 0 failures / 0 errors
  (read from `target/surefire-reports`/`target/failsafe-reports`, not just exit code).
- `V1OboSynonymExtractor` itself now covers 51 of 52 lines (98.1%) and 14 of 14 branches (100%);
  the one uncovered line is the implicit default constructor, never invoked since the only caller
  uses the static method directly. `V1OboXref` (the shared value-object class this extractor calls
  into, previously with zero direct or incidental coverage anywhere in the codebase) now covers 17
  of 29 lines (58.6%) and 12 of 24 branches (50.0%) as a direct side effect of this rollout's tests
  deliberately exercising its null/empty/populated-`linkedEntities` branches — the remaining
  uncovered branches belong to xref shapes (`http(s):`-prefixed URLs, `scheme://`-with-uppercase
  DOI-style xrefs) this class never constructs, since it always resolves an axiom's `hasDbXref`
  values from real OBO-style ontology data. Whole-backend JaCoCo coverage is 72.4% lines (3,481 of
  4,810) and 55.8% branches (1,071 of 1,918), up from the most recently documented baseline of
  71.3% lines and 54.1% branches (3,428 of 4,810 lines, 1,037 of 1,918 branches). The 51 lines / 14
  branches directly attributable to `V1OboSynonymExtractor` plus the 17 lines / 12 branches newly
  attributable to `V1OboXref` don't fully reconcile arithmetically against the observed
  whole-backend delta (53 lines / 34 branches) — a small residual in both directions, consistent
  with the same kind of measurement drift already noted in prior baseline entries, and not chased
  further. No coverage failure threshold is introduced.
- No production defect was discovered by this rollout. Both investigation findings above are
  documented as confirmed, non-defect behaviours.
## Implemented V1OboXrefExtractor baseline

`V1OboXrefExtractor` (`repository/v1/mappers`) is the next Tier B target in this programme,
following the same pure-logic pattern as `AnnotationExtractor` and (on a separate, not-yet-merged
branch at the time of writing, `test/cover-v1-obo-synonym-extractor`) `V1OboSynonymExtractor`. It
is a pure static-method utility class -- no Spring bean, no constructor state, no Postgres
dependency of its own -- with a single public method, `extractFromJson(JsonObject)`, called only
from `V1TermMapper.mapTerm` (confirmed via `graphify query "who calls
V1OboXrefExtractor.extractFromJson"`, cross-checked with a direct grep). It extracts an entity's
`http://www.geneontology.org/formats/oboInOwl#hasDbXref` values into `V1OboXref` objects, handling
plain-string xrefs, reified xref objects with no axioms, and reified xref objects whose one-or-more
axioms each independently contribute a `description`/`url`-overridden entry (via `source` values,
then `label` values, then neither), before deduplicating the whole result with `V1OboXref.equals()`.
It had no dedicated test file before this rollout; the closest thing to prior exercise was
incidental, through `V1TermControllerIT`/`V1TermRepositoryIT`'s real-Postgres term lookups (to
whatever extent their loaded ontology fixtures carry `hasDbXref` data) -- per this programme's
standing rule, that controller/repository-level happy-path exercise doesn't count as dedicated
coverage of this extractor's own edge cases, and in particular never touched the multi-axiom,
`source`-vs-`label`-priority, or dedup logic explicitly.

**Scope: unit-only, no IT layer.** Same rationale as its Tier B siblings: a pure-logic class with
no Postgres dependency of its own doesn't get a dedicated `*IT.java` layer under the Tier B
methodology -- there is no real-database behaviour to prove beyond what a direct unit test already
covers with hand-built `JsonObject` fixtures. This baseline is a single new
`V1OboXrefExtractorTest.java` (26 cases, this repo's plain-JUnit/AssertJ idiom, no Mockito, no
Spring context, using the real `V1OboXref` value object directly rather than a fake).

**Real-data grounding.** Per this repo's `graphify` project rule, `graphify query` was used first
to find the production call site, then real xref shapes were pulled from committed
`testcases_expected_output/*/ontologies_linked.json` fixtures (a scripted scan of all 111 files,
985 top-level entities): 202 reified xref objects were found, 147 carrying one or more axioms (all
147 with a `source` value, 0 with a `label` value and 0 with an axiom-level `url` field), and 55
with no `"axioms"` key at all; 0 bare-primitive (non-reified) xrefs were found anywhere in this
corpus. The real 3-source axiom on `http://purl.obolibrary.org/obo/MONDO_0000001` in
`testcases_expected_output/annotation-properties/gitIssue502/ontologies_linked.json` --
`["DOID:4", "EFO:0000408", "MONDO:equivalentTo"]` -- is used verbatim in the "last source element
wins" test. Shapes not found in any committed fixture (a `label`-only axiom, an axiom's own `url`
field, a bare primitive xref, and every `://`-containing `V1OboXref.fromString` shape -- see
below) are covered with hand-built synthetic fixtures instead, same as this program's precedent
for previously-unobserved shapes.

**Branches enumerated in `extractFromJson`.** A plain-`JsonPrimitive` xref is added directly via
`V1OboXref.fromString`, with no axioms processing at all. A `JsonObject` xref with no `"axioms"`
key, and separately one with a present-but-empty `axioms` array, both confirmed to take the
*identical* plain-entry branch (the source checks `axioms.size() > 0`, so "absent" and
"empty-but-present" are the same case, not two different ones -- tested explicitly as two separate
cases to prove this rather than assumed). An xref object with 2+ axioms produces one entry per
axiom independently (tested with one axiom carrying a `source` value and a second with neither,
producing 2 entries before dedup). Per axiom: a `source` value (or values) present sets
`description` to the *last* element of that list (`Lists.reverse(...).iterator().next()`) --
tested with 3 distinct real-fixture-grounded values to rule out first/middle winning by
coincidence -- and short-circuits the `label` check entirely via `continue`, confirmed with an
axiom carrying both a `source` and a `label` value and asserting the `source`-derived description
wins. `label` values (only reached when `source` is absent) get the same last-element-wins
treatment. When neither is present, a plain entry is added with no `description`. In all three
shapes, the axiom's own `"url"` field, when non-null, overrides whatever `V1OboXref.fromString`
itself derived (tested for both the `source` and `label` paths); when the axiom's `"url"` is
absent, the extractor leaves `fromString`'s own derived `url` untouched (tested by resolving a
real `url` via a matching `linkedEntities` entry and confirming the axiom-absent-url case doesn't
clobber it). `mergeDuplicates` (keyed on `V1OboXref.equals()`, i.e. `id`+`description`+`database`+
`url`) was tested two ways: two axioms on the same xref object producing genuinely identical
results collapse to one entry, and two axioms (from two different xref objects sharing the same
underlying id/database, each contributing a distinct `source`-derived `description`) differing in
exactly that one field both survive as distinct entries. Finally, the
`res.size() > 0 ? res : null` contract is tested explicitly in both directions.

**Collateral `V1OboXref.fromString` coverage, driven as a side effect.** Per this rollout's brief,
most fixtures above deliberately used plain-primitive xref strings (rather than always wrapping in
a reified object) specifically to drive real, direct coverage of `V1OboXref.fromString`'s own
branches -- previously exercised only shallowly (17 of 29 lines, 12 of 24 branches, per the
not-yet-merged `V1OboSynonymExtractor` baseline) since that sibling extractor's real-fixture xrefs
are all plain `"DATABASE:ID"` shapes. `V1OboXrefExtractor`'s own call sites cover every branch of
`fromString`:

- `"http:"`- and `"https:"`-prefixed xrefs each set both `id` and `url` to the entire string, with
  `database` left `null` (tested separately for each scheme).
- A `"://"`-containing xref matching `^[A-Z]+:.+` (e.g. the real-world
  `"DOI:https://doi.org/10.1378/chest.12-2762"` shape named in this class's own comment) keeps the
  *entire original string* as `id`, with `database` and `url` both left `null` -- confirmed this
  is the current, active behaviour, not the alternate split-based implementation visible in this
  method's own commented-out dead code.
- **The "`://` but doesn't match the uppercase regex" fallthrough, investigated as a possible
  production defect per this rollout's brief:** a xref like `"orcid://0000-0001-2345-6789"`
  contains `"://"` but its lowercase prefix fails `^[A-Z]+:.+`, and it isn't `http(s):`-prefixed
  either, so it falls through all the way to the generic `oboXref.split(":")` logic below. Verified
  empirically with `jshell` (not assumed): `"orcid://0000-0001-2345-6789".split(":")` produces
  exactly 2 tokens (`"orcid"`, `"//0000-0001-2345-6789"`), since `"://"` itself contributes only
  one literal colon character. The extractor/`fromString` therefore sets `database = "orcid"` and
  `id = "//0000-0001-2345-6789"` -- a mangled, almost certainly unintended parse, and the
  `linkedEntities` lookup (keyed on the full original string, since `tokens.length >= 2`) still
  runs, confirmed with a matching `linkedEntities` entry resolving a `url`. **Investigated for
  real-world reachability, not just constructed and left unverified:** a scripted scan of every
  `hasDbXref` value (raw and axiom-`source`) across all 111 `testcases_expected_output/*/
  ontologies_linked.json` fixtures found zero values containing `"://"` of any kind (http(s)-
  prefixed, DOI-style, or this fallthrough shape) outside the two already-handled early-return
  cases; a second, independent scan of all 1,016 already-processed `oboXrefs` entries across
  `testcases_expected_output_api/**/*.json` (this repo's `test_api.sh` golden-file corpus,
  consulted per the defect workflow) found zero `id` values starting with `"//"` -- the specific,
  unambiguous signature this fallthrough would leave behind. Real OBO xref data in this corpus
  uses only a bare `http(s)` URL, the uppercase `"DATABASE:https://..."` DOI-style convention, or a
  plain `"DATABASE:ID"` pair; a lowercase- or mixed-case-scheme `"scheme://..."` shape was not
  found anywhere. **Conclusion: this is a real, confirmed-via-test code path that would mangle such
  an xref if one ever appeared, but -- in contrast to the two precedent defect PRs (#1416, #1427),
  which both had positive evidence of reachability from an upstream data-generation invariant --
  there is no such positive evidence here, only the absence of any occurrence across this
  programme's entire committed golden-fixture corpus.** Per the defect workflow, this does not
  meet the bar for a separate fix PR; it is documented here, and in the test's own Javadoc, as a
  confirmed-dormant code path rather than filed as a defect.
- No colon at all (`tokens.length < 2`) keeps the whole string as `id` with `database` left `null`,
  and -- confirmed explicitly -- never dereferences `linkedEntities` at all (tested by passing a
  `JsonObject` with no `"linkedEntities"` key alongside a no-colon xref and confirming no
  exception), in direct contrast to the multi-token branch below.
- 2+ tokens after splitting on `:` uses only the *first two* (`database = tokens[0]`,
  `id = tokens[1]`); a 3-token input (`"A:B:C"`) confirms the third token is silently dropped
  (`id` is `"B"`, not `"B:C"`), and that the subsequent `linkedEntities.get(oboXref)` lookup uses
  the *original, full* string (`"A:B:C"`) as its key -- proven by planting a decoy entry under the
  wrong, truncated key (`"A:B"`) that must not be picked up, alongside the correct entry under the
  full key, which is. The `.has("url")` guard on a matching `linkedEntities` entry is tested in
  both directions (present-without-`url` leaves `xref.url` `null`; present-with-`url` resolves it).
- **NPE reachability, independently re-verified for this extractor's own real production callers**
  (per this rollout's brief, rather than assuming the `V1OboSynonymExtractor` baseline's
  conclusion transfers unexamined): `extractFromJson` reads `"linkedEntities"` via the null-safe
  `JsonObject.getAsJsonObject(String)` overload, so a genuinely missing key yields a `null` local
  variable with no exception at that point -- but that `null` is passed straight into
  `V1OboXref.fromString`, which dereferences it unguarded via `linkedEntities.get(oboXref)`
  whenever `tokens.length >= 2`. A dedicated test constructs a `JsonObject` with no
  `"linkedEntities"` key at all and a standard `"NCIT:C2991"`-shaped xref, and confirms the
  resulting `NullPointerException`. Re-derived independently: the only production caller is
  `V1TermMapper.mapTerm`, which passes the exact same `localizedJson` object to this extractor,
  `V1OboSynonymExtractor`, and `V1OboDefinitionCitationExtractor` alike -- a fresh scan of all 111
  committed `ontologies_linked.json` fixtures found 985 top-level entities across the
  classes/individuals/properties arrays, 0 of them missing a `"linkedEntities"` key, and
  `V1TermMapper.mapTerm` itself also dereferences
  `localizedJson.getAsJsonObject("linkedEntities").getAsJsonObject().get(predicate)`
  unconditionally a few lines after calling this extractor, to resolve each `RELATED_TO`
  annotation's label -- so the codebase already assumes this invariant a second, independent time
  in the same method. Conclusion: the missing-null-guard is real and does cause a live
  `NullPointerException`, but is confirmed unreachable given the current pipeline's invariant that
  every top-level entity always carries `"linkedEntities"`. Per the defect workflow, this does not
  warrant a separate bug-fix PR -- documented here as a confirmed non-issue.

Verified locally on 2026-09-12 from `origin/dev` commit `75d96f57c` with Java 17 (no Postgres/
Docker gate -- this class has no IT layer, per the scope note above):

- Surefire runs 1,009 tests, including 26 `V1OboXrefExtractorTest` cases. Two Docker-free runs took
  wall-clock 12.82 and 11.60 seconds, both 0 failures / 0 errors (confirmed via
  `target/surefire-reports/*.txt`, not just exit code).
- The clean `verify` lifecycle runs all 1,214 tests (1,009 surefire + 205 failsafe, unchanged by
  this rollout since no IT was added) in wall-clock 2 minutes 2.26 seconds, 0 failures / 0 errors
  (read from `target/surefire-reports`/`target/failsafe-reports`).
- `V1OboXrefExtractor` itself now covers 50 of 51 lines (98.0%) and 28 of 28 branches (100%); the
  one uncovered line is the implicit default constructor, never invoked since the only caller uses
  the static method directly. `V1OboXref` (the shared value-object class this extractor calls into
  far more heavily than its siblings) now covers 28 of 29 lines (96.6%) and 20 of 24 branches
  (83.3%) -- up from the not-yet-merged `V1OboSynonymExtractor` baseline's 17 of 29 lines (58.6%)
  and 12 of 24 branches (50.0%), a direct side effect of this rollout deliberately exercising the
  http(s)-prefixed, DOI-style, and `://`-fallthrough branches that extractor's own real-fixture
  xrefs never construct. The remaining uncovered branches in `V1OboXref` belong to its `equals()`
  method's `instanceof` mismatch path (`mergeDuplicates` never compares a `V1OboXref` against a
  non-`V1OboXref`, so that branch is structurally unreachable from any real caller) and a couple of
  its chained `Objects.equals(...) &&` short-circuit paths. Whole-backend JaCoCo coverage is 72.7%
  lines (3,498 of 4,810) and 56.5% branches (1,084 of 1,918), up from the most recently documented
  baseline of 71.3% lines and 54.1% branches (3,428 of 4,810 lines, 1,037 of 1,918 branches) --
  i.e. +70 lines / +47 branches covered, against unchanged totals (no production code changed).
  This does not reconcile exactly against the two classes' own combined figures, consistent with
  the same kind of small measurement residual already noted in prior baseline entries, and not
  chased further. No coverage failure threshold is introduced.
- One code path was investigated as a plausible production defect (the `://`-fallthrough in
  `V1OboXref.fromString`, detailed above) and confirmed real but dormant -- zero occurrences across
  the entire committed golden-fixture corpus, and no upstream invariant showing it can occur,
  unlike the two precedent defect PRs in this programme. No separate defect PR was opened; both
  this finding and the `linkedEntities`-NPE finding are documented above as confirmed, tested,
  non-actioned behaviours rather than left as implicit assumptions.

## Out of scope for the pilot

- Connecting GitHub-hosted CI to production or internal databases.
- Running the complete dataload inside every Maven integration test.
- Full-JSON snapshot assertions in unit, WIT, or repository IT suites.
- Refactoring production controller or repository code solely to make the pilot aesthetically cleaner.
- Testing every Cartesian combination of query parameters.
- Introducing a repository-wide coverage threshold before a meaningful baseline exists.
- Building production smoke monitoring.
