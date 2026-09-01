package uk.ac.ebi.spot.ols.repository.search;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import uk.ac.ebi.spot.ols.testsupport.PostgresIntegrationTestSupport;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OlsSearchClientSuggestIT {

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
    void searchesLabelsAndAppliesTheRequestedPageWindow() {
        assertThat(searchClient.suggestLabels("liver", null, 0, 10))
                .containsExactly("Liver ailment", "Liver disease");
        assertThat(searchClient.suggestLabels("liver", null, 1, 1))
                .containsExactly("Liver disease");
    }

    @Test
    void searchesSynonymsAndRestrictsResultsToTheRequestedOntology() {
        assertThat(searchClient.suggestLabels("hepatic disorder", List.of("efo"), 0, 10))
                .containsExactly("Hepatic disorder");
        assertThat(searchClient.suggestLabels("legacy liver concept", List.of("efo"), 0, 10))
                .containsExactly("Legacy liver concept");
    }
}
