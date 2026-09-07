package uk.ac.ebi.spot.ols.controller.api.v2;

import com.google.gson.JsonElement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.ac.ebi.spot.ols.repository.OntologyRepository;
import uk.ac.ebi.spot.ols.repository.postgres.OlsPostgresClient;
import uk.ac.ebi.spot.ols.repository.search.OlsFacetedResultsPage;
import uk.ac.ebi.spot.ols.repository.transforms.JsonTransformOptions;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthCheckControllerTest {

    private RecordingOntologyRepository ontologyRepository;
    private RecordingOlsPostgresClient postgresClient;
    private HealthCheckController controller;

    @BeforeEach
    void setUp() {
        ontologyRepository = new RecordingOntologyRepository();
        postgresClient = new RecordingOlsPostgresClient();
        controller = new HealthCheckController();
        controller.ontologyRepository = ontologyRepository;
        controller.postgresClient = postgresClient;
    }

    @Test
    void reportsAllSystemsOperationalWhenSearchAndPostgresAreHealthy() {
        ontologyRepository.totalElements = 4;
        postgresClient.nodeCount = 10;

        ResponseEntity<String> response = controller.checkHealth();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("All systems are operational.", response.getBody());
    }

    @Test
    void reportsSearchUnavailableWhenSearchReturnsNoResults() {
        ontologyRepository.totalElements = 0;
        postgresClient.nodeCount = 10;

        ResponseEntity<String> response = controller.checkHealth();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Search is not initialized.", response.getBody());
    }

    @Test
    void reportsSearchUnavailableWhenSearchThrows() {
        ontologyRepository.failure = new RuntimeException("search backend unreachable");
        postgresClient.nodeCount = 10;

        ResponseEntity<String> response = controller.checkHealth();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Search is not initialized.", response.getBody());
    }

    @Test
    void reportsPostgresUnavailableWhenSearchIsHealthyButNodeCountIsZero() {
        ontologyRepository.totalElements = 4;
        postgresClient.nodeCount = 0;

        ResponseEntity<String> response = controller.checkHealth();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Postgres is not initialized.", response.getBody());
    }

    @Test
    void reportsPostgresUnavailableWhenSearchIsHealthyButPostgresThrows() {
        ontologyRepository.totalElements = 4;
        postgresClient.failure = new RuntimeException("postgres unreachable");

        ResponseEntity<String> response = controller.checkHealth();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Postgres is not initialized.", response.getBody());
    }

    private static class RecordingOntologyRepository extends OntologyRepository {
        private long totalElements;
        private RuntimeException failure;

        @Override
        public OlsFacetedResultsPage<JsonElement> find(
                Pageable pageable,
                String lang,
                String search,
                String searchFields,
                String boostFields,
                boolean exactMatch,
                Map<String, Collection<String>> properties,
                JsonTransformOptions outputOpts) {
            if (failure != null) {
                throw failure;
            }
            return new OlsFacetedResultsPage<>(List.of(), Map.of(), pageable, totalElements);
        }
    }

    private static class RecordingOlsPostgresClient extends OlsPostgresClient {
        private long nodeCount;
        private RuntimeException failure;

        @Override
        public long getDatabaseNodeCount() {
            if (failure != null) {
                throw failure;
            }
            return nodeCount;
        }
    }
}
