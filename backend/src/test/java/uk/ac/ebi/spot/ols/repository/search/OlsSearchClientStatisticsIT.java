package uk.ac.ebi.spot.ols.repository.search;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OlsSearchClientStatisticsIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            PostgresIntegrationTestSupport.newContainer();

    private static PostgresIntegrationTestSupport.SearchClientHandle searchClientHandle;
    private static OlsSearchClient searchClient;

    @BeforeAll
    static void setUpDatabase() {
        PostgresIntegrationTestSupport.initializeDatabase(POSTGRES);
        searchClientHandle = PostgresIntegrationTestSupport.createSearchClient(POSTGRES);
        searchClient = searchClientHandle.searchClient();
    }

    @AfterAll
    static void closeDatabaseClient() {
        searchClientHandle.close();
    }

    @Test
    void countsEntitiesBySearchTypeThroughPostgres() {
        Map<String, Long> counts = searchClient.getCountsByField("type");

        assertThat(counts)
                .containsEntry("ontology", 4L)
                .containsEntry("class", 4L)
                .containsEntry("property", 1L)
                .doesNotContainKey("individual");
    }

    @Test
    void returnsTheMostRecentOntologyLoadDateFromPostgres() {
        assertThat(searchClient.getLastModified()).isEqualTo("2026-08-25T00:00:00Z");
    }
}
