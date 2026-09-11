package uk.ac.ebi.spot.ols.repository.postgres;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport.OlsPostgresClientRepositoryHandle;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Postgres coverage of {@link OlsPostgresClient}. This is the "milestone 1" slice of this
 * Tier B target (see the class-level Javadoc on {@link OlsPostgresClientTest} and
 * {@code docs/backend-testing-strategy.md}'s "Implemented OlsPostgresClient" baseline for the
 * full milestone split): {@link OlsPostgresClient#getDatabaseNodeCount()}, {@link
 * OlsPostgresClient#getAll}, and {@link OlsPostgresClient#getOne}. Later milestones add
 * {@code OlsPostgresClientGraphIT}/{@code OlsPostgresClientEmbeddingIT}-style dedicated fixtures
 * for the graph-traversal and embedding/vector-search families; this class deliberately reuses the
 * existing shared {@code initializeDatabase} ontology + entity fixture (no dedicated fixture is
 * needed for {@code getAll}/{@code getOne}, which operate on the generic {@code ols_entities}
 * {@code type}/{@code id}/{@code iri}/{@code ontology_id} columns already populated by it).
 *
 * <p>The shared fixture loaded by {@code initializeDatabase} contains four ontology records and
 * five entity records: four {@code OntologyClass} rows (DUO_0001 in ontology {@code duo}; EFO_0001,
 * EFO_0002, and the obsolete EFO_0999, all in ontology {@code efo}) and one {@code
 * OntologyProperty} row (EFO_0100, in {@code efo}) -- see {@code entity-fixture.json}.
 */
class OlsPostgresClientIT {

    private static PostgreSQLContainer<?> container;
    private static OlsPostgresClientRepositoryHandle handle;
    private static OlsPostgresClient client;

    @BeforeAll
    static void setUpDatabase() {
        container = PostgresIntegrationTestSupport.newContainer();
        container.start();
        PostgresIntegrationTestSupport.initializeDatabase(container);
        handle = PostgresIntegrationTestSupport.createOlsPostgresClientRepositories(container);
        client = handle.olsPostgresClient();
    }

    @AfterAll
    static void tearDownDatabase() {
        if (handle != null) {
            handle.close();
        }
        if (container != null) {
            container.stop();
        }
    }

    private static final String EFO_0001 = "efo+class+http://example.org/EFO_0001";
    private static final String EFO_0002 = "efo+class+http://example.org/EFO_0002";
    private static final String EFO_0999 = "efo+class+http://example.org/EFO_0999";
    private static final String DUO_0001 = "duo+class+http://example.org/DUO_0001";

    // ------------------------------------------------------------------
    // getDatabaseNodeCount -- a one-line passthrough to PostgresClient.returnNodeCount().
    // ------------------------------------------------------------------

    @Test
    void getDatabaseNodeCountReturnsTheTotalOlsEntitiesRowCount() {
        // 4 ontology rows (ontology-fixture.json) + 5 entity rows (entity-fixture.json).
        assertThat(client.getDatabaseNodeCount()).isEqualTo(9L);
    }

    // ------------------------------------------------------------------
    // getAll -- the properties map's three recognized keys (id/iri/ontologyId), an unrecognized
    // key silently ignored, and pagination.
    // ------------------------------------------------------------------

    @Test
    void getAllWithNoPropertyFiltersReturnsEveryEntityOfTheGivenTypeOrderedByIri() {
        Page<JsonElement> page = client.getAll("OntologyClass", Map.of(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(4);
        List<String> iris = page.getContent().stream()
                .map(e -> e.getAsJsonObject().get("iri").getAsString())
                .toList();
        assertThat(iris).containsExactly(
                "http://example.org/DUO_0001",
                "http://example.org/EFO_0001",
                "http://example.org/EFO_0002",
                "http://example.org/EFO_0999");
    }

    @Test
    void getAllFiltersByIdProperty() {
        Page<JsonElement> page = client.getAll("OntologyClass", Map.of("id", EFO_0001), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getAsJsonObject().get("id").getAsString()).isEqualTo(EFO_0001);
    }

    @Test
    void getAllFiltersByIriProperty() {
        Page<JsonElement> page = client.getAll(
                "OntologyClass", Map.of("iri", "http://example.org/EFO_0002"), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getAsJsonObject().get("id").getAsString()).isEqualTo(EFO_0002);
    }

    @Test
    void getAllFiltersByOntologyIdProperty() {
        Page<JsonElement> page = client.getAll("OntologyClass", Map.of("ontologyId", "efo"), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        List<String> ids = page.getContent().stream()
                .map(e -> e.getAsJsonObject().get("id").getAsString())
                .toList();
        assertThat(ids).containsExactlyInAnyOrder(EFO_0001, EFO_0002, EFO_0999);
    }

    @Test
    void getAllIgnoresAnUnrecognizedPropertyKeyEntirely() {
        Page<JsonElement> filtered = client.getAll(
                "OntologyClass", Map.of("thisIsNotARecognizedKey", "anything"), PageRequest.of(0, 10));
        Page<JsonElement> unfiltered = client.getAll("OntologyClass", Map.of(), PageRequest.of(0, 10));

        assertThat(filtered.getTotalElements()).isEqualTo(unfiltered.getTotalElements());
    }

    @Test
    void getAllCombinesMultipleRecognizedPropertyFiltersWithAnd() {
        Page<JsonElement> page = client.getAll(
                "OntologyClass",
                Map.of("ontologyId", "efo", "id", EFO_0002),
                PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getAsJsonObject().get("id").getAsString()).isEqualTo(EFO_0002);
    }

    @Test
    void getAllReturnsNoResultsForAnUnknownType() {
        Page<JsonElement> page = client.getAll("NoSuchType", Map.of(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(0);
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void getAllPaginatesAcrossMultiplePages() {
        Page<JsonElement> firstPage = client.getAll("OntologyClass", Map.of(), PageRequest.of(0, 2));
        Page<JsonElement> secondPage = client.getAll("OntologyClass", Map.of(), PageRequest.of(1, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(4);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(secondPage.getContent()).hasSize(2);

        List<String> firstIris = firstPage.getContent().stream()
                .map(e -> e.getAsJsonObject().get("iri").getAsString())
                .toList();
        List<String> secondIris = secondPage.getContent().stream()
                .map(e -> e.getAsJsonObject().get("iri").getAsString())
                .toList();
        assertThat(firstIris).containsExactly("http://example.org/DUO_0001", "http://example.org/EFO_0001");
        assertThat(secondIris).containsExactly("http://example.org/EFO_0002", "http://example.org/EFO_0999");
    }

    // ------------------------------------------------------------------
    // getOne -- delegates to getAll with a page size of 10, then requires exactly one result.
    // ------------------------------------------------------------------

    @Test
    void getOneReturnsTheSingleMatchingEntity() {
        JsonElement result = client.getOne("OntologyClass", Map.of("id", EFO_0001));

        assertThat(result.getAsJsonObject().get("id").getAsString()).isEqualTo(EFO_0001);
    }

    @Test
    void getOneThrowsWhenNoResultsMatch() {
        assertThatThrownBy(() -> client.getOne("OntologyClass", Map.of("iri", "http://example.org/DOES_NOT_EXIST")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expected exactly one result for getOne, but got 0");
    }

    @Test
    void getOneThrowsWhenMoreThanOneResultMatches() {
        assertThatThrownBy(() -> client.getOne("OntologyClass", Map.of("ontologyId", "efo")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expected exactly one result for getOne, but got 3");
    }
}
