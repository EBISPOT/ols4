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

Read-only production smoke monitoring is a separate future initiative for an internal or self-hosted environment. It is not part of the initial PR testing framework and must not become a merge-blocking production dependency.

## Out of scope for the pilot

- Connecting GitHub-hosted CI to production or internal databases.
- Running the complete dataload inside every Maven integration test.
- Full-JSON snapshot assertions in unit, WIT, or repository IT suites.
- Refactoring production controller or repository code solely to make the pilot aesthetically cleaner.
- Testing every Cartesian combination of query parameters.
- Introducing a repository-wide coverage threshold before a meaningful baseline exists.
- Building production smoke monitoring.
