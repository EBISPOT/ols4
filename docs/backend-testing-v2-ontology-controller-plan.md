# V2OntologyController Testing Pilot — Implementation Plan

**Date:** 2026-08-24

**Strategy:** `docs/backend-testing-strategy.md`

## Goal

Implement the first complete controller testing pilot for `V2OntologyController`, establishing reusable unit, Web Integration Test, PostgreSQL Integration Test, full-stack controller Integration Test, Maven, fixture, coverage, and CI conventions for the rest of the OLS4 backend.

## Constraints

- Branch the testing work from current `dev`.
- Do not connect tests to an internal or production database.
- Do not perform unrelated production refactoring.
- Preserve the existing `test_api.sh` system regression suite.
- When a test exposes a defect, create a separate bug-fix PR from `dev` containing the minimal regression test and fix.
- Use Java 17 for backend Maven work.

## Intended file map

Paths may be adjusted slightly during implementation if existing package conventions require it.

| Action | Path | Responsibility |
|---|---|---|
| Modify | `backend/pom.xml` | Surefire, Failsafe, Testcontainers, and JaCoCo configuration. |
| Create | `backend/src/test/java/uk/ac/ebi/spot/ols/controller/api/v2/V2OntologyControllerTest.java` | Direct controller unit tests. |
| Create | `backend/src/test/java/uk/ac/ebi/spot/ols/controller/api/v2/V2OntologyControllerWIT.java` | Spring MVC contract and parameter tests. |
| Create | `backend/src/test/java/uk/ac/ebi/spot/ols/repository/OntologyRepositoryIT.java` | Real PostgreSQL repository/search tests. |
| Create | `backend/src/test/java/uk/ac/ebi/spot/ols/controller/api/v2/V2OntologyControllerIT.java` | Thin controller-to-database route tests. |
| Create | `backend/src/test/java/uk/ac/ebi/spot/ols/testsupport/PostgresIntegrationTestSupport.java` | Shared Testcontainers, schema, and fixture setup. |
| Create | `backend/src/test/resources/fixtures/v2-ontology-controller/ontologies.json` | Readable four-ontology database fixture. |
| Create | `backend/src/test/resources/fixtures/v2-ontology-controller/README.md` | Fixture provenance and regeneration notes. |
| Modify | `.github/workflows/build-test.yml` | Add unit/WIT and database-IT gates before system regression. |
| Maintain | `docs/backend-testing-strategy.md` | Record any implementation-driven clarification without weakening agreed contracts. |

## Task 1: Establish a verified baseline

- [ ] Record the current branch, `dev` merge-base, and working-tree state.
- [ ] Preserve unrelated tracked and untracked work.
- [ ] Run the existing backend tests with Java 17:

```bash
mvn -B -ntp -pl backend -am test
```

- [ ] Record current test count, duration, and failures.
- [ ] Confirm Docker is available before starting Testcontainers work.
- [ ] Confirm the current OpenAPI representation of `V2OntologyController`, especially the automatically exposed `sort` parameter.

Expected baseline from the design investigation: 11 existing backend tests pass when Maven uses Java 17. Re-verify rather than treating that number as permanent.

## Task 2: Configure the Maven test lifecycle

Modify `backend/pom.xml`.

- [ ] Add a Testcontainers version/BOM compatible with Java 17 and the current Spring Boot version.
- [ ] Add the PostgreSQL Testcontainers module and required JUnit integration dependencies with test scope.
- [ ] Configure Surefire so it retains its normal patterns and also includes `**/*WIT.java`.
- [ ] Configure Failsafe so `**/*IT.java` runs in `integration-test`/`verify`.
- [ ] Configure JaCoCo to generate reports without an initial coverage failure threshold.
- [ ] Ensure `mvn test` does not start Docker-backed ITs.
- [ ] Ensure `mvn verify` runs unit tests, WITs, and ITs.
- [ ] Provide a Failsafe-only CI invocation/profile if needed to avoid rerunning unit/WIT tests in the separate database job.

Verification:

```bash
mvn -B -ntp -pl backend -am test
mvn -B -ntp -pl backend -am verify
```

Add a temporary discovery assertion or inspect Maven reports during implementation to prove `*WIT` and `*IT` are not silently skipped. Remove any temporary class before committing.

## Task 3: Add direct controller unit tests

Create `V2OntologyControllerTest` using JUnit 5 and Mockito without starting Spring.

Cover controller-owned decisions:

- [ ] `getOntologies` adds `isObsolete=false` when `includeObsoleteEntities` is false.
- [ ] `getOntologies` does not add that filter when obsolete ontologies are included.
- [ ] Reserved request parameters are removed before repository delegation.
- [ ] Genuine dynamic properties and their multiple values are preserved.
- [ ] Search, search fields, boost fields, exact-match flag, language, pageable, and transform options are delegated unchanged.
- [ ] Repository page results are mapped into `V2PagedAndFacetedResponse` and `V2Entity` values.
- [ ] `/by-tag` delegates using exactly the `tags` grouping field.
- [ ] `/by-domain` delegates using exactly the `domain` grouping field.
- [ ] `getOntology` returns the repository entity when found.
- [ ] `getOntology` throws the custom `ResourceNotFoundException` when the repository returns null.

Do not assert Spring defaults here; direct method invocation does not apply Spring binding defaults.

## Task 4: Add the Web Integration Test harness

Create `V2OntologyControllerWIT` as a focused Spring MVC slice around the real controller.

- [ ] Load `V2OntologyController` and the real global exception advice.
- [ ] Replace `OntologyRepository` with Spring's current test mock-bean mechanism.
- [ ] Use deterministic `V2Entity` and `OlsFacetedResultsPage` builders/helpers.
- [ ] Assert response status, content type, stable JSON fields, and repository arguments.
- [ ] Avoid complete JSON snapshots.

The baseline list response should assert stable fields such as:

```text
page
numElements
totalPages
totalElements
elements[*].ontologyId
facetFieldsToCounts
```

## Task 5: Cover every list parameter in WIT

For `GET /api/v2/ontologies`, add focused cases for:

- [ ] All omitted parameters: `page=0`, `size=20`, `exactMatch=false`, `includeObsoleteEntities=false`, `lang=en`, and both transform flags false.
- [ ] `page` and `size` supplied with valid values.
- [ ] Supported `sort` field and direction.
- [ ] `search`.
- [ ] `searchFields`.
- [ ] `boostFields`.
- [ ] `exactMatch=true` and explicit `false`.
- [ ] `includeObsoleteEntities=true` and explicit `false`.
- [ ] Non-default `lang`.
- [ ] `resolveReferences=true` binding and forwarding.
- [ ] `manchesterSyntax=true` binding and forwarding.
- [ ] A normal dynamic property.
- [ ] Repeated dynamic-property values.
- [ ] Comma-separated dynamic-property values where repository semantics support them.
- [ ] URI-based/encoded dynamic-property keys.
- [ ] Reserved parameters do not become dynamic filters.
- [ ] A meaningful `search + searchFields + exactMatch` interaction.

Add parameterized malformed cases for:

- [ ] Non-numeric `page` and `size`.
- [ ] Invalid boolean values.
- [ ] Unsupported sort field or direction, according to the final sort contract.

Add boundary cases for:

- [ ] Negative page.
- [ ] Zero or negative size.
- [ ] Size above the configured maximum.

Each malformed input should produce HTTP 400 with stable `status` and `message` fields. If current behaviour differs, follow the defect workflow rather than weakening the assertion.

## Task 6: Cover grouped and single-ontology routes in WIT

### `/by-tag`

- [ ] Default language and transform options.
- [ ] Explicit language and transform flags are bound and forwarded.
- [ ] Stable group key and `ontologyId` fields are serialized.
- [ ] Invalid typed parameters return HTTP 400.

### `/by-domain`

- [ ] Default language and transform options.
- [ ] Explicit language and transform flags are bound and forwarded.
- [ ] Stable group key and `ontologyId` fields are serialized.
- [ ] Invalid typed parameters return HTTP 400.

### `/{onto}`

- [ ] Existing ontology returns HTTP 200 and stable fields.
- [ ] Missing ontology returns HTTP 404 with stable error fields.
- [ ] Invalid ontology identifier returns HTTP 400 once repository validation is exercised by the appropriate layer.
- [ ] Default and explicit language values are forwarded.
- [ ] Transform flags are bound and forwarded without duplicating transformer semantics.

Also assert unsupported HTTP methods produce the intended method-not-allowed response if that behaviour is part of the published Spring contract.

## Task 7: Build the disposable PostgreSQL test harness

Create shared test support for repository and controller ITs.

- [ ] Start `pgvector/pgvector:0.8.0-pg17` through Testcontainers.
- [ ] Register the container's dynamic host, port, database, username, and password with the Spring test context.
- [ ] Generate schema SQL through `dataload/create_postgres_schema.py` rather than maintaining a test-only schema.
- [ ] Declare required dynamic filter columns, including `tags` and `domain`.
- [ ] Execute the generated extensions, table, index, and post-load sections against the container.
- [ ] Load the small fixture using the same compressed JSON representation expected by `OlsSearchClient`.
- [ ] Keep data immutable during read-only repository tests, or reset it deterministically if a test mutates state.
- [ ] Fail clearly when the IT lifecycle is invoked without Docker.

The harness should not require a live production database, production credentials, a full Nextflow run, or a production snapshot.

## Task 8: Create and document the fixture

Create a readable four-record fixture under backend test resources.

- [ ] Derive stable ontology metadata from existing EFO and DUO testcase material.
- [ ] Include two active EFO-derived records, one active DUO-derived record, and one obsolete record.
- [ ] Include overlapping and distinct tags.
- [ ] Include at least two domains.
- [ ] Distribute search terms across fields so `searchFields`, exact matching, and boosting can be distinguished.
- [ ] Choose stable identifiers whose lexical order makes default ordering explicit.
- [ ] Include enough records to produce multiple pages with a deliberately small test page size.
- [ ] Document source files, deliberate deviations, and regeneration steps in the fixture README.
- [ ] Do not add a generic JSON config under `testcases/`, where `test_api.sh` would automatically pick it up.

Use selected existing expected API values as an oracle, not as a full snapshot assertion.

## Task 9: Add `OntologyRepositoryIT`

Exercise the real repository and PostgreSQL query path.

- [ ] Default list query returns active ontology records in deterministic order.
- [ ] Obsolete filtering excludes and includes the intended records.
- [ ] Pagination returns correct page content and totals.
- [ ] Supported sorting changes order correctly.
- [ ] Free-text search returns the intended records.
- [ ] Exact and non-exact search differ where expected.
- [ ] `searchFields` restricts matching to the requested fields.
- [ ] `boostFields` changes ranking in a deliberately distinguishable fixture case.
- [ ] Known dynamic properties filter correctly.
- [ ] Repeated and comma-separated filter values have the intended semantics.
- [ ] Unknown dynamic properties return zero matches rather than unrelated data.
- [ ] URI-named dynamic filters resolve to the correct generated column.
- [ ] Tag grouping returns stable keys and memberships.
- [ ] Domain grouping returns stable keys and memberships.
- [ ] Existing ontology ID returns the correct entity.
- [ ] Missing ontology ID returns no entity.
- [ ] Invalid language and ontology identifiers raise the intended validation error.

Assert query results and stable transformed fields; do not assert query implementation details unless needed for a regression.

## Task 10: Add thin `V2OntologyControllerIT`

Start the Spring test application against the disposable database and issue real MVC requests.

- [ ] `GET /api/v2/ontologies` returns the expected default active records and page metadata.
- [ ] `GET /api/v2/ontologies/by-tag` returns a representative stable grouping.
- [ ] `GET /api/v2/ontologies/by-domain` returns a representative stable grouping.
- [ ] `GET /api/v2/ontologies/{onto}` returns a representative stable ontology.

Do not repeat the complete WIT parameter matrix. Do not add full-stack semantic assertions for `resolveReferences` or `manchesterSyntax` in this pilot.

## Task 11: Resolve contract defects through separate PRs

### Sort investigation

- [ ] Demonstrate current behaviour with the smallest failing regression test.
- [ ] Confirm supported fields and directions.
- [ ] If broken, create a separate branch from `dev` and fix sorting without permitting unsafe arbitrary SQL fields.
- [ ] Ensure `sort` is removed from dynamic-property handling.
- [ ] Merge the fix and rebase the testing pilot.

### Malformed-parameter investigation

- [ ] Demonstrate whether Spring binding failures currently become HTTP 500.
- [ ] If broken, create a separate branch from `dev`.
- [ ] Map relevant binding/type mismatch exceptions to HTTP 400 with stable error fields.
- [ ] Merge the fix and rebase the testing pilot.

Do not combine these fixes merely because the testing pilot discovered both.

## Task 12: Add CI gates

Modify `.github/workflows/build-test.yml`.

- [ ] Add a Java 17 unit/WIT job running the backend Maven test phase.
- [ ] Add a Java 17 database-IT job with Docker/Testcontainers access.
- [ ] Enable Maven dependency caching.
- [ ] Keep the two backend jobs parallel where possible.
- [ ] Make the existing `test-api` job depend on both backend jobs.
- [ ] Upload Surefire, Failsafe, and JaCoCo reports on failure or as appropriate for the repository.
- [ ] Retain the existing dataload, Docker Compose, and API regression behaviour.

Do not rely on the backend Dockerfile's `mvn package -DskipTests` step as test execution.

## Task 13: Verification and review

- [ ] Run unit and WIT suites twice to detect order dependence.
- [ ] Run the complete verify lifecycle twice with Docker.
- [ ] Confirm all four test-class suffixes are discovered by their intended plugin.
- [ ] Confirm ITs use the disposable container address, not environment-default PostgreSQL settings.
- [ ] Confirm no production or internal credentials appear in code, fixtures, logs, or workflow files.
- [ ] Confirm stable-field assertions do not snapshot volatile response content.
- [ ] Review total runtime and identify avoidable repeated container/schema initialization.
- [ ] Run the existing `test_api.sh` workflow or rely on the full CI job when local resources make it impractical, recording what was and was not run locally.
- [ ] Run `graphify update .` after code changes and verify only intended graph updates are included.

Primary verification commands:

```bash
mvn -B -ntp -pl backend -am test
mvn -B -ntp -pl backend -am verify
```

## Pilot completion checklist

- [ ] `V2OntologyControllerTest` covers controller-owned decisions.
- [ ] `V2OntologyControllerWIT` covers all routes and supported parameters.
- [ ] `OntologyRepositoryIT` covers real PostgreSQL semantics used by the controller.
- [ ] `V2OntologyControllerIT` proves all four routes through the real database wiring.
- [ ] Stable success and error fields are asserted.
- [ ] Surefire, Failsafe, Testcontainers, and JaCoCo are configured and verified.
- [ ] GitHub CI runs fast backend and database gates before system regression.
- [ ] Any discovered defects were fixed through separate PRs from `dev`.
- [ ] The complete suite is deterministic and independent of production data.
- [ ] Runtime and coverage baseline are recorded for the V1 rollout decision.

## Next controller

After reviewing the pilot, apply the same framework to `V1OntologyController`, then alternate corresponding V1 and V2 controller families based on traffic and contract risk.
