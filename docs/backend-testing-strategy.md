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

## Out of scope for the pilot

- Connecting GitHub-hosted CI to production or internal databases.
- Running the complete dataload inside every Maven integration test.
- Full-JSON snapshot assertions in unit, WIT, or repository IT suites.
- Refactoring production controller or repository code solely to make the pilot aesthetically cleaner.
- Testing every Cartesian combination of query parameters.
- Introducing a repository-wide coverage threshold before a meaningful baseline exists.
- Building production smoke monitoring.
